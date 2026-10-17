import React from "react";
import { AbsoluteFill, Audio, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { E, clamp, inv, lerp, spring } from "./lib/anim";
import { em, useFontsReady } from "./lib/measure";
import { CARDS, GROCERY_SLOT, Glyph, Layout, TITLE, TextBox, WORDMARK, WORDS, W_CAP, W_SPEECH, W_SUM, W_TITLE, buildLayout } from "./layout";
import { FONT, FPS, Format, ICON, Mode, Pal, RGB, SIZE, WORLDS, mixPal, rgba } from "./theme";
import TL from "./timeline.json";
import ENV from "./voiceEnv.json";

export type FilmProps = { format: Format; mode: Mode; withAudio: boolean; skip?: number };

const T = TL.t;
const V = TL.voice.offset;
const R2D = Math.PI / 180;

// ------------------------------------------------------------------ colour worlds
// Four radial sweeps from the icon centre: brand → blue → amber → violet → brand. Each front is
// a wide soft gradient (no hard disc edge), travelling from -BAND to rMax + BAND so every pixel
// fully reaches the new world.
const BAND = 180;
const sweepFront = (t: number, k: number, rMax: number) => lerp(-BAND, rMax + BAND, E.soft(inv(t, T.sweeps[k], T.sweeps[k] + T.sweepDurs[k])));
const palAt = (t: number, mode: Mode, d: number, rMax: number): Pal => {
  const W = WORLDS[mode];
  let p = W[0];
  for (let k = 0; k < 4; k++) {
    if (t < T.sweeps[k]) break;
    const r = sweepFront(t, k, rMax);
    p = mixPal(p, W[(k + 1) % 4], E.inOut(clamp((r - d) / BAND + 0.5)));
  }
  return p;
};

// ------------------------------------------------------------------ voice line
// Envelope smoothed like a VU meter (fast attack, slow release) and a phase that runs faster
// while the voice is loud, both precomputed per frame and interpolated: the line swells and
// flows with the speech instead of twitching.
const ENV_S: number[] = [];
const PHASE: number[] = [];
{
  let e = 0;
  let ph = 0;
  const att = 1 - Math.exp(-1 / (0.07 * FPS));
  const rel = 1 - Math.exp(-1 / (0.3 * FPS));
  for (const x of ENV) {
    e += (x > e ? att : rel) * (x - e);
    ENV_S.push(e);
    ph += (1.1 + 4.2 * e) / FPS;
    PHASE.push(ph);
  }
}
const sampleArr = (arr: number[], t: number, before: (t: number) => number) => {
  const i = (t - V) * FPS;
  if (i <= 0) return before(t);
  if (i >= arr.length - 1) return arr[arr.length - 1] + before(t) - before(V + (arr.length - 1) / FPS);
  const j = Math.floor(i);
  return arr[j] + (arr[j + 1] - arr[j]) * (i - j);
};
const envAt = (t: number) => (t - V < 0 ? 0 : sampleArr(ENV_S, t, () => 0));
const phaseAt = (t: number) => sampleArr(PHASE, t, (x) => 1.1 * (x - V));
// where along the line the sentence currently is (the swell travels with the words)
const progressAt = (t: number) => {
  let p = 0;
  WORDS.forEach((w, i) => (p = Math.max(p, i * E.inOut(inv(t, V + w.at - 0.1, V + w.at + 0.35)))));
  return 0.06 + (0.88 * (p + 0.5)) / WORDS.length;
};

type Knot = { s: number; a: number; R: number; n: number; hw: number; th: number; dir: number };
const knots = (t: number, scale: number): Knot[] => {
  const out: Knot[] = [];
  let fill = 0;
  let small = 0;
  WORDS.forEach((w, i) => {
    if (!w.knot) return;
    const big = w.knot >= 1;
    const order = big ? fill++ : small++;
    const u0 = big ? T.clean + 0.1 * order : T.clean + 0.42 + 0.12 * order;
    const a = E.out(inv(t, V + w.at, V + w.at + 0.6)) * (1 - E.inOut(inv(t, u0, u0 + 0.8)));
    out.push({
      s: 0.06 + (0.88 * (i + 0.5)) / WORDS.length,
      a,
      R: 52 * w.knot * scale,
      n: big ? 2 : 1,
      hw: big ? 0.052 : 0.034,
      th: [0.45, -0.35, 0.2, -0.5, 0.3, -0.15][(i * 7) % 6],
      dir: i % 2 ? 1 : -1,
    });
  });
  return out;
};

/** The voice line (v1 shape): one line, tangled knots on hesitations, round swells with the voice. */
const linePath = (t: number, L: Layout) => {
  const { x0, x1, y, amp } = L.S.line;
  const scale = L.f === "16x9" ? 1 : 0.82;
  const calm = 1 - E.inOut(inv(t, T.clean, T.clean + 0.6));
  const breathe = 6 * (0.7 + 0.3 * Math.sin(t * 2 * Math.PI * 0.45)) * calm;
  const env = envAt(t);
  // the swell dies away during the clean-up, so the line is straight when it gathers into the bars
  const A = (breathe + amp * 2.8 * Math.tanh(env * 2.2)) * (1 - E.inOut(inv(t, T.clean, T.clean + 0.9))); // soft-saturated: loud words swell, never reach the copy
  const ph = phaseAt(t);
  const c = progressAt(t);
  const ks = knots(t, scale);
  // the line first gathers sideways under the glyph, then rises into it (never crosses the copy)
  const cx_ = E.inOut(inv(t, T.contract - 0.25, T.split - 0.2));
  const cy_ = E.inOut(inv(t, T.contract + 0.15, T.split));
  const gx = L.noteGlyph.cx;
  const span = (ICON.bars[4].x + ICON.barW / 2 - ICON.barW / 2) * L.noteGlyph.g;
  const X0 = lerp(x0, gx - span, cx_);
  const X1 = lerp(x1, gx + span, cx_);
  const Y = lerp(y, L.noteGlyph.cy, cy_);
  const N = 720;
  let d = "";
  for (let i = 0; i <= N; i++) {
    const s = i / N;
    let px = X0 + (X1 - X0) * s;
    const taper = Math.pow(Math.sin(Math.PI * s), 1.5);
    const swell = 0.4 + 0.6 * Math.exp(-(((s - c) / 0.24) ** 2));
    const wave = 0.62 * Math.sin(2 * Math.PI * 1.6 * s - 2.0 * ph) + 0.3 * Math.sin(2 * Math.PI * 2.7 * s + 1.4 * ph + 1.1) + 0.16 * Math.sin(2 * Math.PI * 4.1 * s - 2.9 * ph + 2.3);
    let py = Y + A * taper * swell * wave;
    for (const k of ks) {
      if (k.a <= 0.0005) continue;
      const u = (s - k.s + k.hw) / (2 * k.hw);
      if (u <= 0 || u >= 1) continue;
      const w = Math.pow(Math.sin(Math.PI * u), 2);
      const ang = 2 * Math.PI * k.n * u;
      const dx = -k.a * k.R * w * Math.sin(ang);
      const dy = k.dir * k.a * k.R * w * (1 - Math.cos(ang)) * 0.9;
      px += dx * Math.cos(k.th) - dy * Math.sin(k.th);
      py += dx * Math.sin(k.th) + dy * Math.cos(k.th);
    }
    d += `${i ? "L" : "M"}${px.toFixed(2)},${py.toFixed(2)}`;
  }
  return d;
};

// ------------------------------------------------------------------ glyph / icon state
const lerpG = (a: Glyph, b: Glyph, k: number): Glyph => ({ cx: lerp(a.cx, b.cx, k), cy: lerp(a.cy, b.cy, k), g: lerp(a.g, b.g, k) });
const liftAt = (t: number) => 1 + 0.035 * E.out(inv(t, T.match, T.match + 0.45));
const liftP = (L: Layout, t: number, x: number, y: number) => {
  const c = L.card(GROCERY_SLOT);
  const l = liftAt(t);
  return { x: c.x + c.w / 2 + (x - c.x - c.w / 2) * l, y: c.y + c.h / 2 + (y - c.y - c.h / 2) * l, l };
};
const cardBox = (L: Layout, t: number, b: TextBox): TextBox => {
  const p = liftP(L, t, b.x, b.y);
  return { x: p.x, y: p.y, size: b.size * p.l };
};
const cardGlyph = (L: Layout, t: number): Glyph => {
  const g = L.card(GROCERY_SLOT).glyph;
  const p = liftP(L, t, g.cx, g.cy);
  return { cx: p.x, cy: p.y, g: g.g * p.l };
};
const kCards = (t: number) => E.inOut(inv(t, T.toCards, T.toCards + 0.7));
const kIcon = (t: number) => E.inOut(inv(t, T.toIcon, T.toIcon + 0.9));
const kEnd = (t: number) => E.inOut(inv(t, T.endMove, T.endMove + 0.75));

const iconAt = (L: Layout, t: number) => {
  const k = kEnd(t);
  return { cx: lerp(L.icon.cx, L.endIcon.cx, k), cy: lerp(L.icon.cy, L.endIcon.cy, k), s: lerp(L.icon.s, L.endIcon.s, k) };
};
const glyphAt = (L: Layout, t: number): Glyph => {
  if (t < T.toCards) return L.noteGlyph;
  if (t < T.toIcon) return lerpG(L.noteGlyph, cardGlyph(L, t), kCards(t));
  const ic = iconAt(L, t);
  return lerpG(cardGlyph(L, T.toIcon), { cx: ic.cx, cy: ic.cy, g: ic.s }, kIcon(t));
};
const sumBoxAt = (L: Layout, t: number): TextBox => {
  const c = cardBox(L, t, L.card(GROCERY_SLOT).sum);
  const k = kCards(t);
  return { x: lerp(L.noteSum.x, c.x, k), y: lerp(L.noteSum.y, c.y, k), size: lerp(L.noteSum.size, c.size, k) };
};
const titleBoxAt = (L: Layout, t: number): TextBox => {
  const c = cardBox(L, t, L.card(GROCERY_SLOT).title);
  const k = kCards(t);
  return { x: lerp(L.noteTitle.x, c.x, k), y: lerp(L.noteTitle.y, c.y, k), size: lerp(L.noteTitle.size, c.size, k) };
};

const arc = (cx: number, cy: number, r: number, a0: number, a1: number) => {
  const p = (a: number) => `${(cx + r * Math.cos(a * R2D)).toFixed(2)},${(cy + r * Math.sin(a * R2D)).toFixed(2)}`;
  return `M${p(a0)}A${r.toFixed(2)},${r.toFixed(2)} 0 ${a1 - a0 > 180 ? 1 : 0} 1 ${p(a1)}`;
};

// ------------------------------------------------------------------ small text helpers
type Span = { text: string; accent?: boolean };
const Txt: React.FC<{ x: number; y: number; size: number; weight: number; fill: string; anchor?: "start" | "middle"; opacity?: number; children: React.ReactNode; rotate?: number }> = ({ x, y, size, weight, fill, anchor = "start", opacity = 1, children, rotate = 0 }) =>
  opacity <= 0.002 ? null : (
    <text
      x={0}
      y={0}
      transform={`translate(${x.toFixed(2)} ${y.toFixed(2)}) rotate(${(rotate + 0.03).toFixed(3)})`}
      fontFamily={FONT}
      fontSize={size}
      fontWeight={weight}
      fill={fill}
      opacity={clamp(opacity)}
      textAnchor={anchor}
      textRendering="geometricPrecision"
      style={{ fontKerning: "normal", letterSpacing: `${(-0.012 * size).toFixed(2)}px` }}
    >
      {children}
    </text>
  );

const CAPTIONS: Record<string, Span[]> = {
  clean: [{ text: "AI " }, { text: "cleans it up.", accent: true }],
  find: [{ text: "Find " }, { text: "any thought.", accent: true }],
  offline: [{ text: "Cloud AI or " }, { text: "fully offline.", accent: true }],
  colors: [{ text: "In " }, { text: "your colors.", accent: true }],
};

// ------------------------------------------------------------------ film
export const Film: React.FC<FilmProps> = ({ format, mode, withAudio, skip = 0 }) => {
  const frame = useCurrentFrame();
  const { width: W, height: H } = useVideoConfig();
  const ready = useFontsReady();
  const L = React.useMemo(() => (ready ? buildLayout(format) : null), [ready, format]);
  if (!L) return null;
  const t = frame / FPS + skip; // film time; 9:16 skips the quiet opening and starts on the voice
  // The website composition adds canvas above and below the 9:16 artwork.
  // Keep foreground geometry intact; the animated background covers the full viewport.
  const top = -(H - SIZE[format].H) / 2;
  const W0 = WORLDS[mode][0];
  const dark = mode === "dark";
  const center = { x: L.icon.cx, y: L.icon.cy };
  const rMax = Math.max(...[[0, top], [W, top], [0, top + H], [W, top + H]].map(([x, y]) => Math.hypot(x - center.x, y - center.y))) + 80;
  const pal = (x: number, y: number) => palAt(t, mode, Math.hypot(x - center.x, y - center.y), rMax);
  const P0 = pal(center.x, center.y); // centre colour (glow, icon)
  const cx = W / 2;
  const els: React.ReactNode[] = [];

  // ---------------- "Just talk." (the 9:16 cut opens straight on the voice instead)
  if (!skip) {
    const a = E.out(inv(t, 0.08, 0.6));
    const o = E.in(inv(t, T.justTalkOut, T.justTalkOut + 0.32));
    const S = L.S.justTalk;
    els.push(
      <Txt key="jt" x={cx} y={S.y + (1 - a) * 26 - o * 30} size={S.size} weight={W_TITLE} fill={rgba(W0.ink)} anchor="middle" opacity={a * (1 - o)}>
        Just <tspan fill={rgba(W0.text)}>talk.</tspan>
      </Txt>,
    );
  }

  const barColor = rgba(P0.ring);
  const glyphEls = (ts: number) => {
    const out: React.ReactNode[] = [];
    const G = glyphAt(L, ts);
    const g0 = L.noteGlyph.g;
    const span = (ICON.bars[4].x + ICON.barW / 2 - ICON.barW / 2) * g0; // half span of the contracted line (bar centres)
    const sp = E.inOut(inv(ts, T.split, T.split + 0.3));
    const grow = clamp(spring(ts, T.barsGrow, 1.9, 0.5), 0, 1.2);
    const scaleG = G.g / g0;
    ICON.bars.forEach((b, i) => {
      // piece of the straight line that becomes bar i (pieces tile the line, then shrink to dots)
      const edges = [-span, ...ICON.bars.slice(0, 4).map((q, j) => ((q.x + ICON.bars[j + 1].x) / 2) * g0), span];
      const pc = (edges[i] + edges[i + 1]) / 2;
      const ph = (edges[i + 1] - edges[i]) / 2;
      const w = ICON.barW * G.g;
      const bxc = G.cx + lerp(pc * scaleG, b.x * G.g, sp);
      const hx = lerp(ph * scaleG, 0, sp);
      const hy = Math.max(0, ((b.h - ICON.barW) * G.g) / 2) * grow;
      out.push(<rect key={`bar${i}`} x={bxc - hx - w / 2} y={G.cy - hy - w / 2} width={2 * hx + w} height={2 * hy + w} rx={w / 2} fill={barColor} />);
    });
    if (ts >= T.ring) {
      const ic = iconAt(L, ts);
      const p = E.inOut(inv(ts, T.ring, T.ringEnd));
      const [a0, a1] = ICON.main;
      // "Cloud AI": the ring arrives as a loose dashed signal, a little wider, flowing round;
      // "fully offline": the dashes close into the solid ring of the icon, on the device.
      const close = E.inOut(inv(ts, T.ringClose, T.ringClose + 0.45));
      const pulse = 1 + 0.05 * Math.sin(Math.PI * clamp(inv(ts, T.ringClose + 0.3, T.ringClose + 0.75)));
      const rr = ICON.R * ic.s * lerp(1.14, 1, close) * pulse;
      const dash = lerp(24, 1000, close * close);
      const gap = lerp(26, 0, close);
      if (p > 0.004)
        out.push(
          <path key="ring" d={arc(ic.cx, ic.cy, rr, a0, a0 + (a1 - a0) * p)} pathLength={1000} fill="none" stroke={barColor} strokeWidth={ICON.stroke * ic.s * lerp(0.55, 1, close)} strokeLinecap="round" strokeDasharray={close >= 1 ? undefined : `${dash.toFixed(2)} ${gap.toFixed(2)}`} strokeDashoffset={((1 - close) * -ts * 140).toFixed(2)} />,
        );
      if (ts >= T.mint) {
        const s = spring(ts, T.mint, 1.5, 0.62);
        const r = ICON.R * ic.s * (1 + (1 - s) * 0.6);
        const off = (1 - s) * 28;
        const op = E.out(inv(ts, T.mint, T.mint + 0.18));
        out.push(
          <path key="mint" d={arc(ic.cx, ic.cy, r, ICON.accent[0] + off, ICON.accent[1] + off)} fill="none" stroke={rgba(P0.mint)} strokeWidth={ICON.stroke * ic.s} strokeLinecap="round" opacity={op} />,
        );
      }
    }
    return out;
  };

  // ---------------- voice line → glyph bars → icon
  if (t < T.split) {
    const sw = lerp(6, ICON.barW * L.noteGlyph.g, E.inOut(inv(t, T.contract, T.split)));
    els.push(<path key="line" d={linePath(t, L)} fill="none" stroke={rgba(W0.ring)} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" />);
  } else {
    // 180° shutter: fast moves (bars flying to the icon, the mint arc snapping in) are averaged
    // over several sub-frame samples; stacking sample i at opacity 1/(i+1) is an exact average.
    const SH = 0.5 / FPS;
    const probe = (ts: number) => {
      const G = glyphAt(L, ts);
      const ic = iconAt(L, ts);
      const sm = ts >= T.mint ? spring(ts, T.mint, 1.5, 0.62) : 1;
      const mr = ICON.R * ic.s * (1 + (1 - sm) * 0.6);
      const ma = (ICON.accent[0] + (1 - sm) * 28) * R2D;
      return [G.cx, G.cy, ic.cx + mr * Math.cos(ma), ic.cy + mr * Math.sin(ma)];
    };
    const A = probe(Math.max(T.split, t - SH / 2));
    const B = probe(t + SH / 2);
    const travel = Math.max(Math.hypot(B[0] - A[0], B[1] - A[1]), Math.hypot(B[2] - A[2], B[3] - A[3]));
    const n = clamp(Math.ceil(travel / 3), 1, 14);
    const samples = n === 1 ? [t] : Array.from({ length: n }, (_, i) => Math.max(T.split, t + (i / (n - 1) - 0.5) * SH));
    samples.forEach((ts, si) => els.push(<g key={`gs${si}`} opacity={1 / (si + 1)}>{glyphEls(ts)}</g>));
  }

  // ---------------- spoken words → the note
  const sumB = sumBoxAt(L, t);
  const noteFade = 1 - E.inOut(inv(t, T.toIcon + 0.05, T.toIcon + 0.45));
  const inCard = kCards(t);
  let fi = 0;
  let hi = 0;
  let ki = 0;
  WORDS.forEach((w, i) => {
    const b = L.speech[i];
    const a = E.out(inv(t, V + w.at - 0.05, V + w.at + 0.26));
    if (a <= 0) return;
    let x = b.x;
    let y = b.y + (1 - a) * 22;
    let size = b.size;
    let op = a;
    let weight = W_SPEECH;
    const col: RGB = W0.ink;
    if (w.kind === "filler") {
      const j = fi++;
      const fall = (ts: number) => E.in(inv(ts, T.clean + 0.1 * j, T.clean + 0.1 * j + 0.8));
      const SH = 0.5 / FPS;
      const n = clamp(Math.ceil(((fall(t + SH / 2) - fall(t - SH / 2)) * 240) / 2.5), 1, 12);
      const d = inv(t, T.clean + 0.1 * j, T.clean + 0.1 * j + 0.8);
      const baseOp = op * 0.42 * (1 - E.inQuad(d));
      for (let si = 0; si < n; si++) {
        const ts = n === 1 ? t : t + (si / (n - 1) - 0.5) * SH;
        const e = fall(ts);
        els.push(
          <g key={`w${i}s${si}`} opacity={1 / (si + 1)}>
            <Txt x={x} y={y + e * 240} size={size} weight={weight} fill={rgba(col)} opacity={baseOp} rotate={e * (j % 2 ? 10 : -8)}>{w.text}</Txt>
          </g>,
        );
      }
      return;
    }
    if (w.kind === "helper") {
      const j = hi++;
      const d = E.inOut(inv(t, T.clean + 0.45 + 0.05 * j, T.clean + 0.95 + 0.05 * j));
      y -= d * 20;
      op *= 0.92 * (1 - d);
      els.push(<Txt key={`w${i}`} x={x} y={y} size={size} weight={weight} fill={rgba(col)} opacity={op}>{w.text}</Txt>);
      return;
    }
    // kept word: glide into the summary, then live inside the note / card
    const j = ki++;
    // "bread", "and", "milk" settle first; "eggs." (upper line, ends up leftmost) comes down
    // last on an arc above them, so no two words ever cross.
    const delay = [0.38, 0, 0.08, 0.14][j];
    const raw = inv(t, T.glide + delay, T.glide + 0.85 + delay);
    const g = E.inOut(raw);
    const tx = sumB.x + L.sumTok[i] * sumB.size;
    x = lerp(b.x, tx, j === 0 ? E.out(raw) : g); // "eggs." travels across first, then lands
    y = lerp(y, sumB.y, g) + Math.sin(Math.PI * g) * b.size * [-0.9, 0.45, 0.2, 0.3][j];
    size = lerp(b.size, sumB.size, g);
    weight = lerp(W_SPEECH, W_SUM, g);
    const sumAlpha = lerp(0.82, 0.72, inCard);
    op *= lerp(1, sumAlpha, g) * noteFade;
    const swap = E.inOut(inv(g, 0.25, 0.7));
    // search hit: "milk" highlighted inside the lifted card
    if (w.to === "milk." && t >= T.match) {
      const h = E.out(inv(t, T.match, T.match + 0.35));
      const mw = em("milk", W_SUM) * size;
      els.push(<rect key="hl" x={x - 6} y={y - size * 0.84} width={(mw + 12) * h} height={size * 1.12} rx={size * 0.22} fill={rgba(W0.ring, (dark ? 0.3 : 0.22) * noteFade)} />);
    }
    if (swap < 1) els.push(<Txt key={`w${i}a`} x={x} y={y} size={size} weight={weight} fill={rgba(col)} opacity={op * (1 - swap)}>{w.text}</Txt>);
    if (swap > 0) els.push(<Txt key={`w${i}b`} x={x} y={y} size={size} weight={weight} fill={rgba(col)} opacity={op * swap}>{w.to}</Txt>);
  });

  // ---------------- title "Groceries", written letter by letter
  if (t >= T.title) {
    const tb = titleBoxAt(L, t);
    const chars = [...TITLE];
    chars.forEach((ch, i) => {
      const a = E.out(inv(t, T.title + i * 0.045, T.title + i * 0.045 + 0.32));
      const x = tb.x + (i ? em(TITLE.slice(0, i), W_TITLE) : 0) * tb.size;
      els.push(
        <Txt key={`t${i}`} x={x} y={tb.y + (1 - a) * 16} size={tb.size} weight={W_TITLE} fill={rgba(W0.ink)} opacity={a * noteFade}>
          {ch}
        </Txt>,
      );
    });
  }

  // ---------------- cards + search
  const cardRx = L.f === "16x9" ? 30 : 36;
  const cardsLayer: React.ReactNode[] = [];
  if (t >= T.toCards) {
    const exit = E.in(inv(t, T.toIcon, T.toIcon + 0.45));
    const dim = lerp(1, 0.32, E.inOut(inv(t, T.match, T.match + 0.4)));
    let order = 0;
    CARDS.forEach((c, slot) => {
      if (!c) return;
      const o = order++;
      const p = E.out(inv(t, T.toCards + 0.5 + 0.09 * o, T.toCards + 1.05 + 0.09 * o));
      const box = L.card(slot);
      const dy = (1 - p) * 50 + exit * 40;
      const op = p * dim * (1 - exit);
      if (op <= 0.002) return;
      const G = box.glyph;
      cardsLayer.push(
        <g key={`card${slot}`} opacity={op} transform={`translate(0 ${dy.toFixed(2)})`}>
          <rect x={box.x} y={box.y} width={box.w} height={box.h} rx={cardRx} fill={rgba(W0.surface)} />
          {ICON.bars.map((b, i) => {
            const w = ICON.barW * G.g;
            return <rect key={i} x={G.cx + b.x * G.g - w / 2} y={G.cy - (b.h * G.g) / 2} width={w} height={b.h * G.g} rx={w / 2} fill={rgba(W0.ring, 0.75)} />;
          })}
          <Txt x={box.title.x} y={box.title.y} size={box.title.size} weight={W_TITLE} fill={rgba(W0.ink)}>{c.title}</Txt>
          <Txt x={box.sum.x} y={box.sum.y} size={box.sum.size} weight={W_SUM} fill={rgba(W0.ink)} opacity={0.66}>{c.sum}</Txt>
        </g>,
      );
    });
    // the Groceries card's own background grows in around the note
    const gb = L.card(GROCERY_SLOT);
    const bgA = E.inOut(inv(t, T.toCards + 0.35, T.toCards + 0.8)) * (1 - E.inOut(inv(t, T.toIcon, T.toIcon + 0.4)));
    if (bgA > 0.002) {
      const p1 = liftP(L, t, gb.x, gb.y);
      const l = p1.l;
      const outline = E.out(inv(t, T.match, T.match + 0.4));
      cardsLayer.push(
        <rect key="gcard" x={p1.x} y={p1.y} width={gb.w * l} height={gb.h * l} rx={cardRx * l} fill={rgba(W0.surface)} stroke={rgba(W0.ring, 0.9 * outline)} strokeWidth={3} opacity={bgA} />,
      );
    }
    // search pill
    const pa = E.out(inv(t, T.pill, T.pill + 0.5)) * (1 - E.inOut(inv(t, T.toIcon, T.toIcon + 0.35)));
    if (pa > 0.002) {
      const S = L.S.pill;
      const x0 = cx - S.w / 2;
      const y0 = S.cy - S.h / 2 + (1 - pa) * 20;
      const n = Math.floor(clamp(inv(t, T.type, T.type + 0.5)) * 4 + 1e-6);
      const typed = "milk".slice(0, n);
      const tx = x0 + S.h * 0.95;
      const caretOn = (t * 1.9) % 1 < 0.55 && t < T.match + 0.7;
      const cxm = x0 + S.h * 0.5;
      const cym = y0 + S.h / 2;
      const ir = S.h * 0.16;
      cardsLayer.push(
        <g key="pill" opacity={pa}>
          <rect x={x0} y={y0} width={S.w} height={S.h} rx={S.h / 2} fill={rgba(W0.surface)} stroke={rgba(W0.ink, 0.14)} strokeWidth={2} />
          <circle cx={cxm - ir * 0.25} cy={cym - ir * 0.25} r={ir} fill="none" stroke={rgba(W0.ink, 0.55)} strokeWidth={S.h * 0.045} />
          <line x1={cxm + ir * 0.5} y1={cym + ir * 0.5} x2={cxm + ir * 1.15} y2={cym + ir * 1.15} stroke={rgba(W0.ink, 0.55)} strokeWidth={S.h * 0.045} strokeLinecap="round" />
          <Txt x={tx} y={cym + S.size * 0.36} size={S.size} weight={W_SUM} fill={rgba(W0.ink)}>{typed}</Txt>
          {caretOn ? <rect x={tx + em(typed, W_SUM) * S.size + 4} y={cym - S.size * 0.5} width={3} height={S.size} rx={1.5} fill={rgba(W0.text)} /> : null}
        </g>,
      );
    }
  }

  // ---------------- captions
  TL.captions.forEach((c) => {
    if (t < c.in || t > c.out + 0.35) return;
    const a = E.out(inv(t, c.in, c.in + 0.45));
    const o = E.in(inv(t, c.out, c.out + 0.3));
    const S = L.S.caption;
    const y = S.y + (1 - a) * 26 - o * 14;
    const P = pal(cx, y - S.size * 0.35);
    els.push(
      <Txt key={`cap-${c.id}`} x={cx} y={y} size={S.size} weight={W_CAP} fill={rgba(P.ink)} anchor="middle" opacity={a * (1 - o)}>
        {CAPTIONS[c.id].map((s, i) => (
          <tspan key={i} fill={s.accent ? rgba(P.text) : undefined}>{s.text}</tspan>
        ))}
      </Txt>,
    );
  });

  // ---------------- wordmark
  if (t >= T.wordmark) {
    const p = E.out(inv(t, T.wordmark, T.wordmark + 0.7));
    const b = L.endWord;
    const ink = rgba(P0.ink);
    if (L.f === "16x9") {
      els.push(
        <g key="wm">
          <clipPath id="wmclip">
            <rect x={b.x - 8} y={b.y - b.size * 1.1} width={(L.wmW + 40) * p + 1} height={b.size * 1.5} />
          </clipPath>
          <g clipPath="url(#wmclip)">
            <Txt x={b.x - (1 - p) * 70} y={b.y} size={b.size} weight={W_TITLE} fill={ink} opacity={E.out(inv(t, T.wordmark, T.wordmark + 0.4))}>
              {WORDMARK}
            </Txt>
          </g>
        </g>,
      );
    } else {
      els.push(
        <Txt key="wm" x={b.x} y={b.y + (1 - p) * 34} size={b.size} weight={W_TITLE} fill={ink} opacity={p}>
          {WORDMARK}
        </Txt>,
      );
    }
  }

  // ---------------- background: the sweeps as one smooth radial gradient (no disc edges)
  const rTot = rMax + BAND;
  const moving = Math.max(...T.sweeps.map((s0, k) => Math.sin(Math.PI * clamp(inv(t, s0 - 0.1, s0 + T.sweepDurs[k] + 0.15)))));
  const grainA = (dark ? 0.06 : 0.045) * moving;
  const STOPS = 40;
  const bgStops = Array.from({ length: STOPS + 1 }, (_, i) => {
    const d = (i / STOPS) * rTot;
    return <stop key={i} offset={(i / STOPS).toFixed(4)} stopColor={rgba(pal(center.x + d, center.y).bg)} />;
  });

  return (
    <AbsoluteFill style={{ background: rgba(W0.bg), width: W, height: H, overflow: "hidden" }}>
      <svg width={W} height={H} viewBox={`0 ${top} ${W} ${H}`}>
        <defs>
          <radialGradient id="bgGrad" cx={center.x} cy={center.y} r={rTot} gradientUnits="userSpaceOnUse">
            {bgStops}
          </radialGradient>
          {/* fine grain, re-seeded every frame, only while a colour front moves: dithers the
              front so 8-bit video shows no steps; at rest the page is one flat colour */}
          <filter id="grain" x="0" y="0" width="100%" height="100%">
            <feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" seed={frame % 997} />
            <feColorMatrix type="saturate" values="0" />
          </filter>
        </defs>
        <rect y={top} width={W} height={H} fill="url(#bgGrad)" />
        {grainA > 0.002 ? <rect y={top} width={W} height={H} filter="url(#grain)" opacity={grainA} /> : null}
        {cardsLayer}
        {els}
      </svg>
      {withAudio ? <Audio src={staticFile("audio/mix.wav")} trimBefore={Math.round(skip * FPS)} volume={(f) => (skip ? Math.min(1, f / 8) : 1)} /> : null}
    </AbsoluteFill>
  );
};
