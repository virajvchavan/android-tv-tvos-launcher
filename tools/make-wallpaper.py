#!/usr/bin/env python3
"""Makes a soft colour-blend wallpaper in the style of the tvOS home screen.

Usage: tools/make-wallpaper.py <out.jpg> [brightness]   (default 0.9; 0.45 gives the darker "Dusk")
Renders at 480x270 (the blend is smooth, so nothing is lost) and lets macOS `sips`
scale it to 1920x1080 and write the JPEG.
"""
import math, os, subprocess, sys, tempfile

W, H = 480, 270
BASE = (30, 63, 191)
# (x, y, radius) as fractions of the screen, then RGB.
BLOBS = [
    (0.12, 0.08, 0.30, (60, 200, 150)),
    (0.52, 0.02, 0.26, (245, 160, 60)),
    (0.90, 0.18, 0.28, (70, 205, 170)),
    (0.06, 0.62, 0.26, (170, 70, 210)),
    (0.48, 0.55, 0.20, (225, 50, 70)),
    (0.28, 0.45, 0.30, (40, 90, 230)),
    (0.93, 0.78, 0.26, (245, 170, 50)),
    (0.62, 0.95, 0.32, (40, 130, 240)),
]

out = sys.argv[1]
DARKEN = float(sys.argv[2]) if len(sys.argv) > 2 else 0.9
pixels = bytearray()
for py in range(H):
    for px in range(W):
        x, y = px / W, py / H * (H / W)
        total, r, g, b = 0.02, BASE[0] * 0.02, BASE[1] * 0.02, BASE[2] * 0.02
        for bx, by, rad, (cr, cg, cb) in BLOBS:
            d2 = (x - bx) ** 2 + (y - by * (H / W)) ** 2
            w = math.exp(-d2 / (2 * (rad * 0.38) ** 2))
            total += w
            r, g, b = r + cr * w, g + cg * w, b + cb * w
        pixels += bytes(min(255, int(c / total * DARKEN)) for c in (r, g, b))

with tempfile.TemporaryDirectory() as tmp:
    ppm = os.path.join(tmp, "wall.ppm")
    with open(ppm, "wb") as f:
        f.write(b"P6 %d %d 255\n" % (W, H) + bytes(pixels))
    subprocess.run(["sips", "-s", "format", "jpeg", "-s", "formatOptions", "90",
                    "-z", "1080", "1920", ppm, "--out", out], check=True, capture_output=True)
print(out)
