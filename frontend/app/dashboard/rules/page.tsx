"use client";

import { api, role } from "@/lib/api";
import { ALGORITHMS, type Route, type Rule } from "@/lib/types";
import { FormEvent, useEffect, useState } from "react";

const emptyForm = {
  name: "burst",
  algorithm: "SLIDING_WINDOW_COUNTER",
  limitCount: 10,
  windowMs: 1000,
  priority: 0,
  scope: "GLOBAL" as string,
  routeId: "" as string,
  enabled: true,
};

export default function RulesPage() {
  const [rules, setRules] = useState<Rule[]>([]);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [testHits, setTestHits] = useState<string[]>([]);
  const [testing, setTesting] = useState(false);
  const canWrite = role() === "ADMIN" || role() === "OPERATOR";

  async function load() {
    const [ruleRows, routeRows] = await Promise.all([
      api<Rule[]>("/api/v1/rules"),
      api<Route[]>("/api/v1/routes"),
    ]);
    setRules(ruleRows);
    setRoutes(routeRows);
  }

  useEffect(() => {
    load().catch((e) => setError(String(e)));
  }, []);

  function probeFor(rule: Rule) {
    if (!rule.routeId) {
      return "/api/v1/limited";
    }
    const route = routes.find((r) => r.id === rule.routeId);
    return route ? route.pathPattern.replace("/**", "").replace("/*", "") : "/api/v1/limited";
  }

  async function testRule(rule: Rule) {
    setTesting(true);
    const path = probeFor(rule);
    const lines: string[] = [];
    const n = Math.max(rule.limitCount + 5, 8);
    for (let i = 0; i < n; i++) {
      const res = await fetch(path, { cache: "no-store" });
      lines.push(
        `${i + 1}. ${res.status} remaining=${res.headers.get("x-ratelimit-remaining") ?? "—"} ${path}`,
      );
    }
    setTestHits(lines);
    setTesting(false);
  }

  function routeName(id: string | null) {
    if (!id) {
      return "global /limited";
    }
    return routes.find((r) => r.id === id)?.routeKey || id.slice(0, 8);
  }

  function payload() {
    return {
      name: form.name,
      algorithm: form.algorithm,
      limitCount: form.limitCount,
      windowMs: form.windowMs,
      priority: form.priority,
      scope: form.routeId ? "ROUTE" : "GLOBAL",
      routeId: form.routeId || null,
      enabled: form.enabled,
      redisDownPolicy: "FAIL_CLOSED",
    };
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      if (editing) {
        await api(`/api/v1/rules/${editing}`, { method: "PUT", body: JSON.stringify(payload()) });
      } else {
        await api("/api/v1/rules", { method: "POST", body: JSON.stringify(payload()) });
      }
      setEditing(null);
      setForm(emptyForm);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "save failed");
    }
  }

  return (
    <div>
      <h1 className="text-xl font-semibold">Rate-limit rules</h1>
      <p className="mt-1 text-sm text-zinc-400">
        Attach a rule to a proxy route (Ant pattern) or leave global for /api/v1/limited
      </p>
      {error ? <p className="mt-2 text-sm text-red-400">{error}</p> : null}
      <table className="mt-6 w-full text-left text-sm">
        <thead className="text-zinc-500">
          <tr>
            <th className="py-2">Name</th>
            <th>Applies to</th>
            <th>Algorithm</th>
            <th>Limit</th>
            <th>On</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rules.map((r) => (
            <tr key={r.id} className="border-t border-zinc-800">
              <td className="py-2">{r.name}</td>
              <td>{routeName(r.routeId)}</td>
              <td>{r.algorithm}</td>
              <td>
                {r.limitCount} / {r.windowMs}ms
              </td>
              <td>{r.enabled ? "yes" : "no"}</td>
              <td className="space-x-3">
                <button className="text-emerald-400" disabled={testing} onClick={() => testRule(r)}>
                  Test
                </button>
              {canWrite ? (
                <>
                  <button
                    className="text-zinc-300"
                    onClick={() => {
                      setEditing(r.id);
                      setForm({
                        name: r.name,
                        algorithm: r.algorithm,
                        limitCount: r.limitCount,
                        windowMs: r.windowMs,
                        priority: r.priority,
                        scope: r.scope,
                        routeId: r.routeId || "",
                        enabled: r.enabled,
                      });
                    }}
                  >
                    Edit
                  </button>
                  <button
                    className="text-red-400"
                    onClick={async () => {
                      await api(`/api/v1/rules/${r.id}`, { method: "DELETE" });
                      await load();
                    }}
                  >
                    Delete
                  </button>
                </>
              ) : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {canWrite ? (
        <form onSubmit={onSubmit} className="mt-8 grid max-w-xl grid-cols-2 gap-3">
          <p className="col-span-2 text-sm text-zinc-400">{editing ? "Edit rule" : "New rule"}</p>
          <input
            className="fg-input"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="name"
          />
          <select
            className="fg-input"
            value={form.algorithm}
            onChange={(e) => setForm({ ...form, algorithm: e.target.value })}
          >
            {ALGORITHMS.map((a) => (
              <option key={a}>{a}</option>
            ))}
          </select>
          <input
            type="number"
            className="fg-input"
            value={form.limitCount}
            onChange={(e) => setForm({ ...form, limitCount: Number(e.target.value) })}
          />
          <input
            type="number"
            className="fg-input"
            value={form.windowMs}
            onChange={(e) => setForm({ ...form, windowMs: Number(e.target.value) })}
          />
          <select
            className="fg-input col-span-2"
            value={form.routeId}
            onChange={(e) => setForm({ ...form, routeId: e.target.value })}
          >
            <option value="">Global (/api/v1/limited)</option>
            {routes.map((r) => (
              <option key={r.id} value={r.id}>
                {r.routeKey} ({r.pathPattern})
              </option>
            ))}
          </select>
          <label className="col-span-2 flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
            />
            Enabled
          </label>
          <button className="fg-btn col-span-2">{editing ? "Save" : "Create rule"}</button>
          {editing ? (
            <button
              type="button"
              className="col-span-2 text-sm text-zinc-500"
              onClick={() => {
                setEditing(null);
                setForm(emptyForm);
              }}
            >
              Cancel
            </button>
          ) : null}
        </form>
      ) : (
        <p className="mt-4 text-sm text-zinc-500">Viewer role is read-only for edits.</p>
      )}
      {testHits.length ? (
        <ol className="mt-8 max-h-56 overflow-auto font-mono text-xs text-zinc-400">
          {testHits.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ol>
      ) : null}
    </div>
  );
}
