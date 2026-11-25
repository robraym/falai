import {ApiError} from "./validation.js";
import {googleAccessToken, httpFetch} from "./google.js";

const PATH = "appUpdates/latest";
const TOPIC = "falai-updates-v1";
export function releaseOffer(payload) {
  const release = payload?.release;
  if (payload?.repository?.full_name !== "robraym/falai" || payload.action !== "published"
      || !release || release.draft || release.prerelease) return null;
  const tag = /^v([0-9]+(?:\.[0-9]+){1,3})-apk\.([1-9][0-9]{0,9})$/.exec(release.tag_name || "");
  if (!tag || Number(tag[2]) > 2147483647) return null;
  const asset = release.assets?.find(asset => ["Falai.apk", "falai.apk"].includes(asset.name) && asset.state === "uploaded"
    && Number.isSafeInteger(asset.size) && asset.size > 0 && asset.size <= 209715200 && /^sha256:[a-f0-9]{64}$/.test(asset.digest || ""));
  return asset ? {versionName: tag[1], versionCode: Number(tag[2]), size: asset.size,
    tag: release.tag_name, asset: asset.name, digest: asset.digest} : null;
}
export async function verifyReleaseSignature(bytes, signature, secret) {
  if (!secret) throw new ApiError(503, "release-webhook-unconfigured");
  if (!/^sha256=[a-f0-9]{64}$/.test(signature || "")) throw new ApiError(401, "invalid-signature");
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), {name: "HMAC", hash: "SHA-256"}, false, ["verify"]);
  const digest = Uint8Array.from(signature.slice(7).match(/../g), value => parseInt(value, 16));
  if (!await crypto.subtle.verify("HMAC", key, digest, bytes)) throw new ApiError(401, "invalid-signature");
}
export async function receiveRelease(request, env, db) {
  if (Number(request.headers.get("content-length") || 0) > 262144) throw new ApiError(413, "request-too-large");
  const reader = request.body?.getReader(); if (!reader) throw new ApiError(400, "invalid-argument");
  const chunks = []; let size = 0;
  while (true) {
    const {done, value} = await reader.read(); if (done) break;
    size += value.length; if (size > 262144) { await reader.cancel(); throw new ApiError(413, "request-too-large"); } chunks.push(value);
  }
  const bytes = new Uint8Array(size); let offset = 0; for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  await verifyReleaseSignature(bytes, request.headers.get("x-hub-signature-256"), env.GITHUB_RELEASE_WEBHOOK_SECRET);
  if (request.headers.get("x-github-event") === "ping") return {accepted: true};
  if (request.headers.get("x-github-event") !== "release") return {accepted: false};
  let payload; try { payload = JSON.parse(new TextDecoder().decode(bytes)); } catch { throw new ApiError(400, "invalid-argument"); }
  return {accepted: await queueRelease(db, releaseOffer(payload))};
}
export async function queueRelease(db, offer) {
  if (!offer) return false;
  return db.transaction(async tx => {
    const current = (await tx.get(PATH))?.data;
    if (current?.versionCode >= offer.versionCode) return false;
    tx.set(PATH, {...offer, status: "pending", attempts: 0, nextAttemptAt: new Date(), lease: ""});
    return true;
  });
}
export async function getAppUpdate(db) {
  const data = (await db.get(PATH))?.data;
  if (!data) return {offer: null};
  const {versionName, versionCode, size, tag, asset, digest} = data;
  return {offer: {versionName, versionCode, size, tag, asset, digest}};
}
export async function sendReleasePush(env, offer, validateOnly = false) {
  const accessToken = await googleAccessToken(env);
  const data = {event: "app-update"};
  for (const key of ["versionName", "versionCode", "size", "tag", "asset", "digest"]) data[key] = String(offer[key]);
  const result = await httpFetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
    method: "POST", headers: {Authorization: "Bearer " + accessToken, "Content-Type": "application/json"},
    body: JSON.stringify({validateOnly, message: {topic: TOPIC, data,
      android: {priority: "high", ttl: "2419200s", collapse_key: "falai-app-update"}}})});
  if (!result.ok) throw new ApiError(503, "push-unavailable");
}
export async function drainRelease(db, env, push = sendReleasePush) {
  const job = await db.transaction(async tx => {
    const data = (await tx.get(PATH))?.data;
    if (!data || data.status !== "pending" || data.nextAttemptAt > new Date()) return null;
    const claimed = {...data, attempts: data.attempts + 1, lease: crypto.randomUUID(), nextAttemptAt: new Date(Date.now() + 120000)};
    tx.set(PATH, claimed, true); return claimed;
  });
  if (!job) return;
  let sent = false;
  try { await push(env, job); sent = true; } catch { console.warn("release_push_pending"); }
  await db.transaction(async tx => {
    const current = (await tx.get(PATH))?.data;
    if (current?.lease !== job.lease) return;
    tx.set(PATH, {...current, status: sent ? "sent" : "pending",
      nextAttemptAt: new Date(Date.now() + Math.min(3600, 30 * 2 ** Math.min(job.attempts, 7)) * 1000)}, true);
  });
}
