import {ApiError, requireValue, validChat, validMessageId, normalizeMessage} from "./validation.js";
import {quota} from "./service.js";
import {adjustUnread, isCountedUnread} from "./unread.js";

export async function changeMessage(db, user, data, editing = false) {
  requireValue(validChat(data.chatId) && validMessageId(data.messageId));
  if (!editing) requireValue(data.scope === "self" || data.scope === "all");
  const replacement = editing ? normalizeMessage(data).text : null;
  await quota(db, user.uid, "changes", 1, 600);
  return db.transaction(async tx => {
    const path = `chats/${data.chatId}/messages/${data.messageId}`;
    const [chat, message] = await tx.getAll([`chats/${data.chatId}`, path]);
    if (!chat || !chat.data.members.includes(user.uid)) throw new ApiError(403, "permission-denied");
    if (!message) throw new ApiError(404, "not-found");
    if ((editing || data.scope === "all") && message.data.senderId !== user.uid) throw new ApiError(403, "permission-denied");
    const last = chat.data.lastMessageId === data.messageId || (!chat.data.lastMessageId && chat.data.updatedAt?.getTime() === message.data.createdAt?.getTime());
    const summary = {...chat.data}; let jobId, countsChanged = false;
    if (!editing) {
      for (const uid of summary.members) {
        if ((data.scope === "all" || uid === user.uid) && isCountedUnread(summary, message.data, uid)) {
          adjustUnread(summary, uid, -1); countsChanged = true;
        }
      }
    }
    if (editing) {
      if (message.data.kind === "nudge") throw new ApiError(400, "invalid-argument");
      if (message.data.deletedForAll) throw new ApiError(409, "already-exists");
      tx.set(path, {...message.data, text: replacement, editedAt: new Date()}, true);
      if (last) summary.lastText = replacement;
    } else if (data.scope === "self") {
      const hiddenFor = [...new Set([...(message.data.hiddenFor || []), user.uid])];
      tx.set(path, {...message.data, hiddenFor}, true);
      if (last) summary.previewHiddenFor = [...new Set([...(summary.previewHiddenFor || []), user.uid])];
    } else {
      tx.set(path, {...message.data, text: "", deletedForAll: true, deletedAt: new Date()}, true);
      if (last) { summary.lastText = ""; summary.lastDeleted = true; }
      const recipientId = chat.data.members.find(uid => uid !== user.uid);
      tx.delete(`notificationOutbox/${data.chatId}_${data.messageId}`);
      jobId = `${data.chatId}_${data.messageId}_deleted`;
      tx.set(`notificationOutbox/${jobId}`, {chatId: data.chatId, messageId: data.messageId, recipientId, event: "deleted",
        createdAt: new Date(), nextAttemptAt: new Date(), status: "pending", attempts: 0, sentDeviceIds: []});
    }
    if (last || countsChanged) tx.set(`chats/${data.chatId}`, summary, true);
    return {ok: true, ...(jobId ? {jobId} : {})};
  });
}
export async function deleteConversation(db, user, data) {
  requireValue(validChat(data.chatId)); await quota(db, user.uid, "clearChats", 1, 100);
  await db.transaction(async tx => {
    const path = `chats/${data.chatId}`, chat = await tx.get(path);
    if (!chat || !chat.data.members.includes(user.uid)) throw new ApiError(403, "permission-denied");
    tx.set(path, {...chat.data, unreadCounts: {...(chat.data.unreadCounts || {}), [user.uid]: 0},
      clearedBefore: {...(chat.data.clearedBefore || {}), [user.uid]: new Date()}}, true);
  });
  return {ok: true};
}
