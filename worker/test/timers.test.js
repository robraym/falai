import {test} from "node:test";
import assert from "node:assert/strict";
import {expiration, applyAccountTimer, setDefaultTimer, setChatTimer} from "../src/timers.js";
import {persistMessage} from "../src/service.js";
import {addUnread, consumeUnread, pruneUnread} from "../src/unread.js";

function timerDatabase(seed) {
  const records = new Map(Object.entries(seed));
  const read = async path => records.has(path) ? {path, data: structuredClone(records.get(path))} : null;
  return {records, write: (path, data) => ({path, data}),
    commit: async writes => { for (const {path, data} of writes) records.set(path, data); },
    transaction: async callback => {
      const writes = [];
      const result = await callback({get: read, getAll: paths => Promise.all(paths.map(read)),
        set: (path, data) => writes.push({path, data})});
      for (const {path, data} of writes) records.set(path, data);
      return result;
    }};
}

test("a 24-hour account default does not silently activate a legacy existing chat", async () => {
  const id = "a".repeat(64), chat = {members: ["alice", "bob"], lastText: "original"};
  const db = timerDatabase({[`chats/${id}`]: chat});
  await setDefaultTimer(db, {uid: "alice"}, {hours: 24});
  assert.equal(db.records.get("messageDefaults/alice").hours, 24);
  assert.deepEqual(db.records.get(`chats/${id}`), chat);
  assert.equal([...db.records.keys()].some(path => path.includes("timerEvents")), false);
});

test("the next outgoing message applies 24 hours and creates a shared notice even if the recipient default is off", async () => {
  const id = "a".repeat(64), now = Date.now();
  const oldPath = `chats/${id}/messages/old`, oldExpiry = new Date(now + 7 * 86400000);
  const db = timerDatabase({
    [`chats/${id}`]: {members: ["alice", "bob"], timerHours: 168, timerChangedAt: new Date(now - 20000)},
    "messageDefaults/alice": {hours: 24, updatedAt: new Date(now - 10000)},
    "messageDefaults/bob": {hours: 0, updatedAt: new Date(now - 30000)},
    [oldPath]: {text: "older", expiresAt: oldExpiry}
  });
  const messageId = "11111111-1111-4111-8111-111111111111";
  const input = {chatId: id, messageId, text: "new"};
  await persistMessage(db, "alice", input);
  const sent = db.records.get(`chats/${id}/messages/${messageId}`);
  assert.equal(sent.expiresAt - sent.createdAt, 86400000);
  assert.equal(db.records.get(`chats/${id}`).timerHours, 24);
  const event = db.records.get(`chats/${id}/timerEvents/default-${messageId}`);
  assert.equal(event.hours, 24); assert.equal(event.changedBy, "alice");
  assert.deepEqual(event.createdAt, sent.createdAt);
  assert.deepEqual(db.records.get(oldPath).expiresAt, oldExpiry);
  await persistMessage(db, "alice", input);
  assert.equal([...db.records.keys()].filter(path => path.includes("timerEvents")).length, 1);
  const replyId = "22222222-2222-4222-8222-222222222222";
  await persistMessage(db, "bob", {chatId: id, messageId: replyId, text: "reply"});
  const reply = db.records.get(`chats/${id}/messages/${replyId}`);
  assert.equal(reply.expiresAt - reply.createdAt, 86400000);
  assert.equal(db.records.get(`chats/${id}`).timerHours, 24);
  assert.equal([...db.records.keys()].filter(path => path.includes("timerEvents")).length, 1);
});

test("a newer explicit chat choice wins over an older account setting; a new account choice can disable future expiry", () => {
  const now = new Date("2026-11-23T21:00:00Z");
  const chat = {timerHours: 168, timerChangedAt: new Date("2026-11-23T20:00:00Z")};
  assert.equal(applyAccountTimer(chat, {hours: 24, updatedAt: new Date("2026-11-23T19:00:00Z")}, "alice", now), false);
  assert.equal(chat.timerHours, 168);
  assert.equal(applyAccountTimer(chat, {hours: 0, updatedAt: new Date("2026-11-23T20:30:00Z")}, "alice", now), true);
  assert.equal(expiration(chat, now), null);
  assert.equal(chat.timerChangedBy, "alice");
});

