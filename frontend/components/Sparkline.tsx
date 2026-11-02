type Point = { proxy: number; rejected: number };

export function Sparkline({ points }: { points: Point[] }) {
  const w = 640;
  const h = 140;
  if (points.length < 2) {
    return <p className="text-sm text-zinc-500">Waiting for SSE ticks…</p>;
  }
  const max = Math.max(1, ...points.flatMap((p) => [p.proxy, p.rejected]));
  const path = (key: keyof Point) =>
    points
      .map((p, i) => {
        const x = (i / (points.length - 1)) * w;
        const y = h - (p[key] / max) * (h - 24) - 12;
        return `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(" ");
  return (
    <div>
      <svg viewBox={`0 0 ${w} ${h}`} className="h-36 w-full">
        {[0.25, 0.5, 0.75].map((g) => (
          <line
            key={g}
            x1="0"
            x2={w}
            y1={12 + (1 - g) * (h - 24)}
            y2={12 + (1 - g) * (h - 24)}
            stroke="#27272a"
          />
        ))}
        <path d={path("proxy")} fill="none" stroke="#34d399" strokeWidth="2" />
        <path d={path("rejected")} fill="none" stroke="#f87171" strokeWidth="2" />
      </svg>
      <p className="text-xs text-zinc-500">scale 0–{max}</p>
    </div>
  );
}
