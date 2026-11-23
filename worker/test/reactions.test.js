import {test} from "node:test";
import assert from "node:assert/strict";
import {setMessageReaction} from "../src/mutations.js";

test("reactions can be replaced and removed without changing the other participant's reaction", async () => {
  const chatId = "a".repeat(64);
  const messageId = crypto.randomUUID();
  const path = `chats/${chatId}/messages/${messageId}`;
  const docs = new Map([
    [`chats/${chatId}`, {members: ["alice", "bob"]}],
    [path, {senderId: "alice", text: "Oi"}],
  ]);
  const get = key => docs.has(key) ? {data: docs.get(key)} : null;
  const db = {transaction: action => action({
    get, getAll: keys => keys.map(get), set: (key, value) => docs.set(key, value),
  })};
  const react = (uid, emoji) => setMessageReaction(db, {uid}, {chatId, messageId, emoji});
  await react("alice", "👍");
  await react("bob", "👍");
  await react("alice", "❤️");
  await react("alice", "❤️");
  await react("bob", null);
  assert.deepEqual(docs.get(path), {senderId: "alice", text: "Oi", reactions: {alice: "❤️"}});
  await react("bob", "🧑🏽‍💻");
  assert.deepEqual(docs.get(path).reactions, {alice: "❤️", bob: "🧑🏽‍💻"});
  await assert.rejects(react("mallory", "😢"), {code: "permission-denied"});
  await assert.rejects(react("bob", "texto"), {code: "invalid-argument"});
  await assert.rejects(react("bob", "👍👍"), {code: "invalid-argument"});
  docs.get(path).expiresAt = new Date(0);
  await assert.rejects(react("bob", "👍"), {code: "not-found"});
});
