import {test, before, after} from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {initializeTestEnvironment, assertSucceeds, assertFails} from "@firebase/rules-unit-testing";
import {doc, setDoc, getDoc, getDocs, collection, query, where, orderBy, Timestamp, serverTimestamp} from "firebase/firestore";
import {Firestore} from "../src/firestore.js";
import {chatId} from "../src/validation.js";
import {registerProfile, discoverContacts, openChat, persistMessage, quota, registerDevice, acknowledgeMessages, updateProfilePhoto, updateProfileAbout, sendNudge} from "../src/service.js";
import {changeMessage, deleteConversation} from "../src/mutations.js";
import {dispatchJob, drainOutbox} from "../src/push.js";

let env, db, id;
const user = {uid: "alice", email: "alice@example.com"};
const claims = {email_verified: true, firebase: {sign_in_provider: "google.com"}};
const auth = uid => env.authenticatedContext(uid, claims).firestore();
before(async () => {
  env = await initializeTestEnvironment({projectId: "demo-falai", firestore: {rules: readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8")}});
  db = new Firestore({FIREBASE_PROJECT_ID: "demo-falai"}, "http://127.0.0.1:8080/v1");
  await registerProfile(db, user, {name: "Álice"});
  await registerProfile(db, {uid: "bob", email: "bob@example.com"}, {name: "Beto"});
  id = (await openChat(db, user, {uid: "bob"})).chatId;
});
after(async () => { if (env) await env.cleanup(); });
test("Google participants can read chats and profiles, others cannot", async () => {
  await assertSucceeds(getDoc(doc(auth("alice"), "users/alice")));
  await assertFails(getDoc(doc(auth("bob"), "users/alice")));
  await assertSucceeds(getDoc(doc(auth("alice"), "chats", id)));
  await assertSucceeds(getDocs(query(collection(auth("bob"), "chats"), where("members", "array-contains", "bob"), orderBy("updatedAt", "desc"))));
  await assertFails(getDoc(doc(auth("mallory"), "chats", id)));
  await assertFails(getDocs(collection(auth("mallory"), "chats")));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "chats", id)));
  await assertFails(getDoc(doc(env.authenticatedContext("alice", {email_verified: true, firebase: {sign_in_provider: "phone"}}).firestore(), "chats", id)));
});
test("email discovery finds only registered contacts and does not expose the directory", async () => {
  assert.deepEqual(await discoverContacts(db, user, {emails: ["BOB@example.com", "unknown@example.com", "alice@example.com"]}),
    {contacts: [{uid: "bob", name: "Beto", email: "bob@example.com"}]});
  await assertFails(getDocs(collection(auth("alice"), "directory")));
  await assertFails(getDocs(collection(auth("alice"), "devices")));
  await assertFails(getDocs(collection(auth("alice"), "notificationOutbox")));
});
test("concurrent retries persist exactly one message and one durable notification job", async () => {
  const messageId = crypto.randomUUID(); const data = {chatId: id, messageId, text: "Olá 😀"};
  await Promise.all([persistMessage(db, "alice", data), persistMessage(db, "alice", data)]);
  assert.equal((await db.query("notificationOutbox")).length, 1);
  const message = await db.get(`chats/${id}/messages/${messageId}`);
  assert.equal(message.data.senderId, "alice"); assert.equal(message.data.text, "Olá 😀"); assert.ok(message.data.createdAt instanceof Date);
  assert.equal((await db.get(`chats/${id}`)).data.lastText, "Olá 😀");
  await persistMessage(db, "alice", data);
  await assert.rejects(persistMessage(db, "alice", {...data, text: "Alterada"}), {code: "already-exists"});
  await assert.rejects(persistMessage(db, "mallory", data), {code: "permission-denied"});
  await assertSucceeds(getDocs(query(collection(auth("bob"), "chats", id, "messages"), orderBy("createdAt", "desc"))));
});
test("direct client mutations cannot bypass server validation", async () => {
  for (const path of [`chats/${id}`, `chats/${id}/messages/forged`, "users/alice", "directory/forged", "devices/forged", "notificationOutbox/forged", "quotas/forged"]) {
    await assertFails(setDoc(doc(auth("alice"), path), {senderId: "bob", members: ["alice", "mallory"]}));
  }
});
test("typing and foreground presence enforce ownership, membership and server timestamps", async () => {
  const typingPath = `chats/${id}/typing/alice`;
  await assertSucceeds(setDoc(doc(auth("alice"), typingPath), {active: true, updatedAt: serverTimestamp()}));
  await assertSucceeds(getDoc(doc(auth("bob"), typingPath)));
  await assertFails(getDoc(doc(auth("mallory"), typingPath)));
  await assertFails(setDoc(doc(auth("bob"), typingPath), {active: false, updatedAt: serverTimestamp()}));
  await assertFails(setDoc(doc(auth("mallory"), `chats/${id}/typing/mallory`), {active: true, updatedAt: serverTimestamp()}));
  await assertFails(setDoc(doc(auth("alice"), typingPath), {active: true, updatedAt: Timestamp.fromMillis(Date.now() + 86400000)}));
  await assertFails(setDoc(doc(auth("alice"), typingPath), {active: true, updatedAt: serverTimestamp(), text: "not allowed"}));
  await assertSucceeds(setDoc(doc(auth("alice"), typingPath), {active: false, updatedAt: serverTimestamp()}));
  const sessionPath = `presence/alice/sessions/${crypto.randomUUID()}`;
  await assertSucceeds(setDoc(doc(auth("alice"), sessionPath), {active: true, updatedAt: serverTimestamp()}));
  await assertSucceeds(getDoc(doc(auth("bob"), sessionPath)));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), sessionPath)));
  await assertFails(setDoc(doc(auth("bob"), sessionPath), {active: true, updatedAt: serverTimestamp()}));
  await assertFails(setDoc(doc(auth("alice"), sessionPath), {active: true, updatedAt: Timestamp.fromMillis(Date.now() + 86400000)}));
  await assertSucceeds(setDoc(doc(auth("alice"), sessionPath), {active: false, updatedAt: serverTimestamp()}));
});
test("build date is public without tokens, validates input and supports older installations", async () => {
  const deviceId = crypto.randomUUID(), token = "build-date-test-registration-token", buildTime = Date.now() - 60000;
  await registerDevice(db, {uid: "builduser"}, {deviceId, token, buildTime});
  const value = await assertSucceeds(getDoc(doc(auth("bob"), "profileBuild/builduser")));
  assert.equal(value.data().buildTime.toMillis(), buildTime);
  assert.equal(value.data().token, undefined);
  await assertFails(setDoc(doc(auth("builduser"), "profileBuild/builduser"), {buildTime: serverTimestamp()}));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "profileBuild/builduser")));
  await assert.rejects(registerDevice(db, {uid: "builduser"}, {deviceId, token, buildTime: Date.now() + 172800000}), {code: "invalid-argument"});
  await registerDevice(db, {uid: "builduser"}, {deviceId, token});
  assert.equal((await db.get("profileBuild/builduser")).data.buildTime, null);
  await registerDevice(db, {uid: "builduser"}, {deviceId, token, versionName: "1.1", versionCode: 2});
  const version = (await assertSucceeds(getDoc(doc(auth("bob"), "profileBuild/builduser")))).data();
  assert.equal(version.versionName, "1.1"); assert.equal(version.versionCode, 2); assert.equal(version.buildTime, null);
  await assert.rejects(registerDevice(db, {uid: "builduser"}, {deviceId, token, versionName: "bad version", versionCode: -1}), {code: "invalid-argument"});
  await registerDevice(db, {uid: "builduser"}, {deviceId, token});
  assert.equal((await db.get("profileBuild/builduser")).data.versionName, null);
});
test("quota is enforced in a transaction", async () => {
  await quota(db, "alice", "test", 1, 2); await quota(db, "alice", "test", 1, 2);
  await assert.rejects(quota(db, "alice", "test", 1, 2), {status: 429});
});

