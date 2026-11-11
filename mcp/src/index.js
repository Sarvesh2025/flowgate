#!/usr/bin/env node
/**
 * Off-path sidecar. Reads control-plane GET APIs. Never writes rules.
 */
import http from "node:http";
import { stdin as input, stdout as output } from "node:process";

const GATEWAY = process.env.FLOWGATE_URL || "http://127.0.0.1:8080";
const USER = process.env.FLOWGATE_USER || "operator";
const PASS = process.env.FLOWGATE_PASSWORD || "operator123";
const PORT = Number(process.env.PORT || 8090);
const OPENAI_KEY = process.env.OPENAI_API_KEY || "";

async function login() {
  const res = await fetch(`${GATEWAY}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (!res.ok) {
    throw new Error(`login ${res.status}`);
  }
  const body = await res.json();
  return body.accessToken;
}

async function authJson(path) {
  const token = await login();
  const res = await fetch(`${GATEWAY}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    throw new Error(`${path} ${res.status}`);
  }
  return res.json();
}

async function metrics() {
  const res = await fetch(`${GATEWAY}/api/v1/metrics`);
  if (!res.ok) {
    throw new Error(`metrics ${res.status}`);
  }
  return res.json();
}

async function rules() {
  return authJson("/api/v1/rules");
}

function stubExplain(samples, rulesList) {
  const list = Array.isArray(samples) ? samples : [];
  const first = list[0] || { proxy: 0, rejected: 0 };
  const last = list[list.length - 1] || first;
  const rejected = Number(last.rejected || 0) - Number(first.rejected || 0);
  const proxy = Number(last.proxy || 0) - Number(first.proxy || 0);
  const tight = (rulesList || []).filter((r) => r.enabled && r.limitCount <= 10);
  if (rejected > proxy && rejected > 0) {
    return `429s rose faster than successful proxy (${rejected} rejects vs ${proxy} proxy in the ring). Enabled low caps: ${
      tight.map((r) => r.name + "=" + r.limitCount).join(", ") || "none listed"
    }. Draft a higher limit; do not apply from MCP.`;
  }
  if (rejected > 0) {
    return `Some throttling (${rejected} rejects, ${proxy} proxy). Check algorithm ${
      (rulesList[0] && rulesList[0].algorithm) || "yaml default"
    } and windowMs. MCP will not change rules.`;
  }
  return `No 429 spike in the ring (proxy Δ ${proxy}). If Grafana disagrees, the ring may have rotated.`;
}

function draftFromText(text) {
  const raw = String(text || "");
  const count = Number((raw.match(/(\d+)\s*(?:rps|req|per second|\/s)/i) || raw.match(/\b(\d+)\b/) || [])[1] || 10);
  const seconds = raw.match(/(\d+)\s*(?:s|sec|second)/i);
  const msMatch = raw.match(/(\d+)\s*ms/i);
  const windowMs = msMatch ? Number(msMatch[1]) : seconds ? Number(seconds[1]) * 1000 : 1000;
  let algorithm = "SLIDING_WINDOW_COUNTER";
  if (/token/i.test(raw)) algorithm = "TOKEN_BUCKET";
  if (/log/i.test(raw)) algorithm = "SLIDING_WINDOW_LOG";
  if (/fixed/i.test(raw)) algorithm = "FIXED_WINDOW";
  const redisDownPolicy = /fail\s*open/i.test(raw) ? "FAIL_OPEN" : "FAIL_CLOSED";
  return {
    apply: false,
    note: "Proposal only. Paste into the rules UI or POST /api/v1/rules yourself.",
    rule: {
      name: "mcp-draft",
      algorithm,
      limitCount: count,
      windowMs,
      priority: 0,
      scope: "GLOBAL",
      redisDownPolicy,
      enabled: false,
    },
  };
}

async function llm(prompt, fallback) {
  if (!OPENAI_KEY) {
    return fallback;
  }
  const res = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${OPENAI_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: process.env.OPENAI_MODEL || "gpt-4o-mini",
      messages: [
        {
          role: "system",
          content: "Flowgate operator copilot. Never apply config. Propose only.",
        },
        { role: "user", content: prompt },
      ],
    }),
  });
  if (!res.ok) {
    return fallback;
  }
  const body = await res.json();
  return body.choices?.[0]?.message?.content || fallback;
}

async function explain() {
  let samples = [];
  let ruleRows = [];
  try {
    samples = await metrics();
  } catch {
    samples = [];
  }
  try {
    ruleRows = await rules();
  } catch {
    ruleRows = [];
  }
  const stub = stubExplain(samples, ruleRows);
  const text = await llm(
      `Ring samples: ${JSON.stringify(samples.slice(-12))}\nRules: ${JSON.stringify(ruleRows)}\nExplain throttling.`,
      stub
  );
  return { source: OPENAI_KEY ? "openai" : "stub", text, apply: false };
}

