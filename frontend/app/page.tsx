"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { login } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin123");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
      router.push("/dashboard");
    } catch (err) {
      const message = err instanceof Error ? err.message : "login failed";
      setError(message.includes("Failed to fetch") || message.includes("Timeout") ? "Gateway is not running on :8080" : message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6">
      <p className="text-xs uppercase tracking-widest text-emerald-500">Control plane</p>
      <h1 className="mt-2 text-2xl font-semibold tracking-tight">Flowgate</h1>
      <p className="mt-1 text-sm text-zinc-400">Sign in with a seeded operator account</p>
      <form onSubmit={onSubmit} className="mt-8 space-y-4">
        <label className="block text-sm">
          Username
          <input
            className="fg-input mt-1 w-full"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
          />
        </label>
        <label className="block text-sm">
          Password
          <input
            type="password"
            className="fg-input mt-1 w-full"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>
        {error ? <p className="text-sm text-red-400">{error}</p> : null}
        <button type="submit" disabled={busy} className="fg-btn w-full">
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>
      <p className="mt-6 text-xs text-zinc-500">
        Seeded: admin/admin123 · operator/operator123 · viewer/viewer123
      </p>
    </main>
  );
}
