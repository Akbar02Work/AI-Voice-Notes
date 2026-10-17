import React from "react";
import { AbsoluteFill, Img, staticFile } from "remotion";

// Contact sheet of pre-rendered frames (public/sheet/*.png) with time labels.
export type SheetProps = { title: string; cols: number; thumbW: number; aspect: number; items: { file: string; label: string }[] };

export const Sheet: React.FC<SheetProps> = ({ title, cols, thumbW, aspect, items }) => (
  <AbsoluteFill style={{ background: "#D9E2E3", padding: 40, fontFamily: "Inter" }}>
    <div style={{ fontSize: 34, fontWeight: 700, color: "#0A1F23", marginBottom: 24 }}>{title}</div>
    <div style={{ display: "grid", gridTemplateColumns: `repeat(${cols}, ${thumbW}px)`, gap: 22 }}>
      {items.map((it) => (
        <div key={it.file}>
          <Img src={staticFile(`sheet/${it.file}`)} style={{ width: thumbW, height: thumbW / aspect, display: "block", borderRadius: 8, boxShadow: "0 4px 16px rgba(0,0,0,0.18)" }} />
          <div style={{ fontSize: 24, fontWeight: 600, color: "#2A3B3E", marginTop: 8 }}>{it.label}</div>
        </div>
      ))}
    </div>
  </AbsoluteFill>
);
