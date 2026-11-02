"use client";

import { api, role } from "@/lib/api";
import type { CreatedKey, KeyView } from "@/lib/types";
import { FormEvent, useEffect, useState } from "react";

export default function KeysPage() {
  const isAdmin = role() === "ADMIN";
  const [keys, setKeys] = useState<KeyView[]>([]);
  const [revealed, setRevealed] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [name, setName] = useState("demo");
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setKeys(await api<KeyView[]>("/api/v1/keys"));
  }

  useEffect(() => {
    if (!isAdmin) {
      return;
    }
    load().catch((e) => setError(String(e)));
  }, [isAdmin]);

  if (!isAdmin) {
    return <p className="text-sm text-zinc-400">API keys are ADMIN only.</p>;
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const created = await api<CreatedKey>("/api/v1/keys", {
        method: "POST",
        body: JSON.stringify({ name, tier: "FREE" }),
      });
      setRevealed(created.apiKey);
      setCopied(false);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "create failed");
    }
  }

  return (
    <div>
      <h1 className="text-xl font-semibold">API keys</h1>
      <p className="mt-1 text-sm text-zinc-400">Raw secret is shown once. Send it as header X-API-Key.</p>
      {error ? <p className="mt-2 text-sm text-red-400">{error}</p> : null}
      {revealed ? (
        <div className="fg-card mt-4 border-amber-800">
          <p className="break-all font-mono text-sm">{revealed}</p>
          <button
            className="mt-2 text-xs text-amber-300"
            onClick={async () => {
              await navigator.clipboard.writeText(revealed);
              setCopied(true);
            }}
          >
            {copied ? "Copied" : "Copy"}
          </button>
        </div>
      ) : null}
      <table className="mt-6 w-full text-left text-sm">
        <thead className="text-zinc-500">
          <tr>
            <th className="py-2">Name</th>
            <th>Prefix</th>
            <th>Tier</th>
            <th>Revoked</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {keys.map((k) => (
            <tr key={k.id} className="border-t border-zinc-800">
              <td className="py-2">{k.name}</td>
              <td className="font-mono text-xs">{k.prefix}</td>
              <td>{k.tier}</td>
              <td>{k.revoked ? "yes" : "no"}</td>
              <td className="space-x-3">
                {!k.revoked ? (
                  <>
                    <button
                      className="text-zinc-300"
                      onClick={async () => {
                        const created = await api<CreatedKey>(`/api/v1/keys/${k.id}/rotate`, {
                          method: "POST",
                        });
                        setRevealed(created.apiKey);
                        setCopied(false);
                        await load();
                      }}
                    >
                      Rotate
                    </button>
                    <button
                      className="text-red-400"
                      onClick={async () => {
                        await api(`/api/v1/keys/${k.id}`, { method: "DELETE" });
                        await load();
                      }}
                    >
                      Revoke
                    </button>
                  </>
                ) : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <form onSubmit={onCreate} className="mt-8 flex max-w-md gap-2">
        <input className="fg-input flex-1" value={name} onChange={(e) => setName(e.target.value)} />
        <button className="fg-btn">Create</button>
      </form>
    </div>
  );
}
