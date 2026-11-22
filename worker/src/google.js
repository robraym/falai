import {ApiError, firebaseClaims, requireValue} from "./validation.js";

const encoder = new TextEncoder();
const keyCaches = new Map();
const oauthCaches = new Map();
export function base64url(bytes) {
  return btoa(String.fromCharCode(...new Uint8Array(bytes))).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}
export function decode64(value) {
  const normalized = value.replaceAll("-", "+").replaceAll("_", "/");
  return Uint8Array.from(atob(normalized + "=".repeat((4 - normalized.length % 4) % 4)), c => c.charCodeAt(0));
}
export async function httpFetch(url, options = {}) {
  return fetch(url, {...options, signal: AbortSignal.timeout(15000)});
}
export async function verifyUser(token, project) {
  try {
    if (typeof token !== "string" || token.length > 10000) throw new Error();
    const parts = token.split("."); if (parts.length !== 3) throw new Error();
    const header = JSON.parse(new TextDecoder().decode(decode64(parts[0])));
    if (header.alg !== "RS256" || typeof header.kid !== "string") throw new Error();
    let cache = keyCaches.get("firebase");
    if (!cache || cache.until < Date.now()) {
      const response = await httpFetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
      if (!response.ok) throw new ApiError(503, "unavailable");
      cache = {keys: (await response.json()).keys, until: Date.now() + 300000}; keyCaches.set("firebase", cache);
    }
    const jwk = cache.keys.find(key => key.kid === header.kid); if (!jwk) throw new Error();
    const key = await crypto.subtle.importKey("jwk", jwk, {name: "RSASSA-PKCS1-v1_5", hash: "SHA-256"}, false, ["verify"]);
    const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, decode64(parts[2]), encoder.encode(parts[0] + "." + parts[1]));
    if (!valid) throw new Error();
    return firebaseClaims(JSON.parse(new TextDecoder().decode(decode64(parts[1]))), project);
  } catch (error) {
    if (error instanceof ApiError && error.status === 503) throw error;
    throw new ApiError(401, "unauthenticated");
  }
}
export function serviceAccount(env) {
  try {
    const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
    requireValue(account.project_id === env.FIREBASE_PROJECT_ID && typeof account.client_email === "string"
      && account.client_email.endsWith(".iam.gserviceaccount.com") && typeof account.private_key === "string");
    return account;
  } catch { throw new ApiError(503, "server-not-configured"); }
}
export async function googleAccessToken(env) {
  const account = serviceAccount(env);
  const cacheKey = account.client_email + ":" + account.private_key_id;
  const cached = oauthCaches.get(cacheKey);
  if (cached?.until > Date.now()) return cached.token;
  if (cached?.pending) return cached.pending;
  const pending = (async () => {
    const now = Math.floor(Date.now() / 1000);
    const header = base64url(encoder.encode(JSON.stringify({alg: "RS256", typ: "JWT", kid: account.private_key_id})));
    const claims = base64url(encoder.encode(JSON.stringify({iss: account.client_email,
      scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600})));
    const pem = account.private_key.replace(/-----[^-]+-----/g, "").replace(/\s/g, "");
    const key = await crypto.subtle.importKey("pkcs8", decode64(pem), {name: "RSASSA-PKCS1-v1_5", hash: "SHA-256"}, false, ["sign"]);
    const signed = header + "." + claims;
    const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(signed));
    const response = await httpFetch("https://oauth2.googleapis.com/token", {method: "POST", body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: signed + "." + base64url(signature)})});
    const result = await response.json();
    if (!response.ok || !result.access_token) throw new ApiError(503, "server-credentials-invalid");
    oauthCaches.set(cacheKey, {token: result.access_token, until: Date.now() + Math.min(result.expires_in || 3600, 3600) * 1000 - 120000});
    return result.access_token;
  })();
  oauthCaches.set(cacheKey, {pending});
  try { return await pending; } catch (error) { oauthCaches.delete(cacheKey); throw error; }
}
