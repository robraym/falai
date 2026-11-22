import {ApiError, requireValue, hash, normalizeEmail, validUid, normalizeMessage, chatId} from "./validation.js";

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
    tx.set(path, {...message, senderId: uid, createdAt: now}, false);
    tx.set(`chats/${data.chatId}`, {...chat.data, lastText: message.text, updatedAt: now}, true);
    tx.set(`notificationOutbox/${jobId}`, {chatId: data.chatId, messageId: data.messageId, recipientId,
      createdAt: now, nextAttemptAt: now, status: "pending", attempts: 0, sentDeviceIds: []}, false);
    return true;
  });
  return {messageId: data.messageId, jobId, created};
}
export async function registerDevice(db, user, data) {
  requireValue(typeof data.deviceId === "string" && /^[a-f0-9-]{36}$/.test(data.deviceId));
  requireValue(typeof data.token === "string" && data.token.length >= 20 && data.token.length <= 4096 && !/[\u0000-\u0020\u007f]/.test(data.token));
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
    tx.set(registryPath, {paths});
    for (const stale of snapshots) if (stale && !paths.includes(stale.path)) tx.delete(stale.path);
  });
  return {ok: true};
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
    for (const doc of docs) {
      if (!doc || doc.data.senderId === user.uid) continue;
      const value = {...doc.data};
      let changed = false;
      if (!value.deliveredAt) { value.deliveredAt = now; changed = true; }
      if (data.state === "read" && !value.readAt) { value.readAt = now; changed = true; }
      if (changed) tx.set(doc.path, value, true);
    }
  });
  return {ok: true};
}
export async function unregisterDevice(db, user, data) {
  requireValue(typeof data.deviceId === "string" && /^[a-f0-9-]{36}$/.test(data.deviceId));
  await db.commit([db.remove(`devices/${await hash(user.uid + ":" + data.deviceId)}`)]);
  return {ok: true};
}
