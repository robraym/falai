import {test} from "node:test";
import assert from "node:assert/strict";
import {sendPush, notificationPreview} from "../src/push.js";

test("notification preview preserves normal text and bounds Unicode payload size", () => {
  assert.equal(notificationPreview("Olá 😀"), "Olá 😀");
  const preview = notificationPreview("😀".repeat(2000));
  assert.ok(new TextEncoder().encode(preview).length <= 1203);
  assert.ok(preview.endsWith("…"));
  assert.ok(!preview.includes("\uFFFD"));
});

test("message push wakes the Android sound handler without an automatic system notification", async context => {
  const keys = await crypto.subtle.generateKey({name: "RSASSA-PKCS1-v1_5", modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256"}, true, ["sign", "verify"]);
  const bytes = new Uint8Array(await crypto.subtle.exportKey("pkcs8", keys.privateKey));
  const privateKey = "-----BEGIN PRIVATE KEY-----\n" + btoa(String.fromCharCode(...bytes)) + "\n-----END PRIVATE KEY-----";
  const env = {FIREBASE_PROJECT_ID: "sound-test", FIREBASE_SERVICE_ACCOUNT: JSON.stringify({
    project_id: "sound-test", client_email: "worker@sound-test.iam.gserviceaccount.com", private_key: privateKey})};
  let payload;
  context.mock.method(globalThis, "fetch", async (url, options) => {
    if (String(url).includes("oauth2.googleapis.com")) return Response.json({access_token: "test-token", expires_in: 3600});
    payload = JSON.parse(options.body);
    return Response.json({name: "projects/sound-test/messages/test"});
  });
  const job = {chatId: "a".repeat(64), messageId: crypto.randomUUID(), recipientId: "bob"};
  assert.equal(await sendPush(env, "device-token", job), "sent");
  assert.equal(payload.validateOnly, false);
  assert.deepEqual(payload.message.data, {...job, senderName: "Falai", text: "", event: "message"});
  assert.equal(payload.message.android.priority, "high");
  assert.equal(payload.message.android.ttl, "2419200s");
  assert.equal(payload.message.notification, undefined);
  assert.equal(payload.message.android.notification, undefined);
  assert.equal(payload.message.token, "device-token");
});
