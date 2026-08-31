import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const framesDir = '/tmp/frames';
const frames = fs.readdirSync(framesDir).filter(f => f.endsWith('.jpg')).sort();
const content = [{
  type: 'text',
  text: `These are ${frames.length} sequential frames (2 seconds apart, order: f_01 oldest -> f_${frames[frames.length-1]} newest) from a 24s screen recording of an Android terminal app "PocketShell".
User report: typing on the built-in in-app keyboard does NOT update the terminal view; when the keyboard is toggled OFF, previously typed input suddenly becomes visible. Enter requires: toggle keyboard on -> press enter -> toggle off.

Analyze the sequence and answer precisely:
1) Describe the UI: terminal area, in-app keyboard row(s), toggle button, any extra keys (CTRL/ALT/ESC/arrows).
2) For each frame in order: what text is visible in the terminal, is the keyboard visible or hidden, any change vs previous frame.
3) Identify the exact transition frames where keyboard appears/disappears, and whether terminal text changed only at those moments.
4) Does the keyboard overlap/cover the terminal? Is the terminal area resized when keyboard shows, or does the keyboard overlay it?
5) Cursor movement visible? Any frozen/stale view evidence?
6) Your best technical diagnosis of what is wrong.`
}];
for (const f of frames) {
  const b64 = fs.readFileSync(`${framesDir}/${f}`).toString('base64');
  content.push({ type: 'image_url', image_url: { url: `data:image/jpeg;base64,${b64}` } });
}

const zai = await ZAI.create();
const resp = await zai.chat.completions.createVision({
  messages: [{ role: 'user', content }],
  thinking: { type: 'enabled' }
});
console.log(resp.choices[0]?.message?.content);
