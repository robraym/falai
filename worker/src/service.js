import {ApiError, requireValue, hash, normalizeEmail, validUid, validChat, validMessageId, normalizeMessage, chatId} from "./validation.js";
import {validatePhoto} from "./photo.js";
import {adjustUnread, isCountedUnread} from "./unread.js";

export async function updateProfileAbout(db, user, data) {
  requireValue(typeof data.text === "string");
  const text = data.text.trim().replace(/\s+/g, " ");
  requireValue(text.length <= 139 && !/[\u0000-\u001f\u007f]/.test(text));
  await quota(db, user.uid, "about", 1, 20);
  if (!await db.get(`users/${user.uid}`)) throw new ApiError(404, "not-found");
  await db.commit([text ? db.write(`profileAbout/${user.uid}`, {text, updatedAt: new Date()}) : db.remove(`profileAbout/${user.uid}`)]);
  return {text};
}

export async function updateProfilePhoto(db, user, data) {
  const photo = data.photo === null ? null : validatePhoto(data.photo);
  await quota(db, user.uid, "photo", 1, 20);
  if (!await db.get(`users/${user.uid}`)) throw new ApiError(404, "not-found");
  await db.commit([photo === null ? db.remove(`profilePhotos/${user.uid}`)
    : db.write(`profilePhotos/${user.uid}`, {photo, updatedAt: new Date()})]);
  return {ok: true};
}

