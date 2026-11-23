import {test, before} from "node:test";
import assert from "node:assert/strict";
import {base64url, verifyUser} from "../src/google.js";
import worker from "../src/index.js";
let keys, jwk;
before(async () => {
  keys = await crypto.subtle.generateKey({name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256"}, true, ["sign", "verify"]);
  jwk = {...await crypto.subtle.exportKey("jwk", keys.publicKey), kid: "test-key", alg: "RS256", use: "sig"};
});
async function token(extra = {}) {
  const now = Math.floor(Date.now() / 1000);
  const payload = {aud: "falai-test", iss: "https://securetoken.google.com/falai-test", sub: "alice", email: "alice@example.com", email_verified: true,
    firebase: {sign_in_provider: "google.com"}, iat: now, auth_time: now, exp: now + 3600, ...extra};
  const head = base64url(new TextEncoder().encode(JSON.stringify({alg: "RS256", kid: "test-key"})));
  const body = base64url(new TextEncoder().encode(JSON.stringify(payload)));
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", keys.privateKey, new TextEncoder().encode(head + "." + body));
  return head + "." + body + "." + base64url(signature);
}
test("JWT signature is checked and claims cannot be changed by the caller", async context => {
  context.mock.method(globalThis, "fetch", async () => Response.json({keys: [jwk]}));
  const signed = await token(); assert.equal((await verifyUser(signed, "falai-test")).uid, "alice");
  const parts = signed.split("."); parts[1] = base64url(new TextEncoder().encode(JSON.stringify({sub: "mallory"})));
  await assert.rejects(verifyUser(parts.join("."), "falai-test"), {status: 401});
  await assert.rejects(verifyUser(await token({aud: "other-project"}), "falai-test"), {status: 401});
});
test("Worker rejects unauthenticated, unknown and inherited method names before accessing the database", async () => {
  for (const action of ["sendMessage", "registerProfile", "discoverContacts", "registerDevice", "acknowledgeMessages", "updateProfilePhoto", "updateProfileAbout", "editMessage", "deleteMessage", "deleteConversation"]) {
    const response = await worker.fetch(new Request("https://worker/v1/" + action, {method: "POST", body: "{}"}), {FIREBASE_PROJECT_ID: "falai-test"}, {});
    assert.equal(response.status, 401);
  }
  for (const action of ["__proto__", "constructor", "unknown"]) {
    const response = await worker.fetch(new Request("https://worker/v1/" + action, {method: "POST", body: "{}"}), {}, {});
    assert.equal(response.status, 404);
  }
});
