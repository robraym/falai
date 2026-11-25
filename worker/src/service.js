import {ApiError, requireValue, hash, normalizeEmail, validUid, validChat, validMessageId, normalizeMessage, chatId} from "./validation.js";
import {validatePhoto} from "./photo.js";
import {addUnread, consumeUnread, pruneUnread} from "./unread.js";
import {expiration, applyLatestAccountTimer} from "./timers.js";

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
async function dailyQuota(db, uid, action, amount, limit) {
  await db.transaction(async tx => {
    const path = `quotas/${uid}_${action}`, previous = (await tx.get(path))?.data;
    const now = Date.now(), same = previous && previous.start > now - 86400000;
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
    if (!chat) {
      const [mine, theirs] = await tx.getAll([`messageDefaults/${user.uid}`, `messageDefaults/${data.uid}`]);
      const hours = mine?.data.hours || theirs?.data.hours || 0, now = new Date();
      const changedBy = mine?.data.hours ? user.uid : data.uid;
      tx.set(`chats/${id}`, {members: [user.uid, data.uid].sort(),
        names: {[user.uid]: me.data.name, [data.uid]: them.data.name}, lastText: "", updatedAt: now,
        timerHours: hours, ...(hours ? {timerChangedBy: changedBy, timerChangedAt: now, timerFormatSince: now} : {})}, false);
      if (hours) tx.set(`chats/${id}/timerEvents/default`, {hours, changedBy, createdAt: now}, false);
    }
    else if (chat.data.names[user.uid] !== me.data.name || chat.data.names[data.uid] !== them.data.name) {
      tx.set(`chats/${id}`, {...chat.data, names: {[user.uid]: me.data.name, [data.uid]: them.data.name}}, true);
    }
  });
  return {chatId: id};
}
export async function persistMessage(db, uid, data) {
  const message = normalizeMessage(data);
  const replyToId = data.replyToId ?? null;
  requireValue(replyToId === null || (validMessageId(replyToId) && replyToId !== data.messageId));
  const jobId = data.chatId + "_" + data.messageId;
  const created = await db.transaction(async tx => {
    const path = `chats/${data.chatId}/messages/${data.messageId}`;
    const [chat, existing] = await tx.getAll([`chats/${data.chatId}`, path]);
    if (!chat || !chat.data.members.includes(uid)) throw new ApiError(403, "permission-denied");
    if (existing) {
      if (existing.data.expired) throw new ApiError(404, "not-found");
      if (existing.data.senderId !== uid || existing.data.text !== message.text || existing.data.kind !== message.kind
          || (existing.data.reply?.messageId ?? null) !== replyToId) throw new ApiError(409, "already-exists");
      return false;
    }
    const media = message.kind === "image" ? await tx.get(`chatMedia/${data.chatId}_${data.messageId}`) : null;
    if (message.kind === "image" && (!media || media.data.senderId !== uid || media.data.expiresAt <= new Date())) throw new ApiError(404, "not-found");
    const recipientId = chat.data.members.find(member => member !== uid);
    const now = new Date();
    const settings = await tx.getAll(chat.data.members.map(member => `messageDefaults/${member}`));
    const timerChanged = applyLatestAccountTimer(chat.data, settings.map((setting, index) => ({uid: chat.data.members[index], data: setting?.data})), now);
    const expiresAt = expiration(chat.data, now);
    let reply;
    if (replyToId) {
      const original = (await tx.get(`chats/${data.chatId}/messages/${replyToId}`))?.data;
      const clearedBefore = chat.data.clearedBefore?.[uid];
      if (!original || original.deletedForAll || original.expired || original.hiddenFor?.includes(uid)
          || (original.expiresAt && original.expiresAt <= now) || (clearedBefore && original.createdAt <= clearedBefore)) {
        throw new ApiError(404, "not-found");
      }
      reply = {messageId: replyToId, senderId: original.senderId, kind: original.kind || "message",
        expiresAt: original.expiresAt ?? null, ...(original.expiresAt ? {} : {text: original.text.slice(0, 220)})};
    }
    if (timerChanged) tx.set(`chats/${data.chatId}/timerEvents/default-${data.messageId}`,
      {hours: chat.data.timerHours, changedBy: chat.data.timerChangedBy, createdAt: now, source: "account"}, false);
    addUnread(chat.data, recipientId, expiresAt);
    tx.set(path, {...message, ...(reply ? {reply} : {}), senderId: uid, createdAt: now, expiresAt, expired: false, unreadTracked: true}, false);
    tx.set(`chats/${data.chatId}`, {...chat.data, lastText: message.text, updatedAt: now, lastMessageId: data.messageId,
      lastSenderId: uid, lastKind: message.kind || "message", lastExpiresAt: expiresAt, lastExpired: false, lastDeliveredAt: null, lastReadAt: null, lastDeleted: false, previewHiddenFor: []}, true);
    tx.set(`notificationOutbox/${jobId}`, {chatId: data.chatId, messageId: data.messageId, recipientId,
      createdAt: now, expiresAt, nextAttemptAt: now, status: "pending", attempts: 0, sentDeviceIds: []}, false);
    if (expiresAt) tx.set(`messageExpiry/${jobId}`, {chatId: data.chatId, messageId: data.messageId, expiresAt}, false);
    return true;
  });
  return {messageId: data.messageId, jobId, created};
}

