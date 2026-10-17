#!/usr/bin/env python3
"""Original score + sound design + voice mix for the AI Voice Notes promo "Say it messy".

Python standard library only: additive sines, seeded noise, one-pole filters; no samples and
no third-party music. Deterministic. Cue times come from remotion/src/timeline.json, the same
file that drives the picture. Both spoken takes use the user's own ElevenLabs voice clone:
the opening thought and the final "AI Voice Notes" line, decoded from the delivered MP3s.

Writes remotion/public/audio/mix.wav (48 kHz stereo 16-bit) and audio/mix.measure.json.

Harmony (calm, unresolved while the thought is messy, resolving when the note appears):
  0–9.0   Fmaj7 ⇄ Em7 pad, ducked under the voice
  9.05    fillers fall: four soft descending plinks; filtered riser while the line untangles
  11.0    Cmaj9 bloom + bell as the line splits into the five bars; title letters tick
  13.7    four key taps ("milk"), chime on the match
  15.65   air whoosh, ring draws on a rising shimmer (Fmaj9)
  17.95   one chord per colour world: Am9, Fmaj7#11, G6sus, back home to Cmaj9
  21.15   the mint arc clicks into place; final Cmaj9 bell chord, tail to silence
"""

import json
import math
import random
import struct
import sys
import wave
from array import array
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))
from loudness import integrated_lufs, sample_peak_dbfs  # noqa: E402

SR = 48000
TAU = 2 * math.pi
TARGET_LUFS = -16.0
CEIL = 10 ** (-1.5 / 20)

tl = json.loads((ROOT / "remotion/src/timeline.json").read_text())
DUR = float(tl["film"]["durationSeconds"])
T = tl["t"]
VOICES = [tl["voice"], tl["outroVoice"]]
N = int(DUR * SR)
rng = random.Random(20261017)

L = array("d", bytes(8 * N))
R = array("d", bytes(8 * N))
VL = array("d", bytes(8 * N))  # voice bus (kept separate for ducking)


def hz(name):
    names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
    n, o = (name[:-1], int(name[-1]))
    return 440.0 * 2 ** ((12 * (o + 1) + names.index(n) - 69) / 12)


def put(start, buf, amp, pan=0.0):
    a = (pan + 1) * math.pi / 4
    gl, gr = math.cos(a) * amp, math.sin(a) * amp
    i0 = int(round(start * SR))
    for j, v in enumerate(buf):
        i = i0 + j
        if 0 <= i < N:
            L[i] += v * gl
            R[i] += v * gr


# ------------------------------------------------------------------ pad
CHORDS = [  # (start time, notes)
    (0.0, ["F3", "A3", "C4", "E4", "G4"]),
    (2.6, ["E3", "G3", "B3", "D4", "F#4"]),
    (5.2, ["F3", "A3", "C4", "E4", "G4"]),
    (7.6, ["E3", "G3", "B3", "D4", "A4"]),
    (11.0, ["C3", "G3", "B3", "D4", "E4"]),
    (13.1, ["A2", "E3", "B3", "C4", "G4"]),
    (15.65, ["F2", "C3", "A3", "E4", "G4"]),
    (T["sweeps"][0], ["A2", "E3", "G3", "B3", "C4"]),
    (T["sweeps"][1], ["F2", "C3", "E3", "A3", "B3"]),
    (T["sweeps"][2], ["G2", "D3", "E3", "A3", "C4"]),
    (T["sweeps"][3], ["C2", "G2", "E3", "B3", "D4"]),
]
XF = 0.7


def chord_weights(t):
    """Crossfade weights for each chord at time t (equal-power)."""
    w = [0.0] * len(CHORDS)
    for k, (s, _) in enumerate(CHORDS):
        nxt = CHORDS[k + 1][0] if k + 1 < len(CHORDS) else 1e9
        if t < s or t >= nxt + XF:
            continue
        a = 1.0 if t >= s + XF or k == 0 else math.sin(0.5 * math.pi * (t - s) / XF)
        b = 1.0 if t < nxt else math.cos(0.5 * math.pi * (t - nxt) / XF)
        w[k] = a * b
    return w


