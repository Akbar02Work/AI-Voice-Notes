"""Print a 20 ms RMS envelope and detected voiced segments of a mono 16-bit WAV."""
import sys, wave, struct, math
w = wave.open(sys.argv[1]); sr = w.getframerate(); n = w.getnframes()
d = struct.unpack('<%dh' % n, w.readframes(n))
hop = int(sr * 0.02)
env = []
for i in range(0, n - hop, hop):
    s = d[i:i + hop]; env.append(math.sqrt(sum(x * x for x in s) / hop) / 32768)
peak = max(env)
db = [20 * math.log10(max(e, 1e-6) / peak) for e in env]
thr = float(sys.argv[2]) if len(sys.argv) > 2 else -30
segs = []; on = None
for i, v in enumerate(db):
    if v > thr and on is None: on = i
    if v <= thr and on is not None:
        segs.append((on, i)); on = None
if on is not None: segs.append((on, len(db)))
# merge gaps < 60 ms
m = []
for s in segs:
    if m and s[0] - m[-1][1] < 3: m[-1] = (m[-1][0], s[1])
    else: m.append(s)
print('duration %.2f s' % (n / sr))
for a, b in m:
    if b - a >= 3: print('%.2f-%.2f  (%.2f)  max %.1f dB' % (a * .02, b * .02, (b - a) * .02, max(db[a:b])))
line = ''.join(' .:-=+*#%@'[min(9, max(0, int((v + 50) / 5)))] for v in db)
for k in range(0, len(line), 50): print('%5.2f %s' % (k * .02, line[k:k + 50]))
