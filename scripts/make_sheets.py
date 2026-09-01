#!/usr/bin/env python3
"""Build 3x2 contact sheets from unique video frames using ffmpeg."""
import subprocess, os, sys

SRC = "/home/z/my-project/vframes/uniq"
OUT = "/home/z/my-project/vframes/sheets"
os.makedirs(OUT, exist_ok=True)
files = sorted(f for f in os.listdir(SRC) if f.endswith(".png"))
TILE_W, TILE_H, COLS, ROWS = 320, 782, 3, 2
per = COLS * ROWS

for s in range(0, len(files), per):
    batch = files[s:s + per]
    idx = len(os.listdir(OUT)) + 1
    inputs, parts, labels = [], [], []
    for i, f in enumerate(batch):
        inputs += ["-i", os.path.join(SRC, f)]
        parts.append(
            f"[{i}:v]scale={TILE_W}:{TILE_H},drawtext=text='{f}':x=6:y=6:"
            f"fontsize=22:fontcolor=yellow:box=1:boxcolor=black@0.6[v{i}]"
        )
    rows = []
    for r in range(ROWS):
        row_items = [f"[v{r * COLS + c}]FW1" for c in range(COLS) if r * COLS + c < len(batch)]
        if row_items:
            rows.append("".join(row_items))
    # hstack rows then vstack; pad missing slots with black
    n = len(batch)
    # build explicit layout: always produce full grid using black sources for missing
    total = COLS * ROWS
    # Use xstack-free approach: hstack per row with pad, then vstack
    filt = ";".join(parts)
    vstack_inputs = []
    cur = 0
    for r in range(ROWS):
        row_items = [r * COLS + c for c in range(COLS) if r * COLS + c < n]
        if not row_items:
            continue
        chain = "".join(f"[v{i}]" for i in row_items)
        if len(row_items) == COLS:
            filt += f";{chain}hstack=3[row{r}]"
        elif len(row_items) == 1:
            filt += f";[v{row_items[0]}]null[row{r}]"
        else:
            filt += f";{chain}hstack=inputs={len(row_items)}[rowp{r}];color=black:s={TILE_W * (COLS - len(row_items))}x{TILE_H}[pad{r}];[rowp{r}][pad{r}]hstack=inputs=2[row{r}]"
        vstack_inputs.append(f"[row{r}]")
    if len(vstack_inputs) == 1:
        filt += f";{vstack_inputs[0]}null[out]"
    else:
        filt += ";" + "".join(vstack_inputs) + f"vstack=inputs={len(vstack_inputs)}[out]"
    out_path = os.path.join(OUT, f"sheet_{(s // per) + 1:02d}.png")
    cmd = ["ffmpeg", "-v", "error"] + inputs + ["-filter_complex", filt, "-map", "[out]", out_path]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(f"sheet {(s // per) + 1}: FAIL {r.stderr[:400]}")
        sys.exit(1)
print(f"OK: {len(os.listdir(OUT))} sheets for {len(files)} frames")
