import {test} from "node:test";
import assert from "node:assert/strict";
import {expiration} from "../src/timers.js";
import {addUnread, consumeUnread, pruneUnread} from "../src/unread.js";

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
