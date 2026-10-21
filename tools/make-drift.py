#!/usr/bin/env python3
"""Makes "Drift": the Dusk colour field with each colour patch slowly circling, as a seamless loop.

Usage: tools/make-drift.py <out.mp4>
Renders small frames at a low frame rate (the field is smooth and slow, so nothing is lost),
then ffmpeg blends them to 30 fps, scales to 1080p, adds a little grain against banding,
and encodes HEVC.
"""
import math, subprocess, sys

W, H = 160, 90
FPS = 12
SECONDS = 60
BRIGHTNESS = 0.5
BASE = (30, 63, 191)
# (x, y, radius, orbit, phase) as fractions of the screen, then RGB. Orbits are whole turns per loop.
BLOBS = [
    (0.12, 0.08, 0.30, 0.10, 0.0, (60, 200, 150)),
    (0.52, 0.02, 0.26, 0.08, 1.3, (245, 160, 60)),
    (0.90, 0.18, 0.28, 0.09, 2.1, (70, 205, 170)),
    (0.06, 0.62, 0.26, 0.07, 3.4, (170, 70, 210)),
    (0.48, 0.55, 0.20, 0.12, 4.0, (225, 50, 70)),
    (0.28, 0.45, 0.30, 0.09, 5.2, (40, 90, 230)),
    (0.93, 0.78, 0.26, 0.08, 0.7, (245, 170, 50)),
    (0.62, 0.95, 0.32, 0.10, 2.8, (40, 130, 240)),
]

out = sys.argv[1]
ffmpeg = subprocess.Popen([
    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
    "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
    "-vf", "framerate=fps=30,scale=1920:1080:flags=bicubic,noise=alls=3:allf=t,format=yuv420p",
    "-c:v", "hevc_videotoolbox", "-profile:v", "main", "-b:v", "3M", "-g", "120",
    "-tag:v", "hvc1", "-an", "-movflags", "+faststart", out,
], stdin=subprocess.PIPE)

aspect = H / W
frames = FPS * SECONDS
for f in range(frames):
    turn = 2 * math.pi * f / frames
    blobs = []
    for bx, by, rad, orbit, phase, color in BLOBS:
        cx = bx + orbit * math.cos(turn + phase)
        cy = (by + orbit * math.sin(turn * (1 if phase < 3 else -1) + phase)) * aspect
        blobs.append((cx, cy, 2 * (rad * 0.38) ** 2, color))
    frame = bytearray()
    for py in range(H):
        y = py / H * aspect
        for px in range(W):
            x = px / W
            total, r, g, b = 0.02, BASE[0] * 0.02, BASE[1] * 0.02, BASE[2] * 0.02
            for cx, cy, spread, (cr, cg, cb) in blobs:
                w = math.exp(-((x - cx) ** 2 + (y - cy) ** 2) / spread)
                total += w
                r, g, b = r + cr * w, g + cg * w, b + cb * w
            frame += bytes(min(255, int(c / total * BRIGHTNESS)) for c in (r, g, b))
    ffmpeg.stdin.write(frame)
ffmpeg.stdin.close()
sys.exit(ffmpeg.wait())
