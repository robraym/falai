import {ApiError, requireValue, validChat, validMessageId} from "./validation.js";
import {quota} from "./service.js";
import {pruneUnread} from "./unread.js";

export const TIMER_HOURS = [0, 24, 168, 2160];
export function expiration(chat, createdAt) {
  return TIMER_HOURS.includes(chat.timerHours) && chat.timerHours > 0 ? new Date(createdAt.getTime() + chat.timerHours * 3600000) : null;
}
// A newly chosen account timer takes effect on the next send in an existing chat.
// Older settings from the recipient must not undo the newer shared conversation timer.
export function applyAccountTimer(chat, setting, senderId, now) {
  if (!setting || !TIMER_HOURS.includes(setting.hours) || !(setting.updatedAt instanceof Date)
      || !Number.isFinite(setting.updatedAt.getTime())) return false;
  const changedAt = chat.timerChangedAt || chat.timerFormatSince;
  if (changedAt instanceof Date && setting.updatedAt <= changedAt) return false;
  if ((chat.timerHours || 0) === setting.hours) return false;
  chat.timerHours = setting.hours;
  chat.timerChangedBy = senderId;
  chat.timerChangedAt = now;
  chat.timerFormatSince ||= now;
  return true;
}
export function applyLatestAccountTimer(chat, accounts, now) {
  const latest = accounts.filter(account => account?.data && TIMER_HOURS.includes(account.data.hours)
      && account.data.updatedAt instanceof Date && Number.isFinite(account.data.updatedAt.getTime()))
    .sort((a, b) => b.data.updatedAt - a.data.updatedAt || a.uid.localeCompare(b.uid))[0];
  return latest ? applyAccountTimer(chat, latest.data, latest.uid, now) : false;
}
export async function setDefaultTimer(db, user, data) {
  requireValue(TIMER_HOURS.includes(data.hours));
  await quota(db, user.uid, "defaultTimer", 1, 60);
  await db.commit([db.write(`messageDefaults/${user.uid}`, {hours: data.hours, updatedAt: new Date()})]);
  return {hours: data.hours};
}
export async function setChatTimer(db, user, data) {
  requireValue(validChat(data.chatId) && validMessageId(data.requestId) && TIMER_HOURS.includes(data.hours));
  await quota(db, user.uid, "chatTimer", 1, 120);
  return db.transaction(async tx => {
    const path = `chats/${data.chatId}`, eventPath = `${path}/timerEvents/${data.requestId}`;
    const [chat, event] = await tx.getAll([path, eventPath]);
    if (!chat || !chat.data.members.includes(user.uid)) throw new ApiError(403, "permission-denied");
    if (event) {
      if (event.data.changedBy !== user.uid || event.data.hours !== data.hours) throw new ApiError(409, "already-exists");
      return {hours: chat.data.timerHours || 0};
    }
    if ((chat.data.timerHours || 0) === data.hours) return {hours: data.hours};
    const now = new Date();
    tx.set(path, {...chat.data, timerHours: data.hours, timerChangedBy: user.uid, timerChangedAt: now,
      timerFormatSince: chat.data.timerFormatSince || now}, true);
    tx.set(eventPath, {hours: data.hours, changedBy: user.uid, createdAt: now}, false);
    return {hours: data.hours};
  });
}
// Bounded server cleanup on the existing Free Worker cron. Keep small tombstones to
// prevent an old message UUID being replayed after its content has expired.
export async function expireMessages(db) {
  const jobs = await db.query("messageExpiry", [["expiresAt", "LESS_THAN_OR_EQUAL", new Date()]], "expiresAt", 2);
  for (const job of jobs) await db.transaction(async tx => {
    const [current, chat, message] = await tx.getAll([job.path, `chats/${job.data.chatId}`, `chats/${job.data.chatId}/messages/${job.data.messageId}`]);
    if (!current || current.data.expiresAt > new Date()) return;
    if (!message) { tx.delete(job.path); return; }
    const value = message.data;
    if (!(value.expiresAt instanceof Date)) { tx.delete(job.path); return; }
    if (value.expiresAt > new Date()) { tx.set(job.path, {...current.data, expiresAt: value.expiresAt}, true); return; }
    tx.set(message.path, {senderId: value.senderId, createdAt: value.createdAt, expiresAt: value.expiresAt, expired: true, unreadTracked: false}, true);
    if (chat) {
      const summary = {...chat.data}; let changed = pruneUnread(summary);
      if (summary.lastMessageId === job.data.messageId) { summary.lastText = ""; summary.lastExpired = true; changed = true; }
      if (changed) tx.set(chat.path, summary, true);
    }
    tx.delete(`notificationOutbox/${job.data.chatId}_${job.data.messageId}`); tx.delete(job.path);
  });
}
