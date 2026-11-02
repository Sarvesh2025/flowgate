"use client";

import { Sparkline } from "@/components/Sparkline";
import { api } from "@/lib/api";
import type { MetricSample, Route } from "@/lib/types";
import { useEffect, useState } from "react";

type Health = { status?: string };

export default function OverviewPage() {
  const [points, setPoints] = useState<{ proxy: number; rejected: number }[]>([]);
  const [latest, setLatest] = useState<MetricSample | null>(null);
  const [status, setStatus] = useState("connecting");
  const [health, setHealth] = useState<string>("…");
  const [routesUp, setRoutesUp] = useState<string>("…");

  useEffect(() => {
    api<Health>("/actuator/health")
      .then((h) => setHealth(h.status || "UNKNOWN"))
      .catch(() => setHealth("DOWN"));
    api<Route[]>("/api/v1/routes")
      .then((rows) => {
        const targets = rows.flatMap((r) => r.targets);
        const up = targets.filter((t) => t.healthy).length;
        setRoutesUp(`${up}/${targets.length} targets up`);
      })
      .catch(() => setRoutesUp("routes unavailable"));

    const es = new EventSource("/api/v1/metrics/stream");
    es.addEventListener("tick", (ev) => {
      const sample = JSON.parse((ev as MessageEvent).data) as MetricSample;
      setLatest(sample);
      setPoints((prev) => [...prev.slice(-59), { proxy: sample.proxy, rejected: sample.rejected }]);
      setStatus("live");
    });
    es.addEventListener("snapshot", (ev) => {
      const samples = JSON.parse((ev as MessageEvent).data) as MetricSample[];
      setPoints(samples.slice(-60).map((s) => ({ proxy: s.proxy, rejected: s.rejected })));
      if (samples.length) {
        setLatest(samples[samples.length - 1]);
      }
      setStatus("live");
    });
    es.onerror = () => setStatus("disconnected");
    return () => es.close();
  }, []);

  return (
    <div>
      <h1 className="text-xl font-semibold">Overview</h1>
      <p className="mt-1 text-sm text-zinc-400">Live SSE · {status}</p>
      <div className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <div className="fg-card">
          <p className="text-xs uppercase text-zinc-500">Gateway</p>
          <p className={`mt-1 text-2xl ${health === "UP" ? "text-emerald-400" : "text-red-300"}`}>{health}</p>
        </div>
        <div className="fg-card">
          <p className="text-xs uppercase text-zinc-500">Upstreams</p>
          <p className="mt-1 text-2xl">{routesUp}</p>
        </div>
        <div className="fg-card">
          <p className="text-xs uppercase text-zinc-500">Proxy calls</p>
          <p className="mt-1 text-2xl">{latest?.proxy ?? 0}</p>
        </div>
        <div className="fg-card">
          <p className="text-xs uppercase text-zinc-500">Rate-limit rejects</p>
          <p className="mt-1 text-2xl text-red-300">{latest?.rejected ?? 0}</p>
        </div>
      </div>
      <div className="fg-card mt-6">
        <p className="mb-2 text-xs text-zinc-500">Green = proxy · Red = rejects (cumulative)</p>
        <Sparkline points={points} />
      </div>
    </div>
  );
}
