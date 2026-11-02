"use client";

import { api, role } from "@/lib/api";
import type { Route } from "@/lib/types";
import { FormEvent, useEffect, useState } from "react";

const emptyRoute = {
  routeKey: "",
  pathPattern: "/gw/",
  methods: "GET",
  rewritePrefix: "/api/",
  lbAlgorithm: "ROUND_ROBIN",
  canaryPercent: 0,
};

export default function RoutesPage() {
  const [routes, setRoutes] = useState<Route[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState(emptyRoute);
  const [targetUrl, setTargetUrl] = useState("http://localhost:8081");
  const canWrite = role() === "ADMIN" || role() === "OPERATOR";

  async function load() {
    setRoutes(await api<Route[]>("/api/v1/routes"));
  }

  useEffect(() => {
    load().catch((e) => setError(String(e)));
    const timer = setInterval(() => load().catch(() => undefined), 10000);
    return () => clearInterval(timer);
  }, []);

  async function patch(
      id: string,
      body: { enabled?: boolean; canaryPercent?: number; lbAlgorithm?: string },
  ) {
    setError(null);
    try {
      await api(`/api/v1/routes/${id}`, { method: "PATCH", body: JSON.stringify(body) });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "update failed");
    }
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api("/api/v1/routes", { method: "POST", body: JSON.stringify({ ...form, enabled: true }) });
      setForm(emptyRoute);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "create failed");
    }
  }

  return (
    <div>
      <h1 className="text-xl font-semibold">Routes</h1>
      <p className="mt-1 text-sm text-zinc-400">Create proxy paths, attach upstreams, watch live health</p>
      {error ? <p className="mt-2 text-sm text-red-400">{error}</p> : null}
      <div className="mt-6 space-y-4">
        {routes.map((r) => (
          <div key={r.id} className="fg-card">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-medium">
                  {r.routeKey}{" "}
                  <span className="text-zinc-500">
                    {r.methods} {r.pathPattern} → {r.rewritePrefix}
                  </span>
                </p>
                <p className="mt-1 text-xs text-zinc-500">
                  {r.lbAlgorithm} · canary {r.canaryPercent}%
                </p>
              </div>
              {canWrite ? (
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <select
                    className="fg-input py-1"
                    value={r.lbAlgorithm}
                    onChange={(e) => patch(r.id, { lbAlgorithm: e.target.value })}
                  >
                    <option>ROUND_ROBIN</option>
                    <option>WEIGHTED</option>
                    <option>RANDOM</option>
                  </select>
                  <label className="flex items-center gap-1 text-xs text-zinc-400">
                    canary
                    <input
                      key={`${r.id}-${r.canaryPercent}`}
                      type="range"
                      min={0}
                      max={100}
                      defaultValue={r.canaryPercent}
                      onPointerUp={(e) =>
                        patch(r.id, { canaryPercent: Number((e.target as HTMLInputElement).value) })
                      }
                    />
                    {r.canaryPercent}%
                  </label>
                  <button className="text-xs text-zinc-300" onClick={() => patch(r.id, { enabled: !r.enabled })}>
                    {r.enabled ? "Disable" : "Enable"}
                  </button>
                  <button
                    className="text-xs text-red-400"
                    onClick={async () => {
                      await api(`/api/v1/routes/${r.id}`, { method: "DELETE" });
                      await load();
                    }}
                  >
                    Delete route
                  </button>
                </div>
              ) : (
                <span className="text-xs text-zinc-500">{r.enabled ? "enabled" : "disabled"}</span>
              )}
            </div>
            <ul className="mt-3 space-y-1 text-sm">
              {r.targets.map((t) => (
                <li key={t.id} className="flex items-center justify-between gap-2">
                  <span>
                    <span className={t.healthy ? "text-emerald-400" : "text-red-400"}>
                      {t.healthy ? "up" : "down"}
                    </span>{" "}
                    {t.url} · w{t.weight}
                    {t.canary ? " · canary" : ""}
                  </span>
                  {canWrite ? (
                    <button
                      className="text-xs text-red-400"
                      onClick={async () => {
                        await api(`/api/v1/routes/${r.id}/targets/${t.id}`, { method: "DELETE" });
                        await load();
                      }}
                    >
                      Remove
                    </button>
                  ) : null}
                </li>
              ))}
            </ul>
            {canWrite ? (
              <form
                className="mt-3 flex flex-wrap gap-2"
                onSubmit={async (e) => {
                  e.preventDefault();
                  await api(`/api/v1/routes/${r.id}/targets`, {
                    method: "POST",
                    body: JSON.stringify({ url: targetUrl, weight: 1, canary: false }),
                  });
                  await load();
                }}
              >
                <input
                  className="fg-input flex-1"
                  value={targetUrl}
                  onChange={(e) => setTargetUrl(e.target.value)}
                  placeholder="http://localhost:8081"
                />
                <button className="fg-btn py-1">Add target</button>
              </form>
            ) : null}
          </div>
        ))}
      </div>
      {canWrite ? (
        <form onSubmit={onCreate} className="fg-card mt-8 grid max-w-xl grid-cols-2 gap-3">
          <p className="col-span-2 text-sm text-zinc-400">New route</p>
          <input
            className="fg-input"
            placeholder="routeKey"
            value={form.routeKey}
            onChange={(e) => setForm({ ...form, routeKey: e.target.value })}
          />
          <input
            className="fg-input"
            placeholder="GET"
            value={form.methods}
            onChange={(e) => setForm({ ...form, methods: e.target.value })}
          />
          <input
            className="fg-input col-span-2"
            placeholder="/gw/foo/**"
            value={form.pathPattern}
            onChange={(e) => setForm({ ...form, pathPattern: e.target.value })}
          />
          <input
            className="fg-input col-span-2"
            placeholder="/api/foo"
            value={form.rewritePrefix}
            onChange={(e) => setForm({ ...form, rewritePrefix: e.target.value })}
          />
          <button className="fg-btn col-span-2">Create route</button>
        </form>
      ) : null}
    </div>
  );
}
