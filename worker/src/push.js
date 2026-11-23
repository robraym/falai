import {googleAccessToken, httpFetch} from "./google.js";
import {ApiError} from "./validation.js";

export function notificationPreview(text) {
  let result = "", size = 0;
  for (const character of text) {
    const bytes = new TextEncoder().encode(character).length;
    if (size + bytes > 1200) return result + "…";
    result += character; size += bytes;
  }
  return result;
}

export async function sendPush(env, token, job, validateOnly = false) {
  const accessToken = await googleAccessToken(env);
  const message = validateOnly ? {topic: "falai-configuration-check", notification: {title: "Falai", body: "Verificação de configuração"}}
    : {token,
      data: {chatId: job.chatId, messageId: job.messageId, recipientId: job.recipientId,
        senderId: job.senderId || "", senderName: job.senderName || "Falai", text: notificationPreview(job.text || ""), event: job.event || "message",
        ...(job.createdAt instanceof Date ? {createdAt: String(job.createdAt.getTime())} : {}),
        ...(job.event === "nudge" ? {expiresAt: String(job.createdAt.getTime() + 60000)} : {})},
      // Data-only invokes the Android service in foreground and background, so
      // the same ICQ alarm-volume player handles both without duplicate sounds.
      android: {priority: "high", ttl: job.event === "nudge" ? `${Math.max(0, Math.min(60, Math.floor((job.createdAt.getTime() + 60000 - Date.now()) / 1000)))}s` : "2419200s"}};
  const response = await httpFetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
    method: "POST", headers: {Authorization: "Bearer " + accessToken, "Content-Type": "application/json"},
    body: JSON.stringify({validateOnly, message})});
  const result = await response.json();
  const details = result.error?.details || [];
  if (response.ok) return "sent";
  if (details.some(item => item.errorCode === "UNREGISTERED" || item.errorCode === "SENDER_ID_MISMATCH")) return "expired";
  throw new ApiError(503, "push-unavailable");
}
export async function dispatchJob(db, env, path, push = sendPush) {
  const claimed = await db.transaction(async tx => {
    const snapshot = await tx.get(path); const job = snapshot?.data;
    if (!job || job.status !== "pending" || job.nextAttemptAt > new Date()) return null;
    if (job.event === "nudge" && job.createdAt.getTime() + 60000 <= Date.now()) { tx.delete(path); return null; }
    const lease = crypto.randomUUID();
    tx.set(path, {...job, lease, attempts: job.attempts + 1, nextAttemptAt: new Date(Date.now() + 120000)}, true);
    return {...job, lease, attempts: job.attempts + 1};
  });
  if (!claimed) return;
  const acknowledged = new Set(claimed.sentDeviceIds);
  let remaining = false;
  try {
    const chat = await db.get(`chats/${claimed.chatId}`);
    const message = await db.get(`chats/${claimed.chatId}/messages/${claimed.messageId}`);
    claimed.text = message?.data.deletedForAll ? "" : message?.data.text || "";
    if (message?.data.kind === "nudge") claimed.event = "nudge";
    if (message?.data.deletedForAll) claimed.event = "deleted";
    claimed.senderId = message?.data.senderId || "";
    const sender = claimed.senderId ? await db.get(`users/${claimed.senderId}`) : null;
    claimed.senderName = sender?.data.name || chat?.data.names?.[claimed.senderId] || "Falai";
    // Collection is indexed by uid; fixed limit bounds reads and Worker subrequests.
    const devices = await db.query("devices", [["uid", "EQUAL", claimed.recipientId]], undefined, 100);
    const pending = devices.filter(device => !acknowledged.has(device.path));
    const chunk = pending.slice(0, 5); remaining = pending.length > chunk.length;
    for (const device of chunk) {
      const result = await push(env, device.data.token, claimed);
      acknowledged.add(device.path);
      if (result === "expired") await db.commit([db.remove(device.path)]);
    }
    await db.transaction(async tx => {
      const latest = (await tx.get(path))?.data;
      if (!latest || latest.lease !== claimed.lease) return;
      if (!remaining) tx.delete(path);
      else tx.set(path, {...latest, sentDeviceIds: [...acknowledged], nextAttemptAt: new Date()}, true);
    });
  } catch {
    // Persist completed device deliveries even if a later device failed.
    await db.transaction(async tx => {
      const latest = (await tx.get(path))?.data;
      if (!latest || latest.lease !== claimed.lease) return;
      const seconds = Math.min(3600, 30 * 2 ** Math.min(claimed.attempts, 7));
      tx.set(path, {...latest, sentDeviceIds: [...acknowledged], nextAttemptAt: new Date(Date.now() + seconds * 1000)}, true);
    });
    // No tokens, message text or credentials are logged.
    console.warn("push_retry_pending");
  }
}
export async function drainOutbox(db, env) {
  const jobs = await db.query("notificationOutbox", [["status", "EQUAL", "pending"], ["nextAttemptAt", "LESS_THAN_OR_EQUAL", new Date()]], "nextAttemptAt", 2);
  for (const job of jobs) await dispatchJob(db, env, job.path);
}
