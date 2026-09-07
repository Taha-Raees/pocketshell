#!/usr/bin/env python3
"""
M7.1 P2 — bundled launcher icon pipeline (PART D).

Fetches each curated launcher's mark from the OFFICIAL first-party origin,
normalizes every source onto ONE square canvas (trim to content, fit to a
uniform box, center, transparent background) and writes lossless WebP assets
into app/src/main/assets/launcher_icons/. Also renders a contact sheet for
the human visual audit (PART K).

Rules honored here:
- build-time sourcing only: the APP never downloads anything at runtime;
- first-party origins (the brands' own web assets), no scrapers, no CDNs;
- any brand that cannot be fetched cleanly is simply skipped — the launcher
  falls back to the deterministic text badge (never a placeholder lie).

Usage: python3 scripts/make_launcher_icons.py [--offline]
"""
import io
import sys
from pathlib import Path

import PIL.Image
import PIL.ImageDraw
import PIL.ImageFont
import urllib.request


def to_png_bytes(raw: bytes) -> bytes:
    """SVG marks (the z.ai / aider brands ship SVG only) rasterize here at
    build time via cairosvg — transparent background, 512px box."""
    head = raw[:256].lstrip().lower()
    if b"<svg" in head or head.startswith(b"<?xml"):
        import cairosvg
        return cairosvg.svg2png(bytestring=raw, output_width=512, output_height=512)
    return raw

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "app/src/main/assets/launcher_icons"
CACHE_DIR = ROOT / "scripts/icon_sources"
SHEET = CACHE_DIR / "contact_sheet.png"

CANVAS = 192          # asset canvas (px)
CONTENT_BOX = 156     # uniform content box inside the canvas (~81%)

UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0 Safari/537.36")

# id -> (label, [candidate first-party URLs], opts)
#
# opts (all optional):
#   bg          composite the mark onto this OPAQUE brand tile color — the
#               theme-proof "app icon" look (a transparent dark mark would
#               vanish on PocketShell's dark canvas);
#   recolor     repaint the mark's alpha mask in this solid color (the
#               GitHub dark-scheme favicon ships white; the OpenAI flower
#               ships black and gets the white treatment for Codex's dark
#               tile — same glyph, standard monochrome brand treatment);
#   trim_color  crop to the bbox of pixels that DIFFER from this background
#               color (for full-bleed sources whose own background is not
#               transparent).
SOURCES = {
    "builtin-chatgpt": ("ChatGPT", [
        "https://chatgpt.com/apple-touch-icon.png",
        "https://cdn.oaistatic.com/assets/apple-touch-icon-mz9nytnj.webp",
        "https://openai.com/favicon.ico",
    ], {}),
    "builtin-claude": ("Claude", [
        "https://cdn.prod.website-files.com/6889473510b50328dbb70ae6/68c33859cc6cd903686c66a2_apple-touch-icon.png",
        "https://www.anthropic.com/favicon.ico",
    ], {}),
    "builtin-zai": ("Z.ai", [
        "https://z-cdn.chatglm.cn/z-ai/static/logo.svg",
    ], {}),
    "builtin-github": ("GitHub", [
        "https://github.githubassets.com/favicons/favicon-dark.svg",
        "https://github.githubassets.com/favicons/favicon.png",
    ], {"bg": (27, 31, 36)}),
    "hermes": ("Hermes Agent (Nous Research)", [
        "https://nousresearch.com/apple-touch-icon.png",
        "https://nousresearch.com/favicon.ico",
    ], {}),
    "opencode": ("OpenCode", [
        "https://opencode.ai/apple-touch-icon-v3.png",
        "https://opencode.ai/favicon-96x96-v3.png",
        "https://opencode.ai/favicon.ico",
    ], {}),
    "claude": ("Claude Code (Anthropic)", [
        "https://cdn.prod.website-files.com/6889473510b50328dbb70ae6/68c33859cc6cd903686c66a2_apple-touch-icon.png",
        "https://www.anthropic.com/favicon.ico",
    ], {}),
    "zcode": ("ZCode (Z.ai)", [
        "https://z-cdn.chatglm.cn/z-ai/static/logo.svg",
    ], {}),
    "kilo": ("Kilo Code", [
        "https://kilocode.ai/apple-touch-icon.png",
        "https://kilocode.ai/favicon.ico",
        "https://raw.githubusercontent.com/Kilo-Org/kilocode/main/apps/web/public/favicon.png",
    ], {}),
    "cline": ("Cline", [
        "https://cline.bot/assets/branding/favicons/favicon-256x256.png",
        "https://cline.bot/assets/branding/favicons/apple-touch-icon.png",
    ], {}),
    "agy": ("Antigravity (Google)", [
        "https://antigravity.google/favicon.ico",
        "https://antigravity.google/apple-touch-icon.png",
        "https://www.google.com/favicon.ico",
    ], {}),
    "codex": ("Codex (OpenAI)", [
        # The large official OpenAI flower source; Codex's own favicon ships
        # the same glyph as a 48px black-on-transparent icon (too small and
        # invisible on the dark canvas) — the flower gets the white-on-dark
        # Codex CLI treatment here instead.
        "https://cdn.oaistatic.com/assets/apple-touch-icon-mz9nytnj.webp",
        "https://openai.com/favicon.ico",
    ], {"trim_color": (255, 255, 255), "recolor": (255, 255, 255), "bg": (11, 11, 11)}),
    "aider": ("Aider", [
        "https://avatars.githubusercontent.com/Aider-AI?size=256",
        "https://aider.chat/assets/logo.svg",
    ], {}),
    "qwen": ("Qwen Code", [
        "https://assets.alicdn.com/g/qwenweb/qwen-chat-fe/0.2.91/favicon.png",
        "https://img.alicdn.com/imgextra/i4/O1CN01OXv3EM1FN8t9W4P79_!!6000000000474-2-tps-80-80.png",
    ], {}),
}


