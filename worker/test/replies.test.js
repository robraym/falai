import {test} from "node:test";
import assert from "node:assert/strict";
import {persistMessage} from "../src/service.js";

test("replies use the original from the same chat and preserve the reply ID across retries", async () => {
  const chatId = "b".repeat(64);
  const replyToId = crypto.randomUUID();
  const chatPath = `chats/${chatId}`;
  const originalPath = `${chatPath}/messages/${replyToId}`;
  const expiresAt = new Date(Date.now() + 60_000);
  const docs = new Map([
    [chatPath, {members: ["alice", "bob"]}],
    [originalPath, {senderId: "alice", text: "Mensagem temporária", createdAt: new Date(), expiresAt}],
  ]);
  const get = key => docs.has(key) ? {data: docs.get(key)} : null;
  const db = {transaction: action => action({
    get, getAll: keys => keys.map(get), set: (key, value) => docs.set(key, value),
  })};
  const data = {chatId, messageId: crypto.randomUUID(), replyToId, text: "Resposta", reply: {text: "Forjada"}};
  assert.equal((await persistMessage(db, "bob", data)).created, true);
  assert.deepEqual(docs.get(`${chatPath}/messages/${data.messageId}`).reply, {
    messageId: replyToId, senderId: "alice", kind: "message", expiresAt,
  });
  assert.equal((await persistMessage(db, "bob", data)).created, false);
  await assert.rejects(persistMessage(db, "bob", {...data, replyToId: null}), {code: "already-exists"});
  await assert.rejects(persistMessage(db, "bob", {
    ...data, messageId: crypto.randomUUID(), replyToId: crypto.randomUUID(),
  }), {code: "not-found"});
});