async function draft(text) {
  const proposal = draftFromText(text);
  if (OPENAI_KEY) {
    const extra = await llm(
        `Turn this into one JSON rule for Flowgate (algorithm, limitCount, windowMs, redisDownPolicy). Text: ${text}`,
        ""
    );
    if (extra) {
      proposal.llm = extra;
    }
  }
  return proposal;
}

const tools = [
  { name: "get_metrics", description: "Gateway ring-buffer snapshot" },
  { name: "list_rules", description: "GET /api/v1/rules as operator" },
  { name: "explain_throttling", description: "Why 429s might be happening (never applies a rule)" },
  { name: "draft_rule", description: "English → rule JSON. Does not POST to the gateway." },
];

async function callTool(name, args = {}) {
  switch (name) {
    case "get_metrics":
      return metrics();
    case "list_rules":
      return rules();
    case "explain_throttling":
      return explain();
    case "draft_rule":
      return draft(args.text || args.prompt || "");
    default:
      throw new Error("unknown tool " + name);
  }
}

function json(res, code, body) {
  const data = JSON.stringify(body);
  res.writeHead(code, { "Content-Type": "application/json" });
  res.end(data);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => {
      const raw = Buffer.concat(chunks).toString("utf8");
      if (!raw) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch (err) {
        reject(err);
      }
    });
    req.on("error", reject);
  });
}

function startHttp() {
  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, "http://localhost");
      if (req.method === "GET" && url.pathname === "/health") {
        json(res, 200, { name: "flowgate-mcp", gateway: GATEWAY, llm: Boolean(OPENAI_KEY) });
        return;
      }
      if (req.method === "GET" && url.pathname === "/metrics") {
        json(res, 200, await metrics());
        return;
      }
      if (req.method === "GET" && url.pathname === "/rules") {
        json(res, 200, await rules());
        return;
      }
      if (req.method === "POST" && url.pathname === "/explain") {
        json(res, 200, await explain());
        return;
      }
      if (req.method === "POST" && url.pathname === "/draft-rule") {
        const body = await readBody(req);
        json(res, 200, await draft(body.text || body.prompt || ""));
        return;
      }
      json(res, 404, { error: "not found" });
    } catch (err) {
      json(res, 502, { error: String(err.message || err) });
    }
  });
  server.listen(PORT, () => {
    console.log(`flowgate-mcp http://127.0.0.1:${PORT} → ${GATEWAY}`);
  });
}

async function startStdio() {
  let buf = "";
  input.setEncoding("utf8");
  const write = (msg) => {
    const json = JSON.stringify(msg);
    output.write(`Content-Length: ${Buffer.byteLength(json, "utf8")}\r\n\r\n${json}`);
  };
  input.on("data", async (chunk) => {
    buf += chunk;
    while (true) {
      const headerEnd = buf.indexOf("\r\n\r\n");
      if (headerEnd < 0) {
        return;
      }
      const match = buf.slice(0, headerEnd).match(/Content-Length:\s*(\d+)/i);
      if (!match) {
        buf = buf.slice(headerEnd + 4);
        continue;
      }
      const len = Number(match[1]);
      const start = headerEnd + 4;
      if (buf.length < start + len) {
        return;
      }
      const raw = buf.slice(start, start + len);
      buf = buf.slice(start + len);
      let req;
      try {
        req = JSON.parse(raw);
      } catch {
        continue;
      }
      if (req.method === "initialize") {
        write({
          jsonrpc: "2.0",
          id: req.id,
          result: {
            protocolVersion: "2024-11-05",
            capabilities: { tools: {} },
            serverInfo: { name: "flowgate-mcp", version: "0.1.0" },
          },
        });
        continue;
      }
      if (req.method === "notifications/initialized") {
        continue;
      }
      if (req.method === "tools/list") {
        write({
          jsonrpc: "2.0",
          id: req.id,
          result: {
            tools: tools.map((t) => ({
              name: t.name,
              description: t.description,
              inputSchema: {
                type: "object",
                properties: t.name === "draft_rule" ? { text: { type: "string" } } : {},
              },
            })),
          },
        });
        continue;
      }
      if (req.method === "tools/call") {
        try {
          const result = await callTool(req.params.name, req.params.arguments || {});
          write({
            jsonrpc: "2.0",
            id: req.id,
            result: { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] },
          });
        } catch (err) {
          write({
            jsonrpc: "2.0",
            id: req.id,
            error: { code: -32000, message: String(err.message || err) },
          });
        }
      }
    }
  });
}

if (process.argv.includes("--stdio")) {
  startStdio();
} else {
  startHttp();
}
