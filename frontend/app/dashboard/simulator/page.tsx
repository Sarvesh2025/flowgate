"use client";

import { api, role } from "@/lib/api";
import type { Route } from "@/lib/types";
import { useEffect, useState } from "react";

type Hit = { status: number; remaining: string; route: string; cache: string };
type Chaos = { delayMs: number; errorRate: number; forceDown: boolean };

async function fire(target: string): Promise<Hit> {
  const res = await fetch(target, { cache: "no-store" });
  return {
    status: res.status,
    remaining: res.headers.get("x-ratelimit-remaining") || "—",
    route: res.headers.get("x-flowgate-route") || target,
    cache: res.headers.get("x-flowgate-cache") || "—",
  };
}

function probePath(pattern: string) {
  return pattern.replace("/**", "").replace("/*", "") || "/";
}

export default function SimulatorPage() {
  const [count, setCount] = useState(15);
  const [target, setTarget] = useState("/api/v1/limited");
  const [parallel, setParallel] = useState(false);
  const [hits, setHits] = useState<Hit[]>([]);
  const [busy, setBusy] = useState(false);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [orders, setOrders] = useState<Chaos | null>(null);
  const [payments, setPayments] = useState<Chaos | null>(null);
  const [error, setError] = useState<string | null>(null);
  const canWrite = role() === "ADMIN" || role() === "OPERATOR";

  async function loadChaos() {
    try {
      const [o, p] = await Promise.all([
        api<Chaos>("/api/v1/chaos/orders"),
        api<Chaos>("/api/v1/chaos/payments"),
      ]);
      setOrders(o);
      setPayments(p);
    } catch {
      setOrders(null);
      setPayments(null);
    }
  }

  useEffect(() => {
    api<Route[]>("/api/v1/routes").then(setRoutes).catch(() => undefined);
    loadChaos();
  }, []);

  async function run() {
    setBusy(true);
    setHits([]);
    if (parallel) {
      setHits(await Promise.all(Array.from({ length: count }, () => fire(target))));
    } else {
      const next: Hit[] = [];
      for (let i = 0; i < count; i++) {
        next.push(await fire(target));
        setHits([...next]);
      }
    }
    setBusy(false);
  }

  async function setChaos(service: "orders" | "payments", body: Partial<Chaos>) {
    setError(null);
    try {
      const current = service === "orders" ? orders : payments;
      const next = await api<Chaos>(`/api/v1/chaos/${service}`, {
        method: "PUT",
        body: JSON.stringify({
          delayMs: body.delayMs ?? current?.delayMs ?? 0,
          errorRate: body.errorRate ?? current?.errorRate ?? 0,
          forceDown: body.forceDown ?? current?.forceDown ?? false,
        }),
      });
      if (service === "orders") {
        setOrders(next);
      } else {
        setPayments(next);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "chaos failed");
    }
  }

  const ok = hits.filter((h) => h.status < 400).length;
  const limited = hits.filter((h) => h.status === 429).length;
  const failed = hits.filter((h) => h.status >= 500).length;

  return (
    <div>
      <h1 className="text-xl font-semibold">Simulator</h1>
      <p className="mt-1 text-sm text-zinc-400">Burst traffic through the gateway, then trip upstream chaos</p>
      {error ? <p className="mt-2 text-sm text-red-400">{error}</p> : null}
      <div className="mt-6 flex flex-wrap items-end gap-3">
        <label className="text-sm">
          Path
          <select className="fg-input mt-1 block" value={target} onChange={(e) => setTarget(e.target.value)}>
            <option value="/api/v1/limited">/api/v1/limited</option>
            {routes.map((r) => (
              <option key={r.id} value={probePath(r.pathPattern)}>
                {probePath(r.pathPattern)} ({r.routeKey})
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          Count
          <input
            type="number"
            min={1}
            max={100}
            className="fg-input mt-1 block w-24"
            value={count}
            onChange={(e) => setCount(Number(e.target.value))}
          />
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={parallel} onChange={(e) => setParallel(e.target.checked)} />
          Parallel
        </label>
        <button disabled={busy} onClick={run} className="fg-btn">
          {busy ? "Running…" : "Run"}
        </button>
      </div>
      {hits.length ? (
        <p className="mt-4 text-sm text-zinc-400">
          {ok} ok · {limited} × 429 · {failed} × 5xx
        </p>
      ) : null}
      <ol className="mt-4 max-h-64 overflow-auto font-mono text-xs">
        {hits.map((h, i) => (
          <li
            key={i}
            className={h.status === 429 ? "text-red-400" : h.status >= 500 ? "text-amber-400" : "text-emerald-400"}
          >
            {i + 1}. {h.status} remaining={h.remaining} cache={h.cache} {h.route}
          </li>
        ))}
      </ol>

      <h2 className="mt-10 text-lg font-medium">Chaos</h2>
      <p className="text-sm text-zinc-500">Needs orders (:8081) and payments (:8082) running</p>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <ChaosCard name="orders" value={orders} canWrite={canWrite} onChange={(b) => setChaos("orders", b)} />
        <ChaosCard name="payments" value={payments} canWrite={canWrite} onChange={(b) => setChaos("payments", b)} />
      </div>
    </div>
  );
}

function ChaosCard({
  name,
  value,
  canWrite,
  onChange,
}: {
  name: string;
  value: Chaos | null;
  canWrite: boolean;
  onChange: (body: Partial<Chaos>) => void;
}) {
  return (
    <div className="fg-card">
      <p className="font-medium capitalize">{name}</p>
      {value ? (
        <p className="mt-1 text-xs text-zinc-500">
          delay {value.delayMs}ms · errors {(value.errorRate * 100).toFixed(0)}% ·{" "}
          {value.forceDown ? "forced down" : "up"}
        </p>
      ) : (
        <p className="mt-1 text-xs text-zinc-500">unreachable</p>
      )}
      {canWrite && value ? (
        <div className="mt-3 flex flex-wrap gap-2">
          <button className="fg-btn py-1 text-xs" onClick={() => onChange({ forceDown: !value.forceDown })}>
            {value.forceDown ? "Recover" : "Force down"}
          </button>
          <button className="fg-btn py-1 text-xs" onClick={() => onChange({ delayMs: value.delayMs ? 0 : 1500 })}>
            {value.delayMs ? "Clear delay" : "Delay 1.5s"}
          </button>
          <button className="fg-btn py-1 text-xs" onClick={() => onChange({ errorRate: value.errorRate ? 0 : 1 })}>
            {value.errorRate ? "Clear errors" : "100% 5xx"}
          </button>
          <button
            className="text-xs text-zinc-400"
            onClick={() => onChange({ delayMs: 0, errorRate: 0, forceDown: false })}
          >
            Reset
          </button>
        </div>
      ) : null}
    </div>
  );
}
