import { useEffect, useState } from "react";
import { continueRender, delayRender } from "remotion";
import { fontsReady } from "../fonts";
import { FONT } from "../theme";

// Advance widths from canvas once the local font is loaded (deterministic: a pure function
// of the font file). Every piece of copy is laid out from these numbers, so a word that
// travels from the spoken sentence into the note starts exactly where it was drawn.

let ctx: CanvasRenderingContext2D | null = null;
const cache = new Map<string, number>();

/** Advance width of `text` in em at `weight`. */
export const em = (text: string, weight: number) => {
  const key = `${weight}|${text}`;
  const hit = cache.get(key);
  if (hit !== undefined) return hit;
  if (!ctx) ctx = document.createElement("canvas").getContext("2d")!;
  ctx.font = `${weight} 1000px ${FONT}`;
  const w = ctx.measureText(text).width / 1000;
  cache.set(key, w);
  return w;
};

export const useFontsReady = () => {
  const [ready, setReady] = useState(false);
  const [handle] = useState(() => delayRender("fonts"));
  useEffect(() => {
    fontsReady.then(() => {
      setReady(true);
      continueRender(handle);
    });
  }, [handle]);
  return ready;
};