def pad():
    voices = []
    for k, (_, notes) in enumerate(CHORDS):
        for n in notes:
            f = hz(n)
            for d, pan in ((-5, -0.5), (0, 0.0), (5, 0.5)):
                voices.append((k, f * 2 ** (d / 1200), pan, rng.random() * TAU))
    lpL = lpR = 0.0
    step = 64  # chord weights change slowly; refresh every 64 samples
    w = chord_weights(0)
    for i in range(N):
        t = i / SR
        if i % step == 0:
            w = chord_weights(t)
        sl = sr_ = 0.0
        for k, f, pan, ph in voices:
            if w[k] <= 0:
                continue
            x = math.sin(TAU * f * t + ph) + 0.18 * math.sin(2 * TAU * f * t + ph)
            sl += x * w[k] * (0.5 - 0.35 * pan)
            sr_ += x * w[k] * (0.5 + 0.35 * pan)
        # swell in from silence, breathe, fade out at the end
        env = min(1.0, t / 1.2) * (1 - 0.12 * math.sin(TAU * 0.11 * t)) * max(0.0, min(1.0, (DUR - 0.15 - t) / 1.9))
        lpL += 0.06 * (sl - lpL)
        lpR += 0.06 * (sr_ - lpR)
        L[i] += lpL * env * 0.05
        R[i] += lpR * env * 0.05


# ------------------------------------------------------------------ voices / fx
def bell(start, f, amp, pan=0.0, decay=2.2):
    parts = [(1.0, 1.0), (2.0, 0.45), (3.01, 0.22), (4.2, 0.12), (5.43, 0.06)]
    out = []
    for j in range(int(decay * 2.6 * SR)):
        t = j / SR
        v = sum(a * math.sin(TAU * f * m * t) * math.exp(-t * m / decay) for m, a in parts)
        out.append(v * min(1.0, t / 0.004))
    put(start, out, amp, pan)


def plink(start, f0, amp, pan):
    out = []
    ph = 0.0
    for j in range(int(0.7 * SR)):
        t = j / SR
        f = f0 * 2 ** (-1.0 * min(1.0, t / 0.45))  # falls an octave, like the word dropping
        ph += TAU * f / SR
        out.append((math.sin(ph) + 0.25 * math.sin(2 * ph)) * min(1.0, t / 0.003) * math.exp(-t / 0.16))
    put(start, out, amp, pan)


def tick(start, amp, pan=0.0, f=5200.0):
    out = []
    for j in range(int(0.03 * SR)):
        t = j / SR
        out.append(math.sin(TAU * f * t) * math.exp(-t / 0.004) + rng.uniform(-1, 1) * 0.3 * math.exp(-t / 0.0015))
    put(start, out, amp, pan)


def key(start, amp, pan):
    out = []
    lp = 0.0
    for j in range(int(0.06 * SR)):
        t = j / SR
        lp += 0.35 * (rng.uniform(-1, 1) - lp)
        out.append((lp * 1.6 + 0.5 * math.sin(TAU * 1900 * t)) * math.exp(-t / 0.009))
    put(start, out, amp, pan)


def noise_swell(start, dur, amp, f0, f1, pan=0.0, shape=2.0):
    """Filtered noise whose cutoff glides f0→f1; envelope rises then falls (shape>1 = late peak)."""
    out = []
    lp1 = lp2 = 0.0
    n = int(dur * SR)
    for j in range(n):
        u = j / n
        fc = f0 * (f1 / f0) ** u
        a = 1 - math.exp(-TAU * fc / SR)
        lp1 += a * (rng.uniform(-1, 1) - lp1)
        lp2 += a * (lp1 - lp2)
        env = (u ** shape) * (1 - u) ** 0.6 * 3.0
        out.append(lp2 * env)
    put(start, out, amp, pan)