export async function uploadChatMedia(db, user, chatId, messageId, bytes) {
  requireValue(validChat(chatId) && validMessageId(messageId) && bytes instanceof Uint8Array
    && bytes.length >= 4 && bytes.length <= 512 * 1024);
  requireValue(validBoundedJpeg(bytes));
  const chat = await db.get(`chats/${chatId}`);
  if (!chat || !chat.data.members.includes(user.uid)) throw new ApiError(403, "permission-denied");
  const recipientId = chat.data.members.find(member => member !== user.uid);
  const path = `chatMedia/${chatId}_${messageId}`, existing = await db.get(path);
  const now = new Date();
  if (existing) {
    if (existing.data.senderId === user.uid && existing.data.chatId === chatId) return {ok: true};
    throw new ApiError(409, "already-exists");
  }
  await dailyQuota(db, user.uid, "chatMedia", 1, 10);
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 8192) binary += String.fromCharCode(...bytes.subarray(offset, offset + 8192));
  const photo = btoa(binary);
  await db.commit([db.write(path, {chatId, messageId, senderId: user.uid, recipientId, data: photo,
    bytes: bytes.length, createdAt: now, expiresAt: new Date(now.getTime() + 86400000)}, false),
    db.write(`chatMediaExpiry/${chatId}_${messageId}`, {chatId, messageId, expiresAt: new Date(now.getTime() + 86400000)}, false)]);
  return {ok: true};
}
function validBoundedJpeg(bytes) {
  if (bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes[bytes.length - 2] !== 0xff || bytes[bytes.length - 1] !== 0xd9) return false;
  const frames = new Set([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf]);
  let offset = 2;
  while (offset + 3 < bytes.length) {
    if (bytes[offset++] !== 0xff) return false;
    while (offset < bytes.length && bytes[offset] === 0xff) offset++;
    const marker = bytes[offset++];
    if (marker === 0xd9 || marker === 0xda) break;
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
    if (offset + 2 > bytes.length) return false;
    const length = (bytes[offset] << 8) | bytes[offset + 1];
    if (length < 2 || offset + length > bytes.length) return false;
    if (frames.has(marker)) {
      if (length < 7) return false;
      const height = (bytes[offset + 3] << 8) | bytes[offset + 4];
      const width = (bytes[offset + 5] << 8) | bytes[offset + 6];
      return width > 0 && height > 0 && width <= 2400 && height <= 2400 && width * height <= 4000000;
    }
    offset += length;
  }
  return false;
}
export async function downloadChatMedia(db, user, data) {
  requireValue(validChat(data.chatId) && validMessageId(data.messageId));
  const [chat, media] = await db.getAll([`chats/${data.chatId}`, `chatMedia/${data.chatId}_${data.messageId}`]);
  if (!chat || !chat.data.members.includes(user.uid)) throw new ApiError(403, "permission-denied");
  if (!media || media.data.chatId !== data.chatId || media.data.messageId !== data.messageId
      || media.data.recipientId !== user.uid || media.data.expiresAt <= new Date()) throw new ApiError(404, "not-found");
  await quota(db, user.uid, "mediaDownloads", 1, 100);
  const binary = atob(media.data.data), bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
  return bytes;
}
export async function deleteChatMedia(db, user, data) {
  requireValue(validChat(data.chatId) && validMessageId(data.messageId));
  const path = `chatMedia/${data.chatId}_${data.messageId}`, expiry = `chatMediaExpiry/${data.chatId}_${data.messageId}`;
  await db.transaction(async tx => {
    const [chat, media] = await tx.getAll([`chats/${data.chatId}`, path]);
    if (!chat || !chat.data.members.includes(user.uid)) throw new ApiError(403, "permission-denied");
    if (media && media.data.recipientId !== user.uid) throw new ApiError(403, "permission-denied");
    tx.delete(path); tx.delete(expiry);
  });
  return {ok: true};
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
      if (existing.data.expired) throw new ApiError(404, "not-found");
      if (existing.data.senderId !== user.uid || existing.data.kind !== "nudge") throw new ApiError(409, "already-exists");
      return;
    }
    const now = Date.now();
    if (limit?.data.sentAt?.getTime() > now - 10000) throw new ApiError(429, "nudge-cooldown");
    const sameHour = usage && usage.data.start > now - 3600000;
    const count = (sameHour ? usage.data.count : 0) + 1;
    if (count > 20) throw new ApiError(429, "resource-exhausted");
    const createdAt = new Date(now), text = "Chamou sua atenção!";
    const settings = await tx.getAll(chat.data.members.map(member => `messageDefaults/${member}`));
    if (applyLatestAccountTimer(chat.data, settings.map((setting, index) => ({uid: chat.data.members[index], data: setting?.data})), createdAt)) {
      tx.set(`chats/${data.chatId}/timerEvents/default-${data.messageId}`,
        {hours: chat.data.timerHours, changedBy: chat.data.timerChangedBy, createdAt, source: "account"}, false);
    }
    const expiresAt = expiration(chat.data, createdAt);
    addUnread(chat.data, chat.data.members.find(uid => uid !== user.uid), expiresAt);
    tx.set(messagePath, {senderId: user.uid, kind: "nudge", text, createdAt, expiresAt, expired: false, unreadTracked: true}, false);
    tx.set(`chats/${data.chatId}`, {...chat.data, lastText: text, updatedAt: createdAt, lastMessageId: data.messageId,
      lastSenderId: user.uid, lastKind: "nudge", lastExpiresAt: expiresAt, lastExpired: false, lastDeliveredAt: null, lastReadAt: null, lastDeleted: false, previewHiddenFor: []}, true);
    tx.set(limitPath, {sentAt: createdAt});
    tx.set(quotaPath, {start: sameHour ? usage.data.start : now, count});
    tx.set(`notificationOutbox/${jobId}`, {chatId: data.chatId, messageId: data.messageId,
      recipientId: chat.data.members.find(uid => uid !== user.uid), event: "nudge", createdAt, expiresAt,
      nextAttemptAt: createdAt, status: "pending", attempts: 0, sentDeviceIds: []}, false);
    if (expiresAt) tx.set(`messageExpiry/${jobId}`, {chatId: data.chatId, messageId: data.messageId, expiresAt}, false);
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
    const summary = {...chat.data}; let summaryChanged = pruneUnread(summary);
    for (const doc of docs) {
      if (!doc || doc.data.senderId === user.uid) continue;
      const value = {...doc.data};
      let changed = false;
      if (!value.deliveredAt) { value.deliveredAt = now; changed = true; }
      if (data.state === "read" && !value.readAt) {
        if (consumeUnread(summary, value, user.uid)) summaryChanged = true;
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
