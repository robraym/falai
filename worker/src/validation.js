export class ApiError extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}
export function requireValue(value, code = "invalid-argument") {
  if (!value) throw new ApiError(400, code);
}
export async function hash(value) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(bytes)].map(b => b.toString(16).padStart(2, "0")).join("");
}
export const validUid = value => typeof value === "string" && /^[A-Za-z0-9_-]{1,128}$/.test(value);
export const validChat = value => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
export const validMessageId = value => typeof value === "string" && /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(value);
export function normalizeEmail(value) {
  requireValue(typeof value === "string" && value.length <= 254);
  const email = value.trim().toLowerCase();
  requireValue(/^[^\s@/\\\u0000-\u001f]+@[^\s@/\\\u0000-\u001f]+\.[^\s@/\\\u0000-\u001f]+$/.test(email));
  // Do not remove dots or plus tags: they are not interchangeable at all providers.
  return email;
}
export async function chatId(a, b) { return hash(JSON.stringify([a, b].sort())); }
export function normalizeMessage(data) {
  requireValue(data && validChat(data.chatId) && validMessageId(data.messageId));
  if (data.kind === "image") {
    requireValue(data.mediaId === data.messageId);
    return {text: "📷 Foto", kind: "image", mediaId: data.mediaId};
  }
  requireValue(typeof data.text === "string" && data.text.trim().length > 0 && data.text.trim().length <= 4000);
  return {text: data.text.trim()};
}
export function firebaseClaims(payload, project, now = Math.floor(Date.now() / 1000)) {
  requireValue(payload && payload.aud === project && payload.iss === `https://securetoken.google.com/${project}`
    && validUid(payload.sub) && Number.isInteger(payload.exp) && payload.exp > now
    && Number.isInteger(payload.iat) && payload.iat <= now + 30
    && Number.isInteger(payload.auth_time) && payload.auth_time <= now + 30
    && payload.email_verified === true && payload.firebase?.sign_in_provider === "google.com", "unauthenticated");
  return {uid: payload.sub, email: normalizeEmail(payload.email)};
}