def glide(start, dur, f0, f1, amp, pan=0.0):
    out = []
    ph = 0.0
    n = int(dur * SR)
    for j in range(n):
        u = j / n
        f = f0 * (f1 / f0) ** u
        ph += TAU * f / SR
        out.append((math.sin(ph) + 0.3 * math.sin(2 * ph + 0.4)) * math.sin(math.pi * u) ** 1.5)
    put(start, out, amp, pan)


def click(start, amp):
    out = []
    for j in range(int(0.08 * SR)):
        t = j / SR
        out.append(math.sin(TAU * 1400 * t) * math.exp(-t / 0.012) + 0.6 * math.sin(TAU * 3300 * t) * math.exp(-t / 0.005))
    put(start, out, amp, 0.15)


def voice(cue):
    with wave.open(str(HERE / "src" / cue["file"])) as w:
        assert w.getframerate() == SR and w.getnchannels() == 1 and w.getsampwidth() == 2
        n = w.getnframes()
        d = struct.unpack("<%dh" % n, w.readframes(n))
    i0 = int(round(float(cue["offset"]) * SR))
    assert i0 >= 0 and i0 + n <= N, "Voice take must fit the film without truncation"
    for j, x in enumerate(d):
        VL[i0 + j] += x / 32768.0


# ------------------------------------------------------------------ score
pad()
for cue in VOICES:
    voice(cue)

clean = T["clean"]
for j, (f, pan) in enumerate([("A5", -0.35), ("F5", 0.3), ("D5", -0.2), ("C5", 0.4)]):
    plink(clean + 0.1 * j + 0.12, hz(f), 0.16, pan)
noise_swell(clean + 0.1, T["split"] - clean - 0.05, 0.13, 300, 7000, 0.0, 2.2)
glide(clean + 0.4, T["split"] - clean - 0.35, hz("C5"), hz("C6"), 0.035, -0.2)
for f, p, a in (("C5", -0.3, 0.12), ("G5", 0.3, 0.09), ("D6", 0.0, 0.06), ("E6", 0.2, 0.05)):
    bell(T["split"] + 0.02, hz(f), a, p, 2.6)
for i in range(9):  # "Groceries" written letter by letter
    tick(T["title"] + 0.045 * i + 0.05, 0.035, -0.4 + 0.1 * i)

for o in range(3):  # other notes slide in
    noise_swell(T["toCards"] + 0.45 + 0.09 * o, 0.45, 0.05, 900, 2400, -0.3 + 0.3 * o, 1.2)
for i in range(1, 5):  # m-i-l-k
    key(T["type"] + 0.125 * i - 0.01, 0.14, 0.12 * (i - 2))
bell(T["match"], hz("E6"), 0.1, 0.25, 1.6)
bell(T["match"] + 0.07, hz("B6"), 0.06, 0.35, 1.4)

noise_swell(T["toIcon"], 0.9, 0.09, 400, 3000, -0.1, 1.4)
glide(T["ring"], T["ringEnd"] - T["ring"] + 0.15, hz("A4"), hz("A5"), 0.035, 0.1)

for k, (notes, pan) in enumerate([(("E5", "B5"), -0.25), (("A5", "E6"), 0.25), (("D5", "A5"), -0.1), (("G5", "D6"), 0.1)]):
    s = T["sweeps"][k]
    noise_swell(s, T["sweepDurs"][k] + 0.2, 0.06, 600, 4500, pan, 1.6)
    bell(s + 0.05, hz(notes[0]), 0.07, pan, 1.8)
    bell(s + 0.12, hz(notes[1]), 0.045, -pan, 1.6)

