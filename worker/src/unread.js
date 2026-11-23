// Stored on the existing chat summary: no per-conversation message queries.
export function pendingFor(chat, uid) {
  const value = chat.unreadCounts?.[uid];
  return Number.isSafeInteger(value) && value > 0 ? value : 0;
}
export function adjustUnread(chat, uid, amount) {
  chat.unreadCounts = {...(chat.unreadCounts || {}), [uid]: Math.max(0, pendingFor(chat, uid) + amount)};
}
// Minute buckets keep the summary compact; badges expire within the UI's one-minute refresh.
function bucket(expiresAt) { return expiresAt instanceof Date ? String(Math.ceil(expiresAt.getTime() / 60000) * 60000) : null; }
export function pruneUnread(chat, now = Date.now()) {
  let changed = false;
  const all = {...(chat.unreadExpiry || {})};
  for (const [uid, values] of Object.entries(all)) {
    const remaining = {...values};
    for (const [time, count] of Object.entries(remaining)) if (Number(time) <= now) {
      adjustUnread(chat, uid, -count); delete remaining[time]; changed = true;
    }
    all[uid] = remaining;
  }
  if (changed) chat.unreadExpiry = all;
  return changed;
}
export function addUnread(chat, uid, expiresAt) {
  pruneUnread(chat); adjustUnread(chat, uid, 1);
  const time = bucket(expiresAt);
  if (time) {
    const values = {...(chat.unreadExpiry?.[uid] || {})}; values[time] = (values[time] || 0) + 1;
    chat.unreadExpiry = {...(chat.unreadExpiry || {}), [uid]: values};
  }
}
export function consumeUnread(chat, message, uid) {
  if (!isCountedUnread(chat, message, uid)) return false;
  const time = bucket(message.expiresAt);
  if (time && Number(time) <= Date.now()) return false; // Already removed by pruneUnread.
  adjustUnread(chat, uid, -1);
  if (time) {
    const values = {...(chat.unreadExpiry?.[uid] || {})};
    if (values[time] > 1) values[time]--; else delete values[time];
    chat.unreadExpiry = {...(chat.unreadExpiry || {}), [uid]: values};
  }
  return true;
}
export function isCountedUnread(chat, message, uid) {
  const cutoff = chat.clearedBefore?.[uid];
  return message.unreadTracked === true && message.senderId !== uid && !message.readAt
    && !message.deletedForAll && !message.hiddenFor?.includes(uid)
    && (!cutoff || message.createdAt > cutoff);
}
