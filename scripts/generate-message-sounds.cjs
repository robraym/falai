// Original short message tones, generated without external recordings or dependencies.
// The ICQ recording remains the separate, user-supplied icq_uh_oh.mp3.
const fs = require('node:fs');
const path = require('node:path');
const rate = 22050;
function wave(name, duration, sample) {
  const count = Math.round(duration * rate), buffer = Buffer.alloc(44 + count * 2);
  buffer.write('RIFF', 0); buffer.writeUInt32LE(buffer.length - 8, 4); buffer.write('WAVEfmt ', 8);
  buffer.writeUInt32LE(16, 16); buffer.writeUInt16LE(1, 20); buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(rate, 24); buffer.writeUInt32LE(rate * 2, 28); buffer.writeUInt16LE(2, 32); buffer.writeUInt16LE(16, 34);
  buffer.write('data', 36); buffer.writeUInt32LE(count * 2, 40);
  let peak = 0;
  for (let i = 0; i < count; i++) {
    const t = i / rate, fade = Math.min(1, t / .004, (duration - t) / .018);
    const value = Math.max(-.9, Math.min(.9, sample(t) * fade)); peak = Math.max(peak, Math.abs(value));
    buffer.writeInt16LE(Math.round(value * 32767), 44 + i * 2);
  }
  fs.writeFileSync(path.join(__dirname, '../app/src/main/res/raw/', name + '.wav'), buffer);
  console.log(`${name}: ${count} samples, ${(buffer.length / 1024).toFixed(1)} KiB, peak ${peak.toFixed(3)}`);
}
const sine = x => Math.sin(2 * Math.PI * x);
wave('message_pop', .28, t => .68 * Math.exp(-t * 22) * (sine(950 * t - 600 * t * t) + .16 * sine(1900 * t)));
wave('message_double', .50, t => {
  const note = (start, hz) => t < start ? 0 : .44 * Math.exp(-(t - start) * 22) * Math.min(1, (t - start) / .004) * sine(hz * (t - start));
  return note(0, 1050) + note(.15, 1400);
});
wave('message_drop', .35, t => .62 * Math.exp(-t * 19) * sine(1400 * t - 1100 * t * t));
function note(t, start, hz, decay, gain = .4) {
  if (t < start) return 0;
  const age = t - start;
  return gain * Math.min(1, age / .004) * Math.exp(-age * decay) * sine(hz * age);
}
wave('message_crystal', .42, t => note(t, 0, 1760, 13, .42) + note(t, 0, 3520, 24, .13));
wave('message_bell', .60, t => note(t, 0, 880, 8, .38) + note(t, 0, 2387, 14, .16) + note(t, 0, 3960, 22, .07));
wave('message_click', .14, t => .55 * Math.exp(-t * 48) * (sine(720 * t) + .22 * sine(2160 * t)));
wave('message_bubble', .26, t => .58 * Math.exp(-t * 20) * sine(430 * t + 1750 * t * t));
wave('message_digital', .36, t => note(t, 0, 1320, 38, .42) + note(t, .11, 1320, 38, .42));
wave('message_rise', .46, t => note(t, 0, 784, 28) + note(t, .10, 1046.5, 28) + note(t, .20, 1318.5, 22));
wave('message_fall', .46, t => note(t, 0, 1318.5, 28) + note(t, .10, 1046.5, 28) + note(t, .20, 784, 22));