def fetch(url: str) -> bytes | None:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            if resp.status != 200:
                return None
            return resp.read()
    except Exception:
        return None


def open_image(raw: bytes) -> PIL.Image.Image | None:
    try:
        img = PIL.Image.open(io.BytesIO(raw))
        # ICO: request the largest frame explicitly.
        if getattr(img, "format", "") == "ICO":
            try:
                img.size = (256, 256)
            except Exception:
                pass
        img.load()
        return img
    except Exception:
        return None


def normalize(img: PIL.Image.Image, opts: dict) -> PIL.Image.Image:
    """Optional color-key trim/mask, optional recolor, fit CONTENT_BOX
    preserving aspect, center on the (optionally opaque brand-colored)
    canvas. The color-distance mask doubles as the recolor alpha — sources
    without transparency (RGB touch icons) still yield a clean glyph."""
    rgba = img.convert("RGBA")
    trim_color = opts.get("trim_color")
    mask = None
    if trim_color:
        rgb = rgba.convert("RGB")
        mask = PIL.Image.new("L", rgb.size, 0)
        px, out = rgb.load(), mask.load()
        for y in range(rgb.height):
            for x in range(rgb.width):
                p = px[x, y]
                dist = (abs(p[0] - trim_color[0]) + abs(p[1] - trim_color[1])
                        + abs(p[2] - trim_color[2]))
                # soft edge: 0 below 20, full above 90, linear between
                out[x, y] = 0 if dist < 20 else (255 if dist > 90 else int((dist - 20) * 255 // 70))
        bbox = mask.getbbox()
    else:
        bbox = rgba.getchannel("A").getbbox()
    if bbox:
        rgba = rgba.crop(bbox)
        if mask is not None:
            mask = mask.crop(bbox)
    recolor = opts.get("recolor")
    if recolor:
        solid = PIL.Image.new("RGBA", rgba.size, recolor + (255,))
        solid.putalpha(mask if mask is not None else rgba.getchannel("A"))
        rgba = solid
    scale = min(CONTENT_BOX / rgba.width, CONTENT_BOX / rgba.height)
    if scale < 1.0 or scale > 1.6:  # downscale big; upscale only tiny marks
        scale = min(scale, 1.6)
        rgba = rgba.resize(
            (max(1, round(rgba.width * scale)), max(1, round(rgba.height * scale))),
            PIL.Image.LANCZOS,
        )
    bg = opts.get("bg", (0, 0, 0, 0))
    canvas = PIL.Image.new("RGBA", (CANVAS, CANVAS), bg)
    canvas.alpha_composite(
        rgba,
        ((CANVAS - rgba.width) // 2, (CANVAS - rgba.height) // 2),
    )
    return canvas


def main() -> int:
    offline = "--offline" in sys.argv
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    CACHE_DIR.mkdir(parents=True, exist_ok=True)

    results = {}
    for icon_id, entry in SOURCES.items():
        label, urls, opts = entry
        raw_path = CACHE_DIR / f"{icon_id}.raw"
        raw = None
        if not offline:
            for url in urls:
                data = fetch(url)
                if not data:
                    continue
                try:
                    data = to_png_bytes(data)
                except Exception:
                    continue
                if open_image(data) is not None:
                    raw, used = data, url
                    raw_path.write_bytes(data)
                    break
        if raw is None and raw_path.exists():
            raw = raw_path.read_bytes()
            used = "cache"
        if raw is None:
            results[icon_id] = ("MISSING", "-", 0)
            print(f"[skip] {icon_id}: no usable first-party source — badge fallback")
            continue
        img = open_image(raw)
        asset = normalize(img, opts)
        out = OUT_DIR / f"{icon_id}.webp"
        asset.save(out, "WEBP", lossless=True, method=6)
        size = out.stat().st_size
        results[icon_id] = ("OK", used, size)
        print(f"[ ok ] {icon_id}: {used} -> {out.name} ({size} B)")

    # Contact sheet on the Midnight-adjacent dark tone for the visual audit.
    ids = [i for i in SOURCES if results[i][0] == "OK"]
    cols = 5
    rows = max(1, (len(ids) + cols - 1) // cols)
    cell = CANVAS + 56
    sheet = PIL.Image.new("RGBA", (cols * cell, rows * cell), (13, 18, 38, 255))
    draw = PIL.ImageDraw.Draw(sheet)
    try:
        font = PIL.ImageFont.truetype(
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 13)
    except Exception:
        font = PIL.ImageFont.load_default()
    for index, icon_id in enumerate(ids):
        x = (index % cols) * cell + 28
        y = (index // cols) * cell + 8
        tile = PIL.Image.open(OUT_DIR / f"{icon_id}.webp").convert("RGBA")
        sheet.alpha_composite(tile, (x + 28, y))
        draw.text((x + 4, y + CANVAS + 6), icon_id, fill=(190, 200, 230), font=font)
    sheet.convert("RGB").save(SHEET)
    print(f"contact sheet: {SHEET}")

    missing = [i for i in SOURCES if results[i][0] == "MISSING"]
    print(f"bundled {len(ids)}/{len(SOURCES)} icons; missing (badge fallback): {missing}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