test("only the recipient can acknowledge delivery/read and receipts never regress", async () => {
  const messageId = crypto.randomUUID();
  const result = await persistMessage(db, "alice", {chatId: id, messageId, text: "Teste de confirmação"});
  const path = `chats/${id}/messages/${messageId}`;
  const input = {chatId: id, messageIds: [messageId], state: "read"};
  await acknowledgeMessages(db, user, input);
  assert.equal((await db.get(path)).data.readAt, undefined);
  await assert.rejects(acknowledgeMessages(db, {uid: "mallory"}, input), {code: "permission-denied"});
  await assert.rejects(acknowledgeMessages(db, {uid: "bob"}, {...input, messageIds: ["../forged"]}), {code: "invalid-argument"});
  await acknowledgeMessages(db, {uid: "bob"}, {...input, state: "delivered"});
  const delivered = (await db.get(path)).data;
  assert.ok(delivered.deliveredAt instanceof Date); assert.equal(delivered.readAt, undefined);
  await acknowledgeMessages(db, {uid: "bob"}, input);
  const read = (await db.get(path)).data;
  assert.ok(read.readAt instanceof Date); assert.deepEqual(read.deliveredAt, delivered.deliveredAt);
  await acknowledgeMessages(db, {uid: "bob"}, {...input, state: "delivered"});
  await acknowledgeMessages(db, {uid: "bob"}, input);
  assert.deepEqual((await db.get(path)).data, read);
  assert.equal(read.text, "Teste de confirmação"); assert.equal(read.senderId, "alice");
  await assertFails(setDoc(doc(auth("bob"), "chats", id, "messages", messageId), {readAt: Timestamp.now()}, {merge: true}));
  // Keep the outbox fixtures used by the following dispatch tests unchanged.
  await db.commit([db.remove("notificationOutbox/" + result.jobId)]);
});
test("push failure preserves the outbox and successful devices are skipped on retry", async () => {
  for (let n = 0; n < 3; n++) await registerDevice(db, {uid: "bob"}, {deviceId: crypto.randomUUID(), token: "valid-token-for-testing-" + n});
  const job = (await db.query("notificationOutbox"))[0]; const delivered = [];
  await dispatchJob(db, {}, job.path, async (env, token) => {
    if (delivered.length === 1) throw new Error("temporary"); delivered.push(token); return "sent";
  });
  const pending = await db.get(job.path); assert.ok(pending); assert.equal(pending.data.sentDeviceIds.length, 1);
  await db.commit([db.write(job.path, {...pending.data, nextAttemptAt: new Date(0)})]);
  await dispatchJob(db, {}, job.path, async (env, token) => { assert.ok(!delivered.includes(token)); delivered.push(token); return "sent"; });
  assert.equal(delivered.length, 3); assert.equal(await db.get(job.path), null);
});
test("a leased job is not dispatched concurrently and a lost lease can be recovered", async () => {
  const data = {chatId: id, messageId: crypto.randomUUID(), text: "Segunda mensagem"};
  const result = await persistMessage(db, "alice", data); const path = "notificationOutbox/" + result.jobId;
  const job = (await db.get(path)).data;
  await db.commit([db.write(path, {...job, nextAttemptAt: new Date(Date.now() + 120000), lease: "other-worker"})]);
  await dispatchJob(db, {}, path, async () => { assert.fail("active lease must not send"); });
  const pending = await db.get(path);
  await db.commit([db.write(path, {...pending.data, nextAttemptAt: new Date(0)})]);
  let count = 0; await dispatchJob(db, {}, path, async () => { count++; return "sent"; });
  assert.equal(count, 3); assert.equal(await db.get(path), null);
});

