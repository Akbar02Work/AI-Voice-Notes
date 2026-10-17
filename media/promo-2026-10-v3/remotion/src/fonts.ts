import { loadFont } from "@remotion/fonts";
import { staticFile } from "remotion";

// Inter variable, Latin subset (OFL-1.1, @fontsource-variable 5.2.8), copied from the
// Lumingo media projects. Local file only: no network font, no system fallback for copy.
export const fontsReady = loadFont({
  family: "Inter",
  url: staticFile("fonts/inter-latin-wght-normal.woff2"),
  weight: "100 900",
});
