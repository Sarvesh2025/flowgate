"use client";

import { api } from "@/lib/api";
import type { Audit } from "@/lib/types";
import { Fragment, useEffect, useState } from "react";

export default function AuditPage() {
  const [rows, setRows] = useState<Audit[]>([]);
  const [open, setOpen] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Audit[]>("/api/v1/audit")
      .then(setRows)
      .catch((e) => setError(String(e)));
  }, []);

  return (
    <div>
      <h1 className="text-xl font-semibold">Audit</h1>
      {error ? <p className="mt-2 text-sm text-red-400">{error}</p> : null}
      <table className="mt-6 w-full text-left text-sm">
        <thead className="text-zinc-500">
          <tr>
            <th className="py-2">When</th>
            <th>Actor</th>
            <th>Action</th>
            <th>Entity</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <Fragment key={r.id}>
              <tr
                className="cursor-pointer border-t border-zinc-800 hover:bg-zinc-900/60"
                onClick={() => setOpen(open === r.id ? null : r.id)}
              >
                <td className="py-2 text-xs text-zinc-400">{r.at}</td>
                <td>{r.actor}</td>
                <td>{r.action}</td>
                <td>
                  {r.entityType} {r.entityId}
                </td>
              </tr>
              {open === r.id ? (
                <tr className="border-t border-zinc-900">
                  <td colSpan={4} className="py-2">
                    <pre className="overflow-auto rounded bg-zinc-950 p-3 text-xs text-zinc-400">
                      {JSON.stringify(
                        {
                          before: r.beforeJson ? JSON.parse(r.beforeJson) : null,
                          after: r.afterJson ? JSON.parse(r.afterJson) : null,
                        },
                        null,
                        2,
                      )}
                    </pre>
                  </td>
                </tr>
              ) : null}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  );
}