test("profile photos are owned by the authenticated account and visible without exposing private profiles", async () => {
  const photo = readFileSync(new URL("./fixtures/avatar.jpg", import.meta.url)).toString("base64");
  await updateProfilePhoto(db, user, {uid: "bob", photo});
  assert.equal((await db.get("profilePhotos/alice")).data.photo, photo);
  assert.equal(await db.get("profilePhotos/bob"), null);
  await assertSucceeds(getDoc(doc(auth("bob"), "profilePhotos/alice")));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "profilePhotos/alice")));
  await assertFails(getDocs(collection(auth("bob"), "profilePhotos")));
  await assertFails(setDoc(doc(auth("bob"), "profilePhotos/alice"), {photo}));
  await assert.rejects(updateProfilePhoto(db, user, {photo: "invalid"}), {code: "invalid-argument"});
  await registerProfile(db, user, {name: "Álice atualizada"});
  assert.equal((await db.get("profilePhotos/alice")).data.photo, photo);
  await updateProfilePhoto(db, user, {photo: null}); assert.equal(await db.get("profilePhotos/alice"), null);
});

test("edit and delete are authorized, preserve receipt dates and update the last-message summary", async () => {
  const messageId = crypto.randomUUID(), data = {chatId: id, messageId, text: "Original"};
  await persistMessage(db, "alice", data);
  await acknowledgeMessages(db, {uid: "bob"}, {chatId: id, messageIds: [messageId], state: "read"});
  const before = (await db.get(`chats/${id}/messages/${messageId}`)).data;
  const summary = (await db.get(`chats/${id}`)).data;
  assert.ok(summary.lastReadAt instanceof Date); assert.ok(summary.lastDeliveredAt instanceof Date);
  await assert.rejects(changeMessage(db, {uid: "bob"}, {...data, text: "forged"}, true), {code: "permission-denied"});
  await assert.rejects(changeMessage(db, {uid: "mallory"}, {...data, scope: "self"}), {code: "permission-denied"});
  await changeMessage(db, user, {...data, text: "Editada 😀"}, true);
  let message = (await db.get(`chats/${id}/messages/${messageId}`)).data;
  assert.equal(message.text, "Editada 😀"); assert.deepEqual(message.createdAt, before.createdAt); assert.deepEqual(message.readAt, before.readAt);
  assert.equal((await db.get(`chats/${id}`)).data.lastText, "Editada 😀");
  await changeMessage(db, user, {...data, scope: "self"});
  message = (await db.get(`chats/${id}/messages/${messageId}`)).data;
  assert.deepEqual(message.hiddenFor, ["alice"]); assert.equal(message.text, "Editada 😀");
  assert.deepEqual((await db.get(`chats/${id}`)).data.previewHiddenFor, ["alice"]);
  await assert.rejects(changeMessage(db, {uid: "bob"}, {...data, scope: "all"}), {code: "permission-denied"});
  const deleted = await changeMessage(db, user, {...data, scope: "all"});
  message = (await db.get(`chats/${id}/messages/${messageId}`)).data;
  assert.equal(message.text, ""); assert.equal(message.deletedForAll, true); assert.deepEqual(message.readAt, before.readAt);
  assert.equal((await db.get(`chats/${id}`)).data.lastDeleted, true);
  assert.equal((await db.get("notificationOutbox/" + deleted.jobId)).data.event, "deleted");
  await assert.rejects(changeMessage(db, user, {...data, text: "restore"}, true), {code: "already-exists"});
  await assertFails(setDoc(doc(auth("alice"), "chats", id, "messages", messageId), {text: "bypass"}, {merge: true}));
});

