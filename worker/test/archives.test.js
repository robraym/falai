import {test} from "node:test";
import assert from "node:assert/strict";
import {archiveConversation, shouldNotifyArchived} from "../src/archives.js";

function database(chat) {
  const path = `chats/${"a".repeat(64)}`;
  const records = new Map([[path, structuredClone(chat)]]);
  return {records, path, transaction: async callback => {
    const writes = [];
    const result = await callback({get: async key => records.has(key) ? {data: structuredClone(records.get(key))} : null,
      set: (key, data) => writes.push([key, structuredClone(data)])});
    for (const [key, value] of writes) records.set(key, value);
    return result;
  }};
}

test("archiving is personal, idempotent, and preserves messages, unread counts and timers", async () => {
  const original = {members: ["alice", "bob"], lastText: "Olá", unread: {bob: 2}, timerHours: 24,
    updatedAt: new Date("2026-11-24T10:00:00Z")};
  const db = database(original), input = {chatId: "a".repeat(64), archived: true};
  await archiveConversation(db, {uid: "alice"}, input);
  await archiveConversation(db, {uid: "alice"}, input);
  assert.deepEqual(db.records.get(db.path), {...original, archivedFor: ["alice"]});
  await archiveConversation(db, {uid: "bob"}, input);
  await archiveConversation(db, {uid: "alice"}, {...input, archived: false});
  assert.deepEqual(db.records.get(db.path), {...original, archivedFor: ["bob"]});
});

test("non-members and malformed requests cannot archive a conversation", async () => {
  const db = database({members: ["alice", "bob"]});
  await assert.rejects(archiveConversation(db, {uid: "eve"}, {chatId: "a".repeat(64), archived: true}), {status: 403});
  await assert.rejects(archiveConversation(db, {uid: "alice"}, {chatId: "a".repeat(64), archived: "true"}), {status: 400});
  assert.deepEqual(db.records.get(db.path), {members: ["alice", "bob"]});
});

test("archived messages and nudges are quiet, except direct replies to the recipient", () => {
  const chat = {archivedFor: ["bob"]};
  assert.equal(shouldNotifyArchived(chat, "bob", {text: "Olá"}), false);
  assert.equal(shouldNotifyArchived(chat, "bob", {kind: "nudge"}), false);
  assert.equal(shouldNotifyArchived(chat, "bob", {reply: {senderId: "bob"}}), true);
  assert.equal(shouldNotifyArchived(chat, "bob", {reply: {senderId: "alice"}}), false);
  assert.equal(shouldNotifyArchived(chat, "alice", {}), true);
  assert.equal(shouldNotifyArchived({}, "bob", {}), true);
});
