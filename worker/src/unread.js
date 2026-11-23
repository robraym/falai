// Stored on the existing chat summary: no per-conversation message queries.
export function pendingFor(chat, uid) {
  const value = chat.unreadCounts?.[uid];
  return Number.isSafeInteger(value) && value > 0 ? value : 0;
}
export function adjustUnread(chat, uid, amount) {
  chat.unreadCounts = {...(chat.unreadCounts || {}), [uid]: Math.max(0, pendingFor(chat, uid) + amount)};
}
export function isCountedUnread(chat, message, uid) {
  const cutoff = chat.clearedBefore?.[uid];
  return message.unreadTracked === true && message.senderId !== uid && !message.readAt
    && !message.deletedForAll && !message.hiddenFor?.includes(uid)
    && (!cutoff || message.createdAt > cutoff);
}
