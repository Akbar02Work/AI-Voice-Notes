import { em } from "./lib/measure";
import { Format, ICON, SIZE } from "./theme";
import TL from "./timeline.json";

// All positions for one format, derived from measured text so words keep their place
// when they travel between the spoken sentence, the note and the card.

export type Word = (typeof TL.words)[number] & { kind: "filler" | "helper" | "keep"; to?: string; knot?: number };
export const WORDS = TL.words as Word[];

export type Glyph = { cx: number; cy: number; g: number };
export type TextBox = { x: number; y: number; size: number }; // left x, baseline y

export const W_SPEECH = 600;
export const W_TITLE = 700;
export const W_SUM = 500;
export const W_CAP = 700;

const spec = {
  "16x9": {
    speech: { size: 84, maxW: 1480, cy: 470 },
    justTalk: { size: 136, y: 520 },
    line: { x0: 260, x1: 1660, y: 800, amp: 46 },
    caption: { size: 76, y: 200 },
    note: { title: 128, sum: 66, g: 0.6, gap: 56, titleY: 500, sumY: 598, glyphY: 508 },
    cards: { x: 510, w: 900, h: 132, gap: 22, top: 372, g: 0.3, tx: 140, ty: 58, ts: 44, sy: 102, ss: 30 },
    pill: { cy: 290, w: 560, h: 80, size: 36 },
    icon: { cx: 960, cy: 612, s: 0.62 },
    end: { s: 0.5, word: 124, gap: 64, cy: 540 },
  },
  "9x16": {
    speech: { size: 84, maxW: 960, cy: 860 },
    justTalk: { size: 124, y: 900 },
    line: { x0: 100, x1: 980, y: 1290, amp: 40 },
    caption: { size: 78, y: 440 },
    note: { title: 112, sum: 58, g: 0.5, gap: 44, titleY: 900, sumY: 988, glyphY: 908 },
    cards: { x: 70, w: 940, h: 186, gap: 26, top: 760, g: 0.38, tx: 172, ty: 80, ts: 54, sy: 138, ss: 37 },
    pill: { cy: 646, w: 680, h: 96, size: 42 },
    icon: { cx: 540, cy: 1010, s: 0.86 },
    end: { s: 0.62, word: 104, gap: 0, cy: 860 },
  },
} as const;

export const CARDS = [
  { title: "Project idea", sum: "Turn voice memos into a weekly plan." },
  { title: "Call notes", sum: "Send the draft to Anna by Friday." },
  null, // Groceries — the note made in this film
  { title: "Weekend plans", sum: "Hike in the morning, dinner at seven." },
];
export const GROCERY_SLOT = 2;
export const TITLE = "Groceries";
export const SUMMARY = "Eggs, bread and milk.";
export const WORDMARK = "AI Voice Notes";

export const glyphHalfW = (g: number) => (ICON.bars[4].x + ICON.barW / 2) * g;

export const buildLayout = (f: Format) => {
  const S = spec[f];
  const { W } = SIZE[f];
  const cx = W / 2;

  // --- spoken sentence: greedy wrap, centred lines
  const sz = S.speech.size;
  const sp = em(" ", W_SPEECH) * sz;
  const lines: number[][] = [[]];
  let lw = 0;
  WORDS.forEach((w, i) => {
    const ww = em(w.text, W_SPEECH) * sz;
    const cur = lines[lines.length - 1];
    if (cur.length && lw + sp + ww > S.speech.maxW) {
      lines.push([i]);
      lw = ww;
    } else {
      cur.push(i);
      lw += (cur.length > 1 ? sp : 0) + ww;
    }
  });
  const lh = sz * 1.3;
  const speech: TextBox[] = [];
  lines.forEach((ln, li) => {
    const text = ln.map((i) => WORDS[i].text).join(" ");
    const x0 = cx - (em(text, W_SPEECH) * sz) / 2;
    const y = S.speech.cy - ((lines.length - 1) * lh) / 2 + sz * 0.36 + li * lh;
    ln.forEach((i, k) => {
      const prefix = ln.slice(0, k).map((j) => WORDS[j].text).join(" ");
      speech[i] = { x: x0 + (k ? em(prefix + " ", W_SPEECH) * sz : 0), y, size: sz };
    });
  });

  // --- the note: glyph | title over summary, centred as a block
  const N = S.note;
  const titleW = em(TITLE, W_TITLE) * N.title;
  const sumW = em(SUMMARY, W_SUM) * N.sum;
  const gW = glyphHalfW(N.g) * 2;
  const blockW = gW + N.gap + Math.max(titleW, sumW);
  const bx = cx - blockW / 2;
  const noteGlyph: Glyph = { cx: bx + gW / 2, cy: N.glyphY, g: N.g };
  const noteTitle: TextBox = { x: bx + gW + N.gap, y: N.titleY, size: N.title };
  const noteSum: TextBox = { x: noteTitle.x, y: N.sumY, size: N.sum };

  // summary tokens (kept words) as offsets in em from the summary origin
  const keepTo = WORDS.filter((w) => w.kind === "keep").map((w) => w.to!);
  const sumTok: Record<number, number> = {};
  let k = 0;
  WORDS.forEach((w, i) => {
    if (w.kind !== "keep") return;
    const prefix = keepTo.slice(0, k).join(" ");
    sumTok[i] = k ? em(prefix + " ", W_SUM) : 0;
    k++;
  });

  // --- cards
  const C = S.cards;
  const card = (slot: number) => {
    const top = C.top + slot * (C.h + C.gap);
    return {
      x: C.x, y: top, w: C.w, h: C.h,
      glyph: { cx: C.x + C.tx / 2 + 4, cy: top + C.h / 2, g: C.g } as Glyph,
      title: { x: C.x + C.tx, y: top + C.ty, size: C.ts } as TextBox,
      sum: { x: C.x + C.tx, y: top + C.sy, size: C.ss } as TextBox,
    };
  };

  // --- end lockup
  const E = S.end;
  const outer = (ICON.R + ICON.stroke / 2) * E.s;
  let endIcon: { cx: number; cy: number; s: number };
  let endWord: TextBox;
  const wmW = em(WORDMARK, W_TITLE) * E.word;
  if (f === "16x9") {
    const total = outer * 2 + E.gap + wmW;
    const x0 = cx - total / 2;
    endIcon = { cx: x0 + outer, cy: E.cy, s: E.s };
    endWord = { x: x0 + outer * 2 + E.gap, y: E.cy + E.word * 0.36, size: E.word };
  } else {
    endIcon = { cx, cy: E.cy, s: E.s };
    endWord = { x: cx - wmW / 2, y: E.cy + outer + 110 + E.word * 0.36, size: E.word };
  }

  return {
    f, S, speech, noteGlyph, noteTitle, noteSum, sumTok, card,
    icon: S.icon, endIcon, endWord, wmW,
  };
};
export type Layout = ReturnType<typeof buildLayout>;