test("deleting a conversation affects only that user and new messages can restore it", async () => {
  await assert.rejects(deleteConversation(db, {uid: "mallory"}, {chatId: id}), {code: "permission-denied"});
  await deleteConversation(db, user, {chatId: id});
  let chat = (await db.get(`chats/${id}`)).data;
  assert.ok(chat.clearedBefore.alice instanceof Date); assert.equal(chat.clearedBefore.bob, undefined);
  const messageId = crypto.randomUUID(); await persistMessage(db, "bob", {chatId: id, messageId, text: "Nova conversa"});
  chat = (await db.get(`chats/${id}`)).data;
  assert.ok(chat.updatedAt > chat.clearedBefore.alice); assert.equal(chat.lastText, "Nova conversa"); assert.deepEqual(chat.previewHiddenFor, []);
});

test("receipts upgrade legacy last-message summaries without losing chat metadata", async () => {
  const messageId = crypto.randomUUID(); await persistMessage(db, "alice", {chatId: id, messageId, text: "Mensagem anterior"});
  const path = `chats/${id}`, legacy = (await db.get(path)).data;
  delete legacy.lastMessageId; delete legacy.lastSenderId; delete legacy.lastDeliveredAt; delete legacy.lastReadAt;
  await db.commit([db.write(path, legacy)]);
  await acknowledgeMessages(db, {uid: "bob"}, {chatId: id, messageIds: [messageId], state: "read"});
  const updated = (await db.get(path)).data;
  assert.equal(updated.lastMessageId, messageId); assert.equal(updated.lastSenderId, "alice"); assert.ok(updated.lastReadAt instanceof Date);
  assert.deepEqual(updated.clearedBefore, legacy.clearedBefore); assert.equal(updated.lastText, legacy.lastText);
});

