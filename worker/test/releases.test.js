import {test} from "node:test";
import assert from "node:assert/strict";
import {releaseOffer, verifyReleaseSignature, receiveRelease, queueRelease, drainRelease, getAppUpdate, sendReleasePush} from "../src/releases.js";

function payload(code = 220000001) { return {action: "published", repository: {full_name: "robraym/falai"}, release: {
  tag_name: `v1.5-apk.${code}`, draft: false, prerelease: false,
  assets: [{name: "falai.apk", state: "uploaded", size: 100000, digest: "sha256:" + "a".repeat(64)}]}}; }
function database() {
  const records = new Map(); const read = async path => records.has(path) ? {data: structuredClone(records.get(path))} : null;
  return {records, get: read, transaction: async action => {
    const writes = []; const result = await action({get: read, set: (path, data) => writes.push([path, data])});
    for (const [path, data] of writes) records.set(path, data); return result;
  }};
}
test("accepts only a published stable Falai APK with a digest and bounded size", () => {
  assert.equal(releaseOffer(payload()).versionCode, 220000001);
  for (const mutate of [p => p.repository.full_name = "other/repo", p => p.action = "edited", p => p.release.draft = true,
    p => p.release.prerelease = true, p => p.release.assets[0].size = 209715201, p => p.release.assets[0].digest = "",
    p => p.release.assets[0].name = "other.apk", p => p.release.tag_name = "v1.5-apk.2147483648"]) {
    const value = payload(); mutate(value); assert.equal(releaseOffer(value), null);
  }
});
test("validates the documented GitHub HMAC example and rejects altered payloads", async () => {
  const signature = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";
  await verifyReleaseSignature(new TextEncoder().encode("Hello, World!"), signature, "It's a Secret to Everybody");
  await assert.rejects(verifyReleaseSignature(new TextEncoder().encode("Changed"), signature, "It's a Secret to Everybody"));
  await assert.rejects(verifyReleaseSignature(new Uint8Array(), null, "secret"));
  await assert.rejects(receiveRelease(new Request("https://test", {method: "POST", body: "{}"}), {GITHUB_RELEASE_WEBHOOK_SECRET: "secret"}, database()));
});
test("duplicate or older releases do not notify again and API hides outbox state", async () => {
  const db = database(), offer = releaseOffer(payload()); assert.equal(await queueRelease(db, offer), true);
  assert.equal(await queueRelease(db, offer), false); assert.equal(await queueRelease(db, releaseOffer(payload(220000000))), false);
  assert.deepEqual(await getAppUpdate(db), {offer});
  let count = 0; await drainRelease(db, {}, async () => count++); await drainRelease(db, {}, async () => count++);
  assert.equal(count, 1); assert.equal(db.records.get("appUpdates/latest").status, "sent");
});
test("failed push is retained and a newer publication survives completion of an old job", async () => {
  const db = database(); await queueRelease(db, releaseOffer(payload()));
  await drainRelease(db, {}, async () => { throw new Error("offline"); });
  assert.equal(db.records.get("appUpdates/latest").status, "pending");
  assert.ok(db.records.get("appUpdates/latest").nextAttemptAt > new Date());
  db.records.get("appUpdates/latest").nextAttemptAt = new Date(0);
  await drainRelease(db, {}, async () => { await queueRelease(db, releaseOffer(payload(220000002))); });
  assert.equal(db.records.get("appUpdates/latest").versionCode, 220000002);
  assert.equal(db.records.get("appUpdates/latest").status, "pending");
});
test("topic push contains only public release metadata and supports validate-only", async context => {
  const keys = await crypto.subtle.generateKey({name: "RSASSA-PKCS1-v1_5", modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256"}, true, ["sign", "verify"]);
  const bytes = new Uint8Array(await crypto.subtle.exportKey("pkcs8", keys.privateKey));
  const env = {FIREBASE_PROJECT_ID: "release-test", FIREBASE_SERVICE_ACCOUNT: JSON.stringify({project_id: "release-test",
    client_email: "worker@release-test.iam.gserviceaccount.com", private_key: "-----BEGIN PRIVATE KEY-----\n" + btoa(String.fromCharCode(...bytes)) + "\n-----END PRIVATE KEY-----"})};
  let sent;
  context.mock.method(globalThis, "fetch", async (url, options) => {
    if (String(url).includes("oauth2.googleapis.com")) return Response.json({access_token: "test", expires_in: 3600});
    sent = JSON.parse(options.body); return Response.json({name: "validated"});
  });
  await sendReleasePush(env, releaseOffer(payload()), true);
  assert.equal(sent.validateOnly, true); assert.equal(sent.message.topic, "falai-updates-v1");
  assert.equal(sent.message.data.event, "app-update"); assert.equal(sent.message.data.versionCode, "220000001");
  assert.equal(sent.message.notification, undefined); assert.equal(sent.message.token, undefined);
  assert.equal(sent.message.android.collapse_key, "falai-app-update");
});
