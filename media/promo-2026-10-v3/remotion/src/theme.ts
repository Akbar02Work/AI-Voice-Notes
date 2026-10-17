// Formats, colour worlds and the icon geometry the film resolves into.

export const FPS = 60;
export const DURATION = 23.5;
export const FRAMES = Math.round(DURATION * FPS);

export type Format = "16x9" | "9x16";
export type Mode = "dark" | "light";
export const SIZE: Record<Format, { W: number; H: number }> = {
  "16x9": { W: 1920, H: 1080 },
  "9x16": { W: 1080, H: 1920 },
};

export type RGB = [number, number, number];
export type Pal = {
  bg: RGB; // page
  glow: RGB; // soft radial light behind the action
  ink: RGB; // copy
  surface: RGB; // note cards
  ring: RGB; // the icon ring, the voice line, the bars
  mint: RGB; // the icon's accent arc
  text: RGB; // accented copy (readable on bg)
};

const hex = (h: string): RGB => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const pal = (bg: string, glow: string, ink: string, surface: string, ring: string, mint: string, text: string): Pal => ({
  bg: hex(bg), glow: hex(glow), ink: hex(ink), surface: hex(surface), ring: hex(ring), mint: hex(mint), text: hex(text),
});

// Brand = the launcher icon (artwork/icons/android-app-icon.svg): #081E23 field, #12D4DA ring,
// #62F2C8 accent arc, #2FD9DC bars. The other three are dynamic-colour style worlds shown
// in "In your colors"; within a mode their backgrounds stay in a narrow luminance band.
export const WORLDS: Record<Mode, Pal[]> = {
  dark: [
    pal("#071B1F", "#10343C", "#E6F3F3", "#0F2B31", "#12D4DA", "#62F2C8", "#5FE3E6"),
    pal("#0B1222", "#1A2A4C", "#E6ECFA", "#16213A", "#8DB3FF", "#C2D6FF", "#A9C4FF"),
    pal("#1B1309", "#3A2A12", "#FAF0E2", "#2B2012", "#FFB65C", "#FFDDA6", "#FFC27A"),
    pal("#160F1E", "#33204A", "#F3EAFA", "#251A31", "#D7A4FF", "#F2CCFF", "#DDB2FF"),
  ],
  light: [
    pal("#EDF5F5", "#FFFFFF", "#0A1F23", "#FFFFFF", "#0CB9C0", "#2FD3A6", "#06818A"),
    pal("#EEF2FB", "#FFFFFF", "#0E1A33", "#FFFFFF", "#3B6FE0", "#86A8F2", "#2F5FD0"),
    pal("#FBF3E9", "#FFFFFF", "#2A1B08", "#FFFFFF", "#D9831A", "#F2B868", "#A8620C"),
    pal("#F7F0FB", "#FFFFFF", "#241032", "#FFFFFF", "#9A4FD0", "#CB98F0", "#8A3FC0"),
  ],
};

export const mix = (a: RGB, b: RGB, k: number): RGB => [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k];
export const rgba = (c: RGB, a = 1) => `rgba(${Math.round(c[0])},${Math.round(c[1])},${Math.round(c[2])},${a.toFixed(3)})`;
export const mixPal = (a: Pal, b: Pal, k: number): Pal => {
  const o = {} as Pal;
  (Object.keys(a) as (keyof Pal)[]).forEach((key) => (o[key] = mix(a[key], b[key], k)));
  return o;
};

// Icon in its own 1024 units (centre 512,512): ring R 289, stroke 46; main arc from -20° to 288°
// clockwise (screen angles, y down), accent arc -56° → -39°; bars every ~70.5 units.
export const ICON = {
  R: 289,
  stroke: 46,
  main: [-19.99, 288.0] as const,
  accent: [-55.98, -39.0] as const,
  bars: [
    { x: -141, h: 66 },
    { x: -70, h: 134 },
    { x: 0, h: 248 },
    { x: 70, h: 134 },
    { x: 141, h: 66 },
  ],
  barW: 36,
};

export const FONT = "Inter";