test("about phrases are shared separately from private data and can only be changed by their owner", async () => {
  const result = await updateProfileAbout(db, user, {uid: "bob", text: "  Disponível 😀\npara conversar  "});
  assert.equal(result.text, "Disponível 😀 para conversar");
  assert.equal((await db.get("profileAbout/alice")).data.text, result.text); assert.equal(await db.get("profileAbout/bob"), null);
  await assertSucceeds(getDoc(doc(auth("bob"), "profileAbout/alice")));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "profileAbout/alice")));
  await assertFails(getDocs(collection(auth("bob"), "profileAbout")));
  await assertFails(setDoc(doc(auth("bob"), "profileAbout/alice"), {text: "forged"}));
  await assertFails(getDoc(doc(auth("bob"), "users/alice")));
  for (const invalid of [null, 23, "x".repeat(140), "a\u0000b"])
    await assert.rejects(updateProfileAbout(db, user, {text: invalid}), {code: "invalid-argument"});
  await registerProfile(db, user, {name: "Álice"}); assert.equal((await db.get("profileAbout/alice")).data.text, result.text);
  await updateProfileAbout(db, user, {text: ""}); assert.equal(await db.get("profileAbout/alice"), null);
});

test("nudges authorize participants, are idempotent, enforce cooldown and cannot be edited", async () => {
  const alice = {uid: 'nudgealice', email: 'nudgealice@example.com'}, bob = {uid: 'nudgebob', email: 'nudgebob@example.com'};
  await registerProfile(db, alice, {name: 'Alice'}); await registerProfile(db, bob, {name: 'Bob'});
  const chatId = (await openChat(db, alice, {uid: bob.uid})).chatId, messageId = crypto.randomUUID();
  const input = {chatId, messageId};
  await assert.rejects(sendNudge(db, {uid: 'mallory'}, input), {code: 'permission-denied'});
  await Promise.all([sendNudge(db, alice, input), sendNudge(db, alice, input)]);
  const messagePath = `chats/${chatId}/messages/${messageId}`, jobPath = `notificationOutbox/${chatId}_${messageId}`;
  assert.equal((await db.get(messagePath)).data.kind, 'nudge');
  assert.equal((await db.get(jobPath)).data.recipientId, bob.uid);
  assert.equal((await db.get('quotas/nudgealice_nudges')).data.count, 1);
  await assert.rejects(sendNudge(db, alice, {chatId, messageId: crypto.randomUUID()}), {code: 'nudge-cooldown'});
  await assert.rejects(changeMessage(db, alice, {...input, text: 'Forged nudge'}, true), {code: 'invalid-argument'});
  await assertFails(setDoc(doc(auth(alice.uid), `nudgeLimits/${chatId}_${alice.uid}`), {sentAt: serverTimestamp()}));
  await registerDevice(db, bob, {deviceId: crypto.randomUUID(), token: 'nudge-device-test-token'});
  const events = [];
  await dispatchJob(db, {}, jobPath, async (env, token, job) => { events.push(job.event); return 'sent'; });
  assert.deepEqual(events, ['nudge']);
  await changeMessage(db, alice, {...input, scope: 'all'});
  await dispatchJob(db, {}, jobPath + '_deleted', async (env, token, job) => { events.push(job.event); return 'sent'; });
  assert.deepEqual(events, ['nudge', 'deleted']);
  await db.commit([db.write(`nudgeLimits/${chatId}_${alice.uid}`, {sentAt: new Date(Date.now() - 61000)}), db.write('quotas/nudgealice_nudges', {start: Date.now(), count: 20})]);
  await assert.rejects(sendNudge(db, alice, {chatId, messageId: crypto.randomUUID()}), {code: 'resource-exhausted'});
});
test("an expired nudge is not delivered late but its history remains", async () => {
  const alice = {uid: 'expiryalice', email: 'expiryalice@example.com'}, bob = {uid: 'expirybob', email: 'expirybob@example.com'};
  await registerProfile(db, alice, {name: 'Alice'}); await registerProfile(db, bob, {name: 'Bob'});
  const chatId = (await openChat(db, alice, {uid: bob.uid})).chatId, messageId = crypto.randomUUID();
  await sendNudge(db, alice, {chatId, messageId});
  const path = `notificationOutbox/${chatId}_${messageId}`, job = (await db.get(path)).data;
  await db.commit([db.write(path, {...job, createdAt: new Date(Date.now() - 61000)})]);
  let calls = 0; await dispatchJob(db, {}, path, async () => { calls++; return 'sent'; });
  assert.equal(calls, 0); assert.equal(await db.get(path), null);
  assert.equal((await db.get(`chats/${chatId}/messages/${messageId}`)).data.kind, 'nudge');
});
