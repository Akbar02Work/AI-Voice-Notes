# AI Voice Notes promo source

Source for the published October 2026 v3 product film. This directory includes
the Remotion compositions, timeline, original synthesized score, the used voice
take, and the local Inter font with its OFL license.

## Setup

Use Node.js 22 or newer, npm, and Python 3.9 or newer. From this directory:

```bash
cd remotion
npm ci
cd ..
./render.sh typecheck
./render.sh audio
./render.sh website
```

Remotion uses its managed browser by default. To reuse an installed compatible
Chromium, set `BROWSER_EXECUTABLE` to its executable path. `NODE_BIN_DIR` can
optionally prepend a Node runtime to `PATH`; no other project checkout is required.

The audio command regenerates `remotion/public/audio/mix.wav` from the committed
voice WAV and the Python score. Run it before rendering films in a fresh clone.
No speech API credentials or network font downloads are needed.

## Render targets

- `./render.sh website` — dark/light 1080 × 2280 (9:19) films and posters for the website.
- `./render.sh film` — dark/light 1920 × 1080 and 1080 × 1920 social films.
- `./render.sh posters` — social posters at 11.8 seconds of film time.
- `./render.sh master` — ProRes masters.
- `./render.sh sheets` — contact sheets decoded from the social films.
- `./render.sh measure` — video and audio checks.

Pass `dark` or `light` as the second argument to render one theme.
Website and 9:16 films skip the first 1.1 seconds; the website poster is at
8.3 seconds of that cut. The website canvas extends the background while preserving
the original 9:16 foreground composition.

Contact sheets and measurements need `ffmpeg` and `ffprobe` on `PATH`, or
`FFMPEG_DIR` pointing to a compatible bundled directory. On macOS ARM64 the tools
also detect Remotion's bundled binaries automatically.

`out/`, generated audio, caches, dependencies, and the unused alternate voice take
are excluded from Git. The used voice take is intentionally included so a fresh
clone can regenerate the soundtrack. Existing exports are not changed by saving
these sources. `NOTES.md` contains historical design notes and measurements,
including superseded descriptions; this README describes the current workflow.

The Inter font license is included in `remotion/public/fonts/OFL-Inter.txt`.
