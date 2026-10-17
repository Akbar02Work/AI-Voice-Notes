# AI Voice Notes — "Say it messy" promo, v3 (2026-10)

## Website export (9:19)

`./render.sh website` renders additional dark/light 1080×2280 films and posters for
the portfolio's 9:19 cards. The original 9:16 foreground is centred on the taller
canvas without scaling or cropping; radial palette sweeps and grain cover the full
canvas. Duration and audio match the 22.4 s vertical cut. Poster: 8.3 s in the cut
(9.4 s film time), the tangled sentence under "AI cleans it up."

The 16:9 and 9:16 exports keep their original dimensions and layout.

v3 = v2 colours + the v1 voice line (tangled knots, round swells), one line only, no echo strands. Also: motion blur
(180° shutter, exact sub-frame average) on falling fillers, bars flying to the icon and the mint
snap; "Cloud AI or fully offline" ring arrives as a flowing dashed signal and closes into the
solid icon ring at 16.95 s; 9:16 cut starts 1.1 s in, on the voice (22.4 s, no "Just talk.");
colour sweeps on a 0.7 s pulse (17.95 / 18.65 / 19.35) with a slower 1.05 s return home at
20.05, chords and swells regenerated on the same cues. Word sync is still envelope-based.

---

(v2 notes follow)

# v2

Clone of `../promo-2026-10` (v1 kept for comparison). v2 changes: one rope-like voice line with
self-crossing loops (no echo strands), flat backgrounds at rest, narrow soft colour fronts with
per-frame grain only while they move, H.264 at CRF 10 and ProRes 422 HQ masters (`out/master/`).
Chrome renders 8-bit frames, so the 10-bit ProRes container avoids re-compression loss but adds
no extra gradient precision; banding is solved in the picture itself (flat colours).

23.5 s motion film, English, no product UI. A messy spoken sentence (the author's own
ElevenLabs voice clone) is cleaned into a note: fillers fall, the tangled voice line pulls
straight and becomes the five waveform bars, the note joins a searchable list, the bars get
the icon ring, the world recolours through four palettes and the mint arc snaps the icon shut.

## Deliverables (`out/`, git-ignored)

| File | What |
| --- | --- |
| `ai-voice-notes-{dark,light}-16x9.mp4` | 1920×1080, 60 fps, 1410 frames, H.264 + AAC 48 kHz, 6–8 MB (under GitHub's 10 MB video upload limit) |
| `ai-voice-notes-{dark,light}-9x16.mp4` | 1080×1920, separately laid out (not a crop) |
| `…-poster.png` | 11.80 s: the finished note under "AI cleans it up." |
| `review/Sheet-*.png` | 16 frames per film, decoded from the encoded MP4s |

Rebuild: `./render.sh all` (typecheck → audio → films → posters → sheets → measurements).
`./render.sh film dark`, `stills`, `proof 8.6 12.2`; `tools/teststills.sh` for quick review sheets.

## Script

| s | Copy | Picture |
| --- | --- | --- |
| 0–1.5 | Just talk. | a thin line breathes |
| 1.3–8.2 | *So, um… I need to… uh… get eggs. And bread… oh, and milk.* (voice) | words appear on the voice's word onsets; the line moves with the voice envelope and ties a knot under every hesitation |
| 9.05–12.5 | AI cleans it up. | fillers fall, knots pull straight, helper words fade, kept words glide into "Eggs, bread and milk."; the line becomes the bars; "Groceries" is written letter by letter |
| 13.0–15.5 | Find any thought. | the note joins a list, "milk" is typed, the Groceries card lifts with the word highlighted |
| 15.9–17.6 | Cloud AI or fully offline. | the bars fly to centre, the icon ring draws |
| 18.0–20.45 | In your colors. | radial palette sweeps: brand → blue → amber → violet → brand |
| 20.85–23.5 | AI Voice Notes | mint arc snaps in, lockup holds |

Example notes (Groceries, Project idea, Call notes, Weekend plans) are illustrations, not
product claims. "Works fully offline" relies on the English offline model the author is adding;
publish after it ships.

## How it is built

- Remotion 4.0.534, Node, chrome-headless-shell and bundled ffmpeg reused read-only from
  `Lumingo/50-media/lumingo-beta-launch-2026-10-08` (`remotion/node_modules` is a symlink).
  Nothing installed. Inter (OFL-1.1) local woff2.
- `remotion/src/timeline.json` drives picture and sound. `layout.ts` measures text so words keep
  their place between sentence, note and card. `Film.tsx` is a pure function of time.
- Voice: `audio/src/voice-take-a.mp3` (the second delivered take, `-b`, is kept but unused).
  Word onsets come from an RMS envelope analysis (`tools/envelope.py`), not from listening.
  `voiceEnv.json` (60 fps envelope) animates the line.
- Voice line: envelope smoothed like a VU meter (70 ms attack / 300 ms release) and a phase
  that runs faster while the voice is loud; only low spatial frequencies (round swells), a swell
  that travels with the current word, two faint echo ribbons that open with loudness.
- Colour worlds: one radial gradient per frame (40 stops, 340 px soft front) instead of
  hard-edged discs; neutral glow; a static fine grain dithers the gradients against banding.
- Sound: `audio/generate_audio.py`, standard-library Python, original synthesis (pad, plinks,
  risers, bells, key taps, snap); voice compressed 3:1, music ~6 dB under it, look-ahead limiter.

## Verification (final outputs)

- ffprobe: all four 23.5 s, 1410 frames, 60 fps, AAC 23.51 s.
- Loudness of encoded AAC: −16.3 LUFS integrated, −1.4 dBTP, LRA 4.4.
- `tools/frame_stats.py`: 0 blank frames, 0 frame-to-frame mean-luminance jumps > 4/255;
  luminance band dark 28–36/255, light 241–247/255 (palette sweeps cause no flashes).
- Decoded-frame review of all four films; transition strips of the clean-up beat checked
  frame by frame (no word collisions, line never crosses copy).

## Revisions

1. (user, 2026-10-17) Voice wave smoother, rounder and more responsive to the voice;
   halos/rings in the colour sweeps removed. All four films, posters and sheets re-rendered.

## Known limitations

- Audio was measured, not listened to. Word timing is from the envelope; small (~0.1 s) offsets
  between a word appearing and being heard are possible, especially "and milk".
- No motion blur; fast moves rely on easing only.
