import {requireValue} from "./validation.js";

// A small JPEG avatar fits Firestore Spark and the bounded API body, without Storage.
export function validatePhoto(value) {
  requireValue(typeof value === "string" && value.length <= 22000 && value.length % 4 === 0 && /^[A-Za-z0-9+/]+={0,2}$/.test(value));
  let bytes;
  try { bytes = Uint8Array.from(atob(value), c => c.charCodeAt(0)); } catch { requireValue(false); }
  requireValue(bytes.length <= 16384 && bytes.length >= 20 && bytes[0] === 255 && bytes[1] === 216
    && bytes.at(-2) === 255 && bytes.at(-1) === 217);
  let offset = 2, dimensions = false;
  while (offset + 4 < bytes.length) {
    requireValue(bytes[offset++] === 255);
    while (bytes[offset] === 255) offset++;
    const marker = bytes[offset++];
    if (marker === 218 || marker === 217) break;
    const length = (bytes[offset] << 8) | bytes[offset + 1];
    requireValue(length >= 2 && offset + length <= bytes.length);
    if ([192, 193, 194].includes(marker)) {
      requireValue(length >= 8);
      const height = (bytes[offset + 3] << 8) | bytes[offset + 4];
      const width = (bytes[offset + 5] << 8) | bytes[offset + 6];
      requireValue(width > 0 && height > 0 && width <= 256 && height <= 256); dimensions = true;
    }
    offset += length;
  }
  requireValue(dimensions); return value;
}
