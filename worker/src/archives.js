import {ApiError, requireValue, validChat} from "./validation.js";
import {quota} from "./service.js";

export async function archiveConversation(db, user, data) {
  requireValue(validChat(data.chatId) && typeof data.archived === "boolean");
  await quota(db, user.uid, "archiveChats", 1, 120);
  await db.transaction(async tx => {
    const path = `chats/${data.chatId}`, chat = await tx.get(path);
    if (!chat || !chat.data.members.includes(user.uid)) throw new ApiError(403, "permission-denied");
    const archived = new Set(chat.data.archivedFor || []);
    if (data.archived) archived.add(user.uid); else archived.delete(user.uid);
    tx.set(path, {...chat.data, archivedFor: [...archived]}, true);
  });
  return {archived: data.archived};
}

export function shouldNotifyArchived(chat, recipientId, message) {
  if (!Array.isArray(chat?.archivedFor) || !chat.archivedFor.includes(recipientId)) return true;
  return message?.reply?.senderId === recipientId;
}
