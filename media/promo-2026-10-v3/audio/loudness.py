"""ITU-R BS.1770-4 integrated loudness (48 kHz only) and sample peak.

Pure standard library. Used to normalise the generated score; the final MP4
is measured independently with ffmpeg's loudnorm analysis (see render.sh).
"""

import math

# K-weighting biquads for 48 kHz (BS.1770-4, Table 1 and 2).
_SHELF = ((1.53512485958697, -2.69169618940638, 1.19839281085285), (-1.69065929318241, 0.73248077421585))
_HPF = ((1.0, -2.0, 1.0), (-1.99004745483398, 0.99007225036621))


def _biquad(x, coeffs):
    (b0, b1, b2), (a1, a2) = coeffs
    y = [0.0] * len(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, xi in enumerate(x):
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, xi, y1, yi
        y[i] = yi
    return y


def integrated_lufs(left, right, sr=48000):
    if sr != 48000:
        raise ValueError("coefficients are for 48 kHz")
    chans = [_biquad(_biquad(list(c), _SHELF), _HPF) for c in (left, right)]
    block, step = int(0.4 * sr), int(0.1 * sr)
    # Prefix sums of squares for fast block means.
    prefix = []
    for c in chans:
        p = [0.0]
        acc = 0.0
        for v in c:
            acc += v * v
            p.append(acc)
        prefix.append(p)
    z = []
    for start in range(0, len(chans[0]) - block + 1, step):
        z.append(sum((p[start + block] - p[start]) / block for p in prefix))

    def lufs(ms):
        return -0.691 + 10 * math.log10(ms) if ms > 0 else -math.inf

    gated = [m for m in z if lufs(m) > -70]
    if not gated:
        return -math.inf
    rel = lufs(sum(gated) / len(gated)) - 10
    gated = [m for m in gated if lufs(m) > rel]
    return lufs(sum(gated) / len(gated))


def sample_peak_dbfs(left, right):
    peak = max(max(abs(v) for v in left), max(abs(v) for v in right))
    return 20 * math.log10(peak) if peak > 0 else -math.inf
