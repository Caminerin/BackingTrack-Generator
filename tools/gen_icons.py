#!/usr/bin/env python3
"""Regenerate legacy PNG launcher icons (API < 26) with the EQ design.
Adaptive icons (API 26+) use the vector drawables; these PNGs are the fallback."""
import os
from PIL import Image, ImageDraw

BG = (15, 13, 10, 255)        # #0F0D0A
AMBER = (212, 150, 10, 255)   # #D4960A
ORANGE = (230, 126, 0, 255)   # #E67E00
GREEN = (139, 195, 74, 255)   # #8BC34A

# (x_center, top_y) in 108-viewport units; baseline 74; bar half-width 4
BARS = [(34, 60, AMBER), (45, 44, ORANGE), (56, 34, GREEN), (67, 48, ORANGE), (78, 56, AMBER)]
BASE = 74.0

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def draw_icon(size, round_icon):
    scale = size / 108.0
    ss = 4  # supersample
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if round_icon:
        d.ellipse([0, 0, S - 1, S - 1], fill=BG)
    else:
        r = int(18 * scale * ss)
        d.rounded_rectangle([0, 0, S - 1, S - 1], radius=r, fill=BG)
    hw = 4 * scale * ss
    base = BASE * scale * ss
    for cx, top, color in BARS:
        x = cx * scale * ss
        y = top * scale * ss
        d.rounded_rectangle([x - hw, y, x + hw, base], radius=hw, fill=color)
    return img.resize((size, size), Image.LANCZOS)


for dens, px in SIZES.items():
    out_dir = os.path.join(ROOT, f"mipmap-{dens}")
    os.makedirs(out_dir, exist_ok=True)
    draw_icon(px, False).save(os.path.join(out_dir, "ic_launcher.png"))
    draw_icon(px, True).save(os.path.join(out_dir, "ic_launcher_round.png"))
    print("wrote", dens, px)
print("done")