export async function quota(db, uid, action, amount, limit) {
  await db.transaction(async tx => {
    const path = `quotas/${uid}_${action}`;
    const previous = (await tx.get(path))?.data;
    const now = Date.now(); const same = previous && previous.start > now - 3600000;
    const count = (same ? previous.count : 0) + amount;
    if (count > limit) throw new ApiError(429, "resource-exhausted");
    tx.set(path, {start: same ? previous.start : now, count});
  });
}
export async function registerProfile(db, user, data) {
  const name = typeof data.name === "string" ? data.name.trim() : "";
  requireValue(name && name.length <= 60 && !/[\u0000-\u001f]/.test(name));
  await quota(db, user.uid, "profile", 1, 20);
  const directoryPath = `directory/${await hash(user.email)}`;
  await db.transaction(async tx => {
    const previous = await tx.get(`users/${user.uid}`);
    let oldPath, oldDirectory;
    if (previous?.data.email && previous.data.email !== user.email) {
      oldPath = `directory/${await hash(previous.data.email)}`; oldDirectory = await tx.get(oldPath);
    }
    tx.set(`users/${user.uid}`, {name, email: user.email, updatedAt: new Date()});
    tx.set(`profileNames/${user.uid}`, {name});
    tx.set(directoryPath, {uid: user.uid, name});
    if (oldDirectory?.data.uid === user.uid) tx.delete(oldPath);
  });
  return {name, email: user.email};
}
export async function discoverContacts(db, user, data) {
  requireValue(Array.isArray(data.emails) && data.emails.length <= 100);
  const emails = [...new Set(data.emails.map(normalizeEmail))];
  if (!emails.length) return {contacts: []};
  await quota(db, user.uid, "discovery", emails.length, 6000);
  const docs = await db.getAll(await Promise.all(emails.map(async email => `directory/${await hash(email)}`)));
  return {contacts: docs.flatMap((doc, index) => doc && doc.data.uid !== user.uid
    ? [{uid: doc.data.uid, name: doc.data.name, email: emails[index]}] : [])};
}
export async function openChat(db, user, data) {
  requireValue(validUid(data.uid) && data.uid !== user.uid);
  await quota(db, user.uid, "open", 1, 100);
  const id = await chatId(user.uid, data.uid);
  await db.transaction(async tx => {
    const [chat, me, them] = await tx.getAll([`chats/${id}`, `users/${user.uid}`, `users/${data.uid}`]);
    if (!me || !them) throw new ApiError(404, "not-found");
    if (!chat) tx.set(`chats/${id}`, {members: [user.uid, data.uid].sort(),
      names: {[user.uid]: me.data.name, [data.uid]: them.data.name}, lastText: "", updatedAt: new Date()}, false);
    else if (chat.data.names[user.uid] !== me.data.name || chat.data.names[data.uid] !== them.data.name) {
      tx.set(`chats/${id}`, {...chat.data, names: {[user.uid]: me.data.name, [data.uid]: them.data.name}}, true);
    }
  });
  return {chatId: id};
}
export async function persistMessage(db, uid, data) {
  const message = normalizeMessage(data);
  const jobId = data.chatId + "_" + data.messageId;
  const created = await db.transaction(async tx => {
    const path = `chats/${data.chatId}/messages/${data.messageId}`;
    const [chat, existing] = await tx.getAll([`chats/${data.chatId}`, path]);
    if (!chat || !chat.data.members.includes(uid)) throw new ApiError(403, "permission-denied");
    if (existing) {
      if (existing.data.senderId !== uid || existing.data.text !== message.text) throw new ApiError(409, "already-exists");
      return false;
    }
    const recipientId = chat.data.members.find(member => member !== uid);
    const now = new Date();
    adjustUnread(chat.data, recipientId, 1);
    tx.set(path, {...message, senderId: uid, createdAt: now, unreadTracked: true}, false);
    tx.set(`chats/${data.chatId}`, {...chat.data, lastText: message.text, updatedAt: now, lastMessageId: data.messageId,
      lastSenderId: uid, lastKind: "message", lastDeliveredAt: null, lastReadAt: null, lastDeleted: false, previewHiddenFor: []}, true);
    tx.set(`notificationOutbox/${jobId}`, {chatId: data.chatId, messageId: data.messageId, recipientId,
      createdAt: now, nextAttemptAt: now, status: "pending", attempts: 0, sentDeviceIds: []}, false);
    return true;
  });
  return {messageId: data.messageId, jobId, created};
}
export async function sendNudge(db, user, data) {
  requireValue(validChat(data.chatId) && validMessageId(data.messageId));
  const jobId = `${data.chatId}_${data.messageId}`;
  await db.transaction(async tx => {
    const messagePath = `chats/${data.chatId}/messages/${data.messageId}`;
    const limitPath = `nudgeLimits/${data.chatId}_${user.uid}`, quotaPath = `quotas/${user.uid}_nudges`;
    const [chat, existing, limit, usage] = await tx.getAll([`chats/${data.chatId}`, messagePath, limitPath, quotaPath]);
    if (!chat || !chat.data.members.includes(user.uid)) throw new ApiError(403, "permission-denied");
    if (existing) {
      if (existing.data.senderId !== user.uid || existing.data.kind !== "nudge") throw new ApiError(409, "already-exists");
      return;
    }
    const now = Date.now();
    if (limit?.data.sentAt?.getTime() > now - 10000) throw new ApiError(429, "nudge-cooldown");
    const sameHour = usage && usage.data.start > now - 3600000;
    const count = (sameHour ? usage.data.count : 0) + 1;
    if (count > 20) throw new ApiError(429, "resource-exhausted");
    const createdAt = new Date(now), text = "Chamou sua atenção!";
    adjustUnread(chat.data, chat.data.members.find(uid => uid !== user.uid), 1);
    tx.set(messagePath, {senderId: user.uid, kind: "nudge", text, createdAt, unreadTracked: true}, false);
    tx.set(`chats/${data.chatId}`, {...chat.data, lastText: text, updatedAt: createdAt, lastMessageId: data.messageId,
      lastSenderId: user.uid, lastKind: "nudge", lastDeliveredAt: null, lastReadAt: null, lastDeleted: false, previewHiddenFor: []}, true);
    tx.set(limitPath, {sentAt: createdAt});
    tx.set(quotaPath, {start: sameHour ? usage.data.start : now, count});
    tx.set(`notificationOutbox/${jobId}`, {chatId: data.chatId, messageId: data.messageId,
      recipientId: chat.data.members.find(uid => uid !== user.uid), event: "nudge", createdAt,
      nextAttemptAt: createdAt, status: "pending", attempts: 0, sentDeviceIds: []}, false);
  });
  return {messageId: data.messageId, jobId};
}
export async function registerDevice(db, user, data) {
  requireValue(typeof data.deviceId === "string" && /^[a-f0-9-]{36}$/.test(data.deviceId));
  requireValue(typeof data.token === "string" && data.token.length >= 20 && data.token.length <= 4096 && !/[\u0000-\u0020\u007f]/.test(data.token));
  if (data.retentionHours !== undefined) requireValue([0, 24, 168, 2160].includes(data.retentionHours)
    && Number.isSafeInteger(data.retentionRevision) && data.retentionRevision >= 0);
  if (data.buildTime !== undefined) requireValue(Number.isSafeInteger(data.buildTime) && data.buildTime >= 1577836800000 && data.buildTime <= Date.now() + 86400000);
  if (data.versionName !== undefined || data.versionCode !== undefined) requireValue(typeof data.versionName === "string" && /^[A-Za-z0-9._+-]{1,40}$/.test(data.versionName)
    && Number.isSafeInteger(data.versionCode) && data.versionCode >= 1 && data.versionCode <= 2147483647);
  await quota(db, user.uid, "devices", 1, 120);
  const path = `devices/${await hash(user.uid + ":" + data.deviceId)}`;
  await db.transaction(async tx => {
    const registryPath = `deviceRegistrations/${user.uid}`;
    const registry = (await tx.get(registryPath))?.data;
    const previous = registry?.paths || [];
    const snapshots = await tx.getAll(previous);
    const current = snapshots.filter(doc => doc && doc.data.updatedAt > new Date(Date.now() - 60 * 86400000));
    const paths = current.map(doc => doc.path);
    if (!paths.includes(path) && paths.length >= 20) throw new ApiError(429, "resource-exhausted");
    if (!paths.includes(path)) paths.push(path);
    tx.set(path, {uid: user.uid, token: data.token, updatedAt: new Date()});
    // The most recently registered installation, not necessarily the newest build.
    // Old clients explicitly clear this public date instead of leaving a misleading value.
    tx.set(`profileBuild/${user.uid}`, {buildTime: data.buildTime ? new Date(data.buildTime) : null,
      versionName: data.versionName || null, versionCode: data.versionCode || null, updatedAt: new Date()});
    tx.set(registryPath, {paths});
    for (const stale of snapshots) if (stale && !paths.includes(stale.path)) tx.delete(stale.path);
  });
  if (data.retentionHours !== undefined) await publishRetentionNotice(db, user.uid, data.deviceId, data.retentionHours, data.retentionRevision);
  return {ok: true};
}
async function publishRetentionNotice(db, uid, deviceId, hours, revision) {
  const key = await hash(deviceId);
  await db.transaction(async tx => {
    const path = `retentionDevices/${uid}`, previous = await tx.get(path);
    const devices = {...(previous?.data.devices || {})};
    const current = devices[key];
    if (!current && hours === 0) return;
    if (revision === undefined) revision = Math.max(Date.now(), (current?.revision || 0) + 1);
    if (current && (revision < current.revision || (revision === current.revision && hours === current.hours))) return;
    if (current && revision === current.revision && hours !== current.hours) throw new ApiError(409, "already-exists");
    const durations = values => [...new Set(Object.values(values).map(value => value.hours).filter(value => value > 0))].sort((a, b) => a - b);
    const before = durations(devices);
    if (!current && Object.keys(devices).length >= 20) {
      const inactive = Object.entries(devices).filter(([, value]) => value.hours === 0).sort((a, b) => a[1].revision - b[1].revision)[0];
      if (!inactive) throw new ApiError(429, "resource-exhausted");
      delete devices[inactive[0]];
    }
    devices[key] = {hours, revision};
    tx.set(path, {devices});
    const after = durations(devices);
    if (JSON.stringify(before) !== JSON.stringify(after)) tx.set(`profileRetention/${uid}`, {durations: after, updatedAt: new Date()});
  });
}
export async function acknowledgeMessages(db, user, data) {
  requireValue(typeof data.chatId === "string" && /^[a-f0-9]{64}$/.test(data.chatId));
  requireValue(data.state === "delivered" || data.state === "read");
  requireValue(Array.isArray(data.messageIds) && data.messageIds.length > 0 && data.messageIds.length <= 50);
  requireValue(data.messageIds.every(id => typeof id === "string" && /^[a-f0-9-]{36}$/.test(id)));
  const ids = [...new Set(data.messageIds)];
  await quota(db, user.uid, "receipts", ids.length, 12000);
  await db.transaction(async tx => {
    const chat = await tx.get(`chats/${data.chatId}`);
    if (!chat || !chat.data.members.includes(user.uid)) throw new ApiError(403, "permission-denied");
    const docs = await tx.getAll(ids.map(id => `chats/${data.chatId}/messages/${id}`));
    const now = new Date();
    const summary = {...chat.data}; let summaryChanged = false;
    for (const doc of docs) {
      if (!doc || doc.data.senderId === user.uid) continue;
      const value = {...doc.data};
      let changed = false;
      if (!value.deliveredAt) { value.deliveredAt = now; changed = true; }
      if (data.state === "read" && !value.readAt) {
        if (isCountedUnread(summary, value, user.uid)) { adjustUnread(summary, user.uid, -1); summaryChanged = true; }
        value.readAt = now; changed = true;
      }
      if (changed) tx.set(doc.path, value, true);
      if ((doc.path.endsWith("/" + summary.lastMessageId) || (!summary.lastMessageId && summary.updatedAt?.getTime() === value.createdAt?.getTime())) && value.senderId !== user.uid) {
        if (!summary.lastMessageId) { summary.lastMessageId = doc.path.split("/").at(-1); summary.lastSenderId = value.senderId; summaryChanged = true; }
        if (!summary.lastDeliveredAt) { summary.lastDeliveredAt = value.deliveredAt; summaryChanged = true; }
        if (value.readAt && !summary.lastReadAt) { summary.lastReadAt = value.readAt; summaryChanged = true; }
      }
    }
    if (summaryChanged) tx.set(`chats/${data.chatId}`, summary, true);
  });
  return {ok: true};
}
export async function unregisterDevice(db, user, data) {
  requireValue(typeof data.deviceId === "string" && /^[a-f0-9-]{36}$/.test(data.deviceId));
  await db.commit([db.remove(`devices/${await hash(user.uid + ":" + data.deviceId)}`)]);
  return {ok: true};
}
