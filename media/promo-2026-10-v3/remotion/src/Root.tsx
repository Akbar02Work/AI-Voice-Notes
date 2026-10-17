import React from "react";
import { Composition, Folder } from "remotion";
import { Film } from "./Film";
import { Sheet } from "./review/Sheet";
import type { SheetProps } from "./review/Sheet";
import { FPS, FRAMES, Format, Mode, SIZE } from "./theme";
import TL from "./timeline.json";

const skipOf = (f: Format) => (f === "9x16" ? TL.film.skip9x16 : 0);
const framesOf = (f: Format) => FRAMES - Math.round(skipOf(f) * FPS);

const formats: Format[] = ["16x9", "9x16"];
const modes: Mode[] = ["dark", "light"];
const cap = (m: Mode) => (m === "dark" ? "Dark" : "Light");

export const RemotionRoot: React.FC = () => (
  <>
    {formats.flatMap((f) =>
      modes.map((m) => (
        <Composition key={`F${f}${m}`} id={`Film${f}${cap(m)}`} component={Film} width={SIZE[f].W} height={SIZE[f].H} fps={FPS} durationInFrames={framesOf(f)} defaultProps={{ format: f, mode: m, withAudio: true, skip: skipOf(f) }} />
      )),
    )}
    <Folder name="Website">
      {modes.flatMap((m) => [
        <Composition key={`FW${m}`} id={`Film9x19${cap(m)}`} component={Film} width={1080} height={2280} fps={FPS} durationInFrames={framesOf("9x16")} defaultProps={{ format: "9x16", mode: m, withAudio: true, skip: skipOf("9x16") }} />,
        <Composition key={`SW${m}`} id={`Stills9x19${cap(m)}`} component={Film} width={1080} height={2280} fps={FPS} durationInFrames={framesOf("9x16")} defaultProps={{ format: "9x16", mode: m, withAudio: false, skip: skipOf("9x16") }} />,
      ])}
    </Folder>
    <Folder name="Review">
      {formats.flatMap((f) =>
        modes.map((m) => (
          <Composition key={`S${f}${m}`} id={`Stills${f}${cap(m)}`} component={Film} width={SIZE[f].W} height={SIZE[f].H} fps={FPS} durationInFrames={framesOf(f)} defaultProps={{ format: f, mode: m, withAudio: false, skip: skipOf(f) }} />
        )),
      )}
      <Composition id="Sheet" component={Sheet} width={2000} height={1400} fps={FPS} durationInFrames={1} defaultProps={{ title: "Sheet", cols: 4, thumbW: 455, aspect: 16 / 9, items: [] } as SheetProps} />
    </Folder>
  </>
);
