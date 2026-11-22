import {test, before, after} from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {initializeTestEnvironment, assertSucceeds, assertFails} from "@firebase/rules-unit-testing";
import {doc, setDoc, getDoc, getDocs, collection, query, where, orderBy, Timestamp} from "firebase/firestore";
import {Firestore} from "../src/firestore.js";
import {chatId} from "../src/validation.js";
import {registerProfile, discoverContacts, openChat, persistMessage, quota, registerDevice, acknowledgeMessages} from "../src/service.js";
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
