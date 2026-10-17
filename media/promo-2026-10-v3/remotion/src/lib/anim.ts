// Deterministic animation helpers. Everything is a pure function of time (seconds),
// so any frame renders identically whether seeked directly or played in sequence.

export type Ease = (x: number) => number;

export const clamp = (x: number, a = 0, b = 1) => Math.min(b, Math.max(a, x));
export const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
export const inv = (t: number, t0: number, t1: number) => clamp((t - t0) / (t1 - t0));

const bez = (x1: number, y1: number, x2: number, y2: number): Ease => {
  // Cubic-bezier timing curve (same semantics as CSS), solved by bisection.
  const cx = (u: number) => 3 * x1 * u * (1 - u) ** 2 + 3 * x2 * u * u * (1 - u) + u ** 3;
  const cy = (u: number) => 3 * y1 * u * (1 - u) ** 2 + 3 * y2 * u * u * (1 - u) + u ** 3;
  return (x) => {
    if (x <= 0) return 0;
    if (x >= 1) return 1;
    let lo = 0;
    let hi = 1;
    for (let i = 0; i < 28; i++) {
      const m = (lo + hi) / 2;
      if (cx(m) < x) lo = m;
      else hi = m;
    }
    return cy((lo + hi) / 2);
  };
};

export const E = {
  linear: ((x) => x) as Ease,
  inOut: bez(0.65, 0, 0.35, 1),
  soft: bez(0.45, 0, 0.25, 1),
  out: bez(0.16, 1, 0.3, 1),
  in: bez(0.55, 0, 0.9, 0.4),
  inQuad: ((x) => x * x) as Ease,
  outQuad: ((x) => 1 - (1 - x) * (1 - x)) as Ease,
  outBack: ((x) => {
    const c1 = 1.9;
    const c3 = c1 + 1;
    return 1 + c3 * (x - 1) ** 3 + c1 * (x - 1) ** 2;
  }) as Ease,
};

/** Interpolate from a to b between t0 and t1 with an easing. Clamped. */
export const seg = (t: number, t0: number, t1: number, a: number, b: number, e: Ease = E.inOut) =>
  lerp(a, b, e(inv(t, t0, t1)));

export type Key = [time: number, value: number, ease?: Ease];
/** Keyframe track. Each key's ease shapes the segment arriving at that key. */
export const keys = (t: number, k: Key[]): number => {
  if (t <= k[0][0]) return k[0][1];
  for (let i = 1; i < k.length; i++) {
    if (t <= k[i][0]) {
      const [t0, v0] = k[i - 1];
      const [t1, v1, e] = k[i];
      return lerp(v0, v1, (e ?? E.inOut)(inv(t, t0, t1)));
    }
  }
  return k[k.length - 1][1];
};

/** Damped spring step response 0→1 that starts at t0 (overshoots, settles). */
export const spring = (t: number, t0: number, freq = 2.2, damping = 0.42) => {
  if (t <= t0) return 0;
  const tau = t - t0;
  const w = 2 * Math.PI * freq;
  const z = damping;
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * tau) * (Math.cos(wd * tau) + ((z * w) / wd) * Math.sin(wd * tau));
};

/** Decaying oscillation after an impact at t0 (starts at 0, peaks ±1). */
export const wobble = (t: number, t0: number, freq = 4, decay = 5) =>
  t < t0 ? 0 : Math.exp(-(t - t0) * decay) * Math.sin(2 * Math.PI * freq * (t - t0));

/** Parabolic hop: returns height 0→h→0 across [t0, t1]. */
export const hop = (t: number, t0: number, t1: number, h: number) => {
  const u = inv(t, t0, t1);
  return 4 * h * u * (1 - u);
};

export const mulberry32 = (seed: number) => {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let r = Math.imul(a ^ (a >>> 15), 1 | a);
    r = (r + Math.imul(r ^ (r >>> 7), 61 | r)) ^ r;
    return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
  };
};

export const fmt = (n: number) => (Math.round(n * 100) / 100).toString();
export const pts = (p: { x: number; y: number }[]) => p.map((q) => `${fmt(q.x)},${fmt(q.y)}`).join(" ");
