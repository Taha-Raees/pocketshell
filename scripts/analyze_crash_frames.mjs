import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const framesDir = '/tmp/crash_frames';
const frames = fs.readdirSync(framesDir).filter(f => f.endsWith('.png')).sort();
const content = [{
  type: 'text',
  text: `These are ${frames.length} sequential frames (0.25 seconds apart, order: ${frames[0]} oldest -> ${frames[frames.length-1]} newest) from a 4.7s screen recording of an Android app "PocketShell" (a terminal app) on a Samsung device. The user reports the app CLOSES unexpectedly (crash or exit to launcher / "One UI Home").

Analyze the sequence precisely:
1) For each frame in order (brief): which screen is visible (app home screen / diagnostics / settings / terminal / device launcher), what UI elements and text are visible, any dialog or toast.
2) What is the user's last action INSIDE the app before it disappears? (which button/row do the frames show being tapped, if discernible)
3) At which frame does the app UI disappear / launcher appear? Was there any error dialog, spinner, progress bar, or visual glitch right before?
4) Was the app mid-something (e.g. install progress, download bar) at the moment it closed?
5) Best diagnosis: does this look like a silent crash (instant launcher, no dialog) or a graceful exit?`
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