click(T["snap"], 0.18)
for f, p, a in (("C4", -0.2, 0.1), ("G4", 0.2, 0.08), ("E5", -0.1, 0.07), ("B5", 0.15, 0.05), ("D6", 0.0, 0.04)):
    bell(T["snap"] + 0.02, hz(f), a, p, 3.2)

# ------------------------------------------------------------------ voice: gentle compressor
# Feed-forward, 10 ms attack / 120 ms release, threshold -24 dBFS, ratio 3:1, +8 dB makeup,
# so the quiet hesitations ("uh…") sit near the louder words.
env = 0.0
aA = math.exp(-1 / (0.010 * SR))
aR = math.exp(-1 / (0.120 * SR))
TH = 10 ** (-24 / 20)
MAKEUP = 10 ** (8 / 20)
for i in range(N):
    x = abs(VL[i])
    env = aA * env + (1 - aA) * x if x > env else aR * env + (1 - aR) * x
    gain = (TH / env) ** (1 - 1 / 3) if env > TH else 1.0
    VL[i] *= gain * MAKEUP

# ------------------------------------------------------------------ mix: music under the voice
MUSIC = 0.5  # the voice leads; score and effects sit ~6 dB under it
for i in range(N):
    t = i / SR
    duck = 1 - 0.5 * max(
        max(0.0, min(1.0, (t - float(cue["offset"]) + 0.3) / 0.3,
                     (float(cue["end"]) + 0.4 - t) / 0.5))
        for cue in VOICES
    )
    v = VL[i]
    L[i] = L[i] * duck * MUSIC + v
    R[i] = R[i] * duck * MUSIC + v

def limited(g):
    """Gain g then a 2 ms look-ahead brick-wall limiter (80 ms release) at CEIL. Returns new L, R."""
    blk = 96
    nb = (N + blk - 1) // blk
    want = []
    for b in range(nb):
        pk = max(max(abs(L[i]), abs(R[i])) for i in range(b * blk, min(N, (b + 1) * blk))) * g
        want.append(min(1.0, CEIL / pk) if pk > 0 else 1.0)
    gains = []
    cur = 1.0
    rel = math.exp(-blk / (0.080 * SR))
    for b in range(nb):
        target = min(want[b], want[b + 1] if b + 1 < nb else 1.0)  # look one block ahead
        cur = target if target < cur else rel * cur + (1 - rel) * target
        gains.append(cur)
    oL = array("d", bytes(8 * N))
    oR = array("d", bytes(8 * N))
    for i in range(N):
        b = i // blk
        k = (i % blk) / blk
        gg = gains[b] + (gains[min(b + 1, nb - 1)] - gains[b]) * k
        oL[i] = max(-CEIL, min(CEIL, L[i] * g * gg))
        oR[i] = max(-CEIL, min(CEIL, R[i] * g * gg))
    return oL, oR


g = 10 ** ((TARGET_LUFS - integrated_lufs(L, R, SR)) / 20)
for _ in range(3):  # the limiter lowers loudness a little; correct and re-limit
    Lg, Rg = limited(g)
    err = TARGET_LUFS - integrated_lufs(Lg, Rg, SR)
    if abs(err) < 0.2:
        break
    g *= 10 ** (err / 20)
fade = int(0.05 * SR)
(ROOT / "remotion/public/audio").mkdir(parents=True, exist_ok=True)
with wave.open(str(ROOT / "remotion/public/audio/mix.wav"), "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    frames = bytearray()
    for i in range(N):
        e = min(1.0, (N - 1 - i) / fade)
        frames += struct.pack("<hh", int(Lg[i] * e * 32767), int(Rg[i] * e * 32767))
    w.writeframes(bytes(frames))
report = {"integrated_lufs": round(integrated_lufs(Lg, Rg, SR), 2), "sample_peak_dbfs": round(sample_peak_dbfs(Lg, Rg), 2), "duration_s": DUR}
(HERE / "mix.measure.json").write_text(json.dumps(report, indent=2) + "\n")
print(report)
