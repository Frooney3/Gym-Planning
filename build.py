#!/usr/bin/env python3
"""Build the installable phone app (PWA) at the repo root from src/spotter.html.

src/spotter.html is the single source of the app (it is also what's published as the
claude.ai artifact). This script wraps it in a full HTML document and adds the
web app manifest, home-screen icons and an offline service worker, so GitHub
Pages (deploying the repo root) serves an installable app.

    python3 build.py
"""
import hashlib
import json
import re
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).parent
DOCS = ROOT  # output goes to the repo root, which GitHub Pages serves
SRC = (ROOT / "src" / "spotter.html").read_text(encoding="utf-8")

BG_LIGHT, BG_DARK = "#eef1f4", "#0e1217"


def icon(size, logo_scale=0.72):
    """Barbell logo: competition plates on a dark ground (full bleed, no transparency)."""
    s = 4  # supersample
    W = size * s
    img = Image.new("RGB", (W, W), "#141a22")
    d = ImageDraw.Draw(img)
    u = W * logo_scale / 28  # logo drawn on a 28-unit grid, centred
    ox = oy = (W - 28 * u) / 2

    def rect(x, y, w, h, fill, r=1.0):
        d.rounded_rectangle([ox + x * u, oy + y * u, ox + (x + w) * u, oy + (y + h) * u], radius=r * u, fill=fill)

    rect(9, 12.5, 10, 3, "#93a0b0", 0.3)   # bar
    rect(0, 7, 4, 14, "#e5574a")           # red 25s
    rect(24, 7, 4, 14, "#e5574a")
    rect(5, 4, 4, 20, "#5b8cff")           # blue 20s
    rect(19, 4, 4, 20, "#5b8cff")
    return img.resize((size, size), Image.LANCZOS)


def main():
    (DOCS / "icons").mkdir(parents=True, exist_ok=True)
    icon(192).save(DOCS / "icons/icon-192.png")
    icon(512).save(DOCS / "icons/icon-512.png")
    icon(512, logo_scale=0.56).save(DOCS / "icons/maskable-512.png")
    icon(180, logo_scale=0.66).save(DOCS / "icons/apple-touch-icon.png")
    # Android app launcher icons
    for density, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        out = ROOT / "android/app/src/main/res" / f"mipmap-{density}"
        out.mkdir(parents=True, exist_ok=True)
        icon(px, logo_scale=0.66).save(out / "ic_launcher.png")

    manifest = {
        "name": "Spotter",
        "short_name": "Spotter",
        "description": "Your AI personal trainer: logs every set, progresses your weights and plans your training.",
        "id": "./",
        "start_url": "./",
        "scope": "./",
        "display": "standalone",
        "orientation": "portrait",
        "background_color": BG_LIGHT,
        "theme_color": BG_LIGHT,
        "icons": [
            {"src": "icons/icon-192.png", "sizes": "192x192", "type": "image/png"},
            {"src": "icons/icon-512.png", "sizes": "512x512", "type": "image/png"},
            {"src": "icons/maskable-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable"},
        ],
    }
    (DOCS / "manifest.webmanifest").write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    # Everything before the first <header> (title, font links, style) belongs in <head>.
    split = SRC.index("<header")
    head_extra, body = SRC[:split].strip(), SRC[split:].strip()
    head_extra = re.sub(r'<meta name="claude:[^"]+"[^>]*>\s*', "", head_extra)
    page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="{BG_LIGHT}" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="{BG_DARK}" media="(prefers-color-scheme: dark)">
<meta name="description" content="{manifest['description']}">
<link rel="manifest" href="manifest.webmanifest">
<link rel="icon" href="icons/icon-192.png">
<link rel="apple-touch-icon" href="icons/apple-touch-icon.png">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="Spotter">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
<style>:root{{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}}body{{margin:0}}img{{max-width:100%}}[hidden]{{display:none!important}}</style>
{head_extra}
</head>
<body>
{body}
</body>
</html>
"""
    (DOCS / "index.html").write_text(page, encoding="utf-8")

    shell = ["./", "index.html", "manifest.webmanifest", "vendor/anthropic-sdk.js",
             "icons/icon-192.png", "icons/icon-512.png", "icons/apple-touch-icon.png"]
    digest = hashlib.sha256()
    for f in shell[1:]:
        digest.update((DOCS / f).read_bytes())
    version = digest.hexdigest()[:10]
    sw = (ROOT / "sw.template.js").read_text(encoding="utf-8")
    sw = sw.replace("__VERSION__", version).replace("__SHELL__", json.dumps(shell))
    (DOCS / "sw.js").write_text(sw, encoding="utf-8")
    (DOCS / ".nojekyll").write_text("", encoding="utf-8")
    print(f"Built app at repo root (version {version})")


if __name__ == "__main__":
    main()