test("the latest account choice wins even if the other participant sends first", async () => {
  const id = "b".repeat(64), now = Date.now();
  const db = timerDatabase({
    [`chats/${id}`]: {members: ["alice", "bob"], timerHours: 168, timerChangedAt: new Date(now - 30000)},
    "messageDefaults/alice": {hours: 24, updatedAt: new Date(now - 10000)},
    "messageDefaults/bob": {hours: 0, updatedAt: new Date(now - 20000)}
  });
  const messageId = "33333333-3333-4333-8333-333333333333";
  await persistMessage(db, "bob", {chatId: id, messageId, text: "first"});
  assert.equal(db.records.get(`chats/${id}`).timerHours, 24);
  assert.equal(db.records.get(`chats/${id}/timerEvents/default-${messageId}`).changedBy, "alice");
  const message = db.records.get(`chats/${id}/messages/${messageId}`);
  assert.equal(message.expiresAt - message.createdAt, 86400000);
});

test("explicitly selecting an existing chat creates a shared timer and notice, preserving old messages", async () => {
  const id = "a".repeat(64), requestId = "11111111-1111-4111-8111-111111111111";
  const messagePath = `chats/${id}/messages/old`, message = {text: "original", createdAt: new Date("2026-01-01")};
  const db = timerDatabase({[`chats/${id}`]: {members: ["alice", "bob"]}, [messagePath]: message});
  await setChatTimer(db, {uid: "alice"}, {chatId: id, hours: 24, requestId});
  const chat = db.records.get(`chats/${id}`), event = db.records.get(`chats/${id}/timerEvents/${requestId}`);
  assert.equal(chat.timerHours, 24);
  assert.equal(chat.timerChangedBy, "alice");
  assert.equal(event.hours, 24);
  assert.equal(event.changedBy, "alice");
  assert.deepEqual(event.createdAt, chat.timerChangedAt);
  assert.deepEqual(chat.timerFormatSince, chat.timerChangedAt);
  assert.deepEqual(db.records.get(messagePath), message);
  await setChatTimer(db, {uid: "alice"}, {chatId: id, hours: 24, requestId});
  assert.equal([...db.records.keys()].filter(path => path.includes("timerEvents")).length, 1);
  await setChatTimer(db, {uid: "bob"}, {chatId: id, hours: 168, requestId: "22222222-2222-4222-8222-222222222222"});
  assert.equal(db.records.get(`chats/${id}`).timerHours, 168);
  assert.equal(db.records.get(`chats/${id}`).timerChangedBy, "bob");
  assert.deepEqual(db.records.get(messagePath), message);
});

test("a non-participant cannot apply a timer to an existing chat", async () => {
  const id = "a".repeat(64), chat = {members: ["alice", "bob"]};
  const db = timerDatabase({[`chats/${id}`]: chat});
  await assert.rejects(setChatTimer(db, {uid: "eve"}, {chatId: id, hours: 24,
    requestId: "11111111-1111-4111-8111-111111111111"}), error => error.status === 403);
  assert.deepEqual(db.records.get(`chats/${id}`), chat);
});

test("message expiry is fixed from sending time for each supported duration", () => {
  const sent = new Date("2026-01-01T00:00:00Z");
  for (const hours of [24, 168, 2160]) assert.equal(expiration({timerHours: hours}, sent).getTime(), sent.getTime() + hours * 3600000);
  assert.equal(expiration({timerHours: 0}, sent), null);
  assert.equal(expiration({}, sent), null);
});
test("expired unread buckets and late receipts cannot remove a new unread message", context => {
  let now = Date.now(); context.mock.method(Date, "now", () => now);
  const chat = {}, expiry = new Date(now + 86400000);
  const old = {senderId: "alice", createdAt: new Date(now), expiresAt: expiry, unreadTracked: true};
  addUnread(chat, "bob", expiry); addUnread(chat, "bob", null);
  assert.equal(chat.unreadCounts.bob, 2);
  now = Math.ceil(expiry.getTime() / 60000) * 60000;
  assert.equal(pruneUnread(chat), true); assert.equal(chat.unreadCounts.bob, 1);
  assert.equal(pruneUnread(chat), false);
  assert.equal(consumeUnread(chat, old, "bob"), false); assert.equal(chat.unreadCounts.bob, 1);
  const future = new Date(now + 100000); addUnread(chat, "bob", future);
  assert.equal(consumeUnread(chat, {...old, expiresAt: future}, "bob"), true);
  now += 200000; pruneUnread(chat); assert.equal(chat.unreadCounts.bob, 1);
});
