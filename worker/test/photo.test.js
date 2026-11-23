import {test} from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {validatePhoto} from "../src/photo.js";

const jpeg = readFileSync(new URL("./fixtures/avatar.jpg", import.meta.url));
test("profile photos accept only bounded JPEG thumbnails", () => {
  const encoded = jpeg.toString("base64"); assert.equal(validatePhoto(encoded), encoded);
  for (const invalid of [null, "", "not-an-image", "A".repeat(23000), Buffer.from("<script>alert(1)</script>").toString("base64"), jpeg.subarray(0, jpeg.length - 2).toString("base64")])
    assert.throws(() => validatePhoto(invalid), {code: "invalid-argument"});
  const oversized = Buffer.from(jpeg);
  const sof = oversized.indexOf(Buffer.from([255, 192])); assert.ok(sof > 0);
  oversized[sof + 5] = 4; oversized[sof + 6] = 0; // 1024px height: reject decode bombs.
  assert.throws(() => validatePhoto(oversized.toString("base64")), {code: "invalid-argument"});
});
