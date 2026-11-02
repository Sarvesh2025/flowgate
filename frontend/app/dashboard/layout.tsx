"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { logout, role, token, username, type Role } from "@/lib/api";

const links: { href: string; label: string; roles?: Role[] }[] = [
  { href: "/dashboard", label: "Overview" },
  { href: "/dashboard/rules", label: "Rules" },
  { href: "/dashboard/routes", label: "Routes" },
  { href: "/dashboard/keys", label: "API keys", roles: ["ADMIN"] },
  { href: "/dashboard/audit", label: "Audit" },
  { href: "/dashboard/simulator", label: "Simulator" },
];

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const path = usePathname();
  const [ready, setReady] = useState(false);
  const [user, setUser] = useState("");
  const [currentRole, setCurrentRole] = useState<Role | "">("");

  useEffect(() => {
    if (!token()) {
      router.replace("/");
      return;
    }
    setUser(username() || "");
    setCurrentRole(role() || "");
    setReady(true);
  }, [router]);

  if (!ready) {
    return <p className="p-8 text-sm text-zinc-400">Loading…</p>;
  }

  const visible = links.filter((l) => !l.roles || l.roles.includes(currentRole as Role));

  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      <aside className="flex w-full shrink-0 flex-col border-b border-zinc-800 bg-zinc-950 p-4 md:w-56 md:border-b-0 md:border-r">
        <p className="text-sm font-semibold tracking-tight">Flowgate</p>
        <p className="mt-1 truncate text-xs text-zinc-500">
          {user} · {currentRole}
        </p>
        <nav className="mt-6 space-y-1">
          {visible.map((l) => {
            const active = path === l.href;
            return (
              <Link
                key={l.href}
                href={l.href}
                className={`block rounded-md px-2 py-1.5 text-sm ${
                  active ? "bg-zinc-800 text-white" : "text-zinc-400 hover:bg-zinc-900 hover:text-white"
                }`}
              >
                {l.label}
              </Link>
            );
          })}
        </nav>
        <div className="mt-auto space-y-1 pt-8 text-xs">
          <a className="block text-zinc-500 hover:text-zinc-300" href="http://localhost:3000" target="_blank">
            Grafana
          </a>
          <a className="block text-zinc-500 hover:text-zinc-300" href="http://localhost:16686" target="_blank">
            Jaeger
          </a>
          <button
            className="mt-4 text-zinc-500 hover:text-red-400"
            onClick={async () => {
              await logout();
              router.replace("/");
            }}
          >
            Sign out
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-auto p-8">{children}</main>
    </div>
  );
}
