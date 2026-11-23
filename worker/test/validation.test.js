import {test} from "node:test";
import assert from "node:assert/strict";
import {hash, chatId, normalizeEmail, normalizeMessage, firebaseClaims} from "../src/validation.js";
import {encode, decode, fields, Firestore} from "../src/firestore.js";

test("same participants resolve to one chat, without delimiter collisions", async () => {
  assert.equal(await chatId("alice", "bob"), await chatId("bob", "alice"));
  assert.notEqual(await chatId("a_b", "c"), await chatId("a", "b_c"));
});
test("email lookup normalizes case but preserves dots and plus tags", async () => {
  assert.equal(normalizeEmail(" Name.Tag+test@Example.com "), "name.tag+test@example.com");
  assert.notEqual(await hash(normalizeEmail("name.tag@example.com")), await hash(normalizeEmail("nametag@example.com")));
  for (const email of [null, "", "x", "a@b", "a/b@x.com", "a\n@x.com"]) assert.throws(() => normalizeEmail(email));
});
test("messages preserve accents and emoji and cannot carry forged fields", async () => {
  const data = {chatId: await chatId("alice", "bob"), messageId: crypto.randomUUID(), text: " Olá 😀 ", senderId: "mallory", createdAt: 0};
  assert.deepEqual(normalizeMessage(data), {text: "Olá 😀"});
  for (const invalid of [{text: " "}, {text: "x".repeat(4001)}, {text: null}, {messageId: "../../x"}, {chatId: "../x"}]) assert.throws(() => normalizeMessage({...data, ...invalid}));
});
test("identity must be from the configured Firebase project and verified Google login", () => {
  const now = 1000;
  const claims = {aud: "falai-test", iss: "https://securetoken.google.com/falai-test", sub: "alice", exp: 2000, iat: 900,
    auth_time: 800, email_verified: true, email: "alice@example.com", firebase: {sign_in_provider: "google.com"}};
  assert.deepEqual(firebaseClaims(claims, "falai-test", now), {uid: "alice", email: "alice@example.com"});
  for (const invalid of [{aud: "other-project"}, {iss: "https://attacker.com"}, {exp: 999}, {iat: 2000}, {email_verified: false}, {sub: "../root"}, {firebase: {sign_in_provider: "phone"}}]) {
    assert.throws(() => firebaseClaims({...claims, ...invalid}, "falai-test", now));
  }
});
test("Firestore wire values preserve timestamps, nested names, arrays and Unicode", () => {
  const value = {name: "João", enabled: true, attempts: 3, nullable: null, date: new Date("2026-11-22T12:00:00Z"), names: {alice: "Álice"}, sent: ["a", "b"]};
  assert.deepEqual(decode(encode(value)), value);
  assert.ok(fields(value).date.timestampValue);
});
test("streamed Firestore errors are decoded instead of being mistaken for empty query results", async context => {
  context.mock.method(globalThis, "fetch", async () => Response.json([{error: {status: "FAILED_PRECONDITION", message: "index-not-ready"}}], {status: 400}));
  const db = new Firestore({FIREBASE_PROJECT_ID: "demo-falai"}, "http://emulator/v1");
  await assert.rejects(db.query("notificationOutbox"), {code: "database-failed-precondition"});
});

test("transaction conflicts retry with the previous token and permanent errors do not retry", async context => {
  const db = new Firestore({FIREBASE_PROJECT_ID: "demo-falai"}, "http://emulator/v1");
  const starts = []; let commits = 0, rollbacks = 0;
  context.mock.method(db, "request", async (path, body) => {
    if (path === ":beginTransaction") { starts.push(body.options.readWrite); return {transaction: "tx-" + starts.length}; }
    if (path === ":rollback") { rollbacks++; return {}; }
    if (path === ":commit") {
      commits++;
      if (commits === 1) throw Object.assign(new Error("conflict"), {retryable: true});
      return {};
    }
    assert.fail(path);
  });
  assert.equal(await db.transaction(async tx => { tx.set("items/one", {ok: true}); return "done"; }), "done");
  assert.deepEqual(starts, [{}, {retryTransaction: "tx-1"}]); assert.equal(commits, 2); assert.equal(rollbacks, 1);
  await assert.rejects(db.transaction(async () => { throw new Error("permission-denied"); }), /permission-denied/);
  assert.equal(starts.length, 3); assert.equal(rollbacks, 2);
});
