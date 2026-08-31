import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const framesDir = '/tmp/m22_frames';
const frames = fs.readdirSync(framesDir).filter(f => f.endsWith('.png')).sort();
const content = [{
  type: 'text',
  text: `These are ${frames.length} sequential frames (~0.33s apart, oldest first) from a 9.2s screen recording of the Android app "PocketShell" (a terminal app). Context: the user was exercising the M2.2 "Install Linux environment" flow in the Diagnostics screen (states: NOT_INSTALLED -> DOWNLOADING -> VERIFYING -> EXTRACTING -> CONFIGURING -> READY, with a "Runtime size", "Rootfs files", "Free space", "Last event" row). The user's question: a "Runtime size" of 9.3 MB was displayed — is that correct?

Analyze precisely:
1) Frame by frame (brief): which screen/state is visible, what the Linux runtime rows show (State, Distribution, Runtime size, Rootfs files, Free space, Last event), and what actions happen (taps, progress).
2) Did the install reach READY? Which frames show which states?
3) Transcribe ALL numeric values you can read (sizes, counts, free space) and at which frame.
4) Any anomalies: crash/launcher, stuck spinner, error text, state going backwards, impossible numbers?
5) Does the user open the terminal app itself or the Home screen at any point?`
}];
for (const f of frames) {
  const b64 = fs.readFileSync(`${framesDir}/${f}`).toString('base64');
  content.push({ type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } });
}

const zai = await ZAI.create();
const resp = await zai.chat.completions.createVision({
  messages: [{ role: 'user', content }],
  thinking: { type: 'enabled' }
});
console.log(resp.choices[0]?.message?.content);
