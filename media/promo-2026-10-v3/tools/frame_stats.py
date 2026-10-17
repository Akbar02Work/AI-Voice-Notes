#!/usr/bin/env python3
"""Per-frame statistics of a decoded MP4 (bundled ffmpeg → 192-px grey raw frames).

Reports: blank frames (near-uniform), mean-luminance flashes (frame-to-frame jumps),
the largest consecutive-frame differences (to inspect hard cuts / resets), and the
first/last frame means. Usage: frame_stats.py video.mp4
"""
import os
import subprocess
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
FFDIR = os.environ.get("FFMPEG_DIR", os.path.join(HERE, "..", "remotion", "node_modules", "@remotion", "compositor-darwin-arm64"))
def binary(name):
    bundled = os.path.join(FFDIR, name)
    return bundled if os.path.isfile(bundled) else shutil.which(name) or name
video = sys.argv[1]
env = dict(os.environ, DYLD_LIBRARY_PATH=FFDIR)
probe = subprocess.run([binary("ffprobe"), "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "csv=p=0", video], env=env, capture_output=True, text=True, check=True)
w, h = map(int, probe.stdout.strip().split(","))
W = 192
H = round(h * W / w / 2) * 2
raw = subprocess.run([binary("ffmpeg"), "-hide_banner", "-loglevel", "error", "-i", video, "-vf", f"scale={W}:{H}", "-f", "image2pipe", "-c:v", "rawvideo", "-pix_fmt", "gray", "-"], env=env, capture_output=True, check=True).stdout
n = W * H
frames = [raw[i:i + n] for i in range(0, len(raw) - n + 1, n)]
means = [sum(f) / n for f in frames]
stds = [(sum((p - m) ** 2 for p in f[::7]) / len(f[::7])) ** 0.5 for f, m in zip(frames, means)]
diffs = [sum(abs(a - b) for a, b in zip(frames[i][::3], frames[i - 1][::3])) / len(frames[i][::3]) for i in range(1, len(frames))]
print(f"frames={len(frames)} size={W}x{H}")
print(f"mean luminance (0-255): min {min(means):.1f} max {max(means):.1f}; first {means[0]:.1f} last {means[-1]:.1f}")
blank = [i for i, s in enumerate(stds) if s < 2.0]
print(f"near-uniform (blank) frames: {len(blank)} {blank[:10]}")
jumps = [(i, means[i] - means[i - 1]) for i in range(1, len(means)) if abs(means[i] - means[i - 1]) > 4]
print(f"mean-luminance jumps > 4/255 between consecutive frames: {len(jumps)} {[(i, round(d, 1)) for i, d in jumps[:10]]}")
top = sorted(range(len(diffs)), key=lambda i: -diffs[i])[:8]
print("largest consecutive-frame mean |diff| (frame: value):", ", ".join(f"{i + 1} ({(i + 1) / 60:.2f}s): {diffs[i]:.2f}" for i in sorted(top)))
print(f"median |diff| {sorted(diffs)[len(diffs) // 2]:.3f}")
