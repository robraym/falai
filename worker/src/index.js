import {ApiError, requireValue} from "./validation.js";
import {verifyUser, serviceAccount} from "./google.js";
import {Firestore} from "./firestore.js";
import {registerProfile, discoverContacts, openChat, persistMessage, quota, registerDevice, unregisterDevice, acknowledgeMessages, updateProfilePhoto, updateProfileAbout, sendNudge, uploadChatMedia, downloadChatMedia, deleteChatMedia} from "./service.js";
import {dispatchJob, drainOutbox, sendPush} from "./push.js";
import {changeMessage, deleteConversation, clearConversation, setMessageReaction} from "./mutations.js";
import {setDefaultTimer, setChatTimer, expireMessages} from "./timers.js";
import {archiveConversation} from "./archives.js";
import {receiveRelease, getAppUpdate, drainRelease} from "./releases.js";

const VERSION = "falai-worker-1";
let lastHealth;
function response(value, status = 200) {
  return new Response(JSON.stringify(value), {status, headers: {"Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"}});
}
async function readBody(request) {
  if (Number(request.headers.get("content-length") || 0) > 32768) throw new ApiError(413, "request-too-large");
  const reader = request.body?.getReader(); if (!reader) throw new ApiError(400, "invalid-argument");
  const chunks = []; let size = 0;
  while (true) {
    const {done, value} = await reader.read(); if (done) break;
    size += value.length; if (size > 32768) { await reader.cancel(); throw new ApiError(413, "request-too-large"); } chunks.push(value);
  }
  const bytes = new Uint8Array(size); let offset = 0; for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  try { const value = JSON.parse(new TextDecoder().decode(bytes)); requireValue(value && typeof value === "object" && !Array.isArray(value)); return value; }
  catch { throw new ApiError(400, "invalid-argument"); }
}
async function readMedia(request) {
  const maximum = 512 * 1024;
  if (Number(request.headers.get("content-length") || 0) > maximum) throw new ApiError(413, "request-too-large");
  const reader = request.body?.getReader(); if (!reader) throw new ApiError(400, "invalid-argument");
  const chunks = []; let size = 0;
  while (true) { const {done, value} = await reader.read(); if (done) break;
    size += value.length; if (size > maximum) { await reader.cancel(); throw new ApiError(413, "request-too-large"); } chunks.push(value); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  return bytes;
}
async function health(env, db) {
  if (lastHealth?.until > Date.now()) return lastHealth.value;
  serviceAccount(env);
  // Tests real OAuth, Firestore permission and readiness of the outbox composite index.
  await db.query("notificationOutbox", [["status", "EQUAL", "pending"], ["nextAttemptAt", "LESS_THAN_OR_EQUAL", new Date()]], "nextAttemptAt", 1);
  await sendPush(env, null, null, true); // validate_only: nothing is delivered to any device or topic.
  const value = {service: "Falaí", version: VERSION, database: "ok", fcmValidation: "ok"};
  lastHealth = {until: Date.now() + 300000, value}; return value;
}
export default {
  async fetch(request, env, context) {
    try {
      const url = new URL(request.url);
      const db = new Firestore(env);
      if (request.method === "POST" && url.pathname === "/hooks/github/releases") {
        const result = await receiveRelease(request, env, db);
        if (result.accepted && request.headers.get("x-github-event") === "release")
          context.waitUntil(drainRelease(db, env).catch(() => console.warn("release_push_pending")));
        return response(result);
      }
      if (request.method === "GET" && url.pathname === "/health") return response(await health(env, db));
      if (request.method !== "POST" || !url.pathname.startsWith("/v1/")) return response({error: "not-found"}, 404);
      const action = url.pathname.slice(4);
      const auth = request.headers.get("Authorization") || "";
      if (!auth.startsWith("Bearer ")) throw new ApiError(401, "unauthenticated");
      const user = await verifyUser(auth.slice(7), env.FIREBASE_PROJECT_ID);
      if (action === "uploadChatMedia") {
        if (request.headers.get("Content-Type") !== "image/jpeg") throw new ApiError(400, "invalid-argument");
        return response(await uploadChatMedia(db, user, request.headers.get("X-Chat-Id"), request.headers.get("X-Message-Id"), await readMedia(request)));
      }
      if (action === "downloadChatMedia") {
        const bytes = await downloadChatMedia(db, user, await readBody(request));
        return new Response(bytes, {headers: {"Content-Type": "image/jpeg", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"}});
      }
      const handlers = {registerProfile, discoverContacts, openChat, registerDevice, unregisterDevice, acknowledgeMessages, updateProfilePhoto, updateProfileAbout, sendNudge, getAppUpdate,
        deleteChatMedia,
        editMessage: (db, user, data) => changeMessage(db, user, data, true), deleteMessage: changeMessage,
        deleteConversation, clearConversation, archiveConversation, setMessageReaction, setDefaultTimer, setChatTimer};
      if (!(Object.hasOwn(handlers, action) || action === "sendMessage")) return response({error: "not-found"}, 404);
      const data = await readBody(request);
      if (action === "sendMessage") {
        await quota(db, user.uid, "messages", 1, 600);
        const result = await persistMessage(db, user.uid, data);
        // Retry can also nudge an existing pending job; no message or outbox is recreated.
        context.waitUntil(dispatchJob(db, env, "notificationOutbox/" + result.jobId).catch(() => console.warn("push_pending")));
        return response({messageId: result.messageId});
      }
      const result = await handlers[action](db, user, data);
      if (result.jobId) context.waitUntil(dispatchJob(db, env, "notificationOutbox/" + result.jobId).catch(() => console.warn("push_pending")));
      return response(result);
    } catch (error) {
      return response({error: error instanceof ApiError ? error.code : "unavailable"}, error instanceof ApiError ? error.status : 503);
    }
  },
  async scheduled(controller, env, context) {
    context.waitUntil((async () => {
      const db = new Firestore(env);
      await drainOutbox(db, env).catch(() => console.warn("outbox_retry_failed"));
      await expireMessages(db).catch(() => console.warn("message_expiry_failed"));
      await drainRelease(db, env).catch(() => console.warn("release_push_pending"));
    })());
  }
};
