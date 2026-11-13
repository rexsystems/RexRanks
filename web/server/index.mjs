#!/usr/bin/env node
/**
 * RexRanks remote editor host.
 *
 * Serves the built SPA (../dist) and a small session API used when
 * a Minecraft server sets `editor.mode: remote` and uploads a snapshot
 * with /rr editor. Staff edit in the browser; the plugin applies the
 * result with /rr apply <code>.
 *
 * Environment:
 *   PORT          listen port (default 7781)
 *   HOST          bind address (default 0.0.0.0)
 *   PUBLIC_URL    URL stamped into session links (default http://HOST:PORT)
 *   DATA_DIR      session storage (default ./data)
 *   DIST_DIR      SPA files (default ../dist)
 *   SESSION_TTL_MS  default lifetime if the plugin doesn't send expiresAt
 *   EDITOR_SECRET   optional shared secret; require header X-RexRanks-Secret
 */
import { createServer } from "node:http";
import { createReadStream, existsSync, mkdirSync, readdirSync, readFileSync, statSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { randomBytes } from "node:crypto";

const __dirname = dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 7781);
const HOST = process.env.HOST || "0.0.0.0";
const DATA = resolve(process.env.DATA_DIR || join(__dirname, "data"));
const DIST = resolve(process.env.DIST_DIR || join(__dirname, "..", "dist"));
const TTL = Number(process.env.SESSION_TTL_MS || 1000 * 60 * 120);
const SECRET = process.env.EDITOR_SECRET || "";
const PUBLIC_URL = (process.env.PUBLIC_URL || `http://127.0.0.1:${PORT}`).replace(/\/$/, "");

mkdirSync(DATA, { recursive: true });

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".woff2": "font/woff2",
  ".ico": "image/x-icon",
  ".map": "application/json",
};

function json(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(data),
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Authorization, Content-Type, X-RexRanks-Secret",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  });
  res.end(data);
}

function cors(res) {
  res.writeHead(204, {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Authorization, Content-Type, X-RexRanks-Secret",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  });
  res.end();
}

function readBody(req) {
  return new Promise((resolveBody, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (c) => {
      size += c.length;
      if (size > 8 * 1024 * 1024) {
        reject(new Error("payload too large"));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on("end", () => resolveBody(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

function tokenOf(req, url) {
  const auth = req.headers.authorization;
  if (auth && auth.toLowerCase().startsWith("bearer ")) return auth.slice(7).trim();
  return url.searchParams.get("token");
}

function sessionPath(id) {
  const safe = String(id).replace(/[^A-Za-z0-9_-]/g, "");
  return join(DATA, safe + ".json");
}

function loadSession(id) {
  const p = sessionPath(id);
  if (!existsSync(p)) return null;
  try {
    return JSON.parse(readFileSync(p, "utf8"));
  } catch {
    return null;
  }
}

function saveSession(session) {
  writeFileSync(sessionPath(session.id), JSON.stringify(session));
}

function allSessions() {
  return readdirSync(DATA)
    .filter((f) => f.endsWith(".json"))
    .map((f) => {
      try {
        return JSON.parse(readFileSync(join(DATA, f), "utf8"));
      } catch {
        return null;
      }
    })
    .filter(Boolean);
}

function findByToken(token) {
  if (!token) return null;
  return allSessions().find((s) => s.token === token) || null;
}

function findByCode(code) {
  if (!code) return null;
  const c = code.toLowerCase();
  return allSessions().find((s) => String(s.applyCode).toLowerCase() === c) || null;
}

function expired(s) {
  return !s || (s.expiresAt && Date.now() > s.expiresAt);
}

function purge() {
  for (const s of allSessions()) {
    if (expired(s)) {
      try {
        unlinkSync(sessionPath(s.id));
      } catch {
        /* ignore */
      }
    }
  }
}

function requireSecret(req, res) {
  if (!SECRET) return true;
  if (req.headers["x-rexranks-secret"] === SECRET) return true;
  json(res, 401, { error: "invalid secret" });
  return false;
}

function sessionPayload(s) {
  return {
    ok: true,
    id: s.id,
    createdBy: s.createdBy,
    focus: s.focus,
    expiresAt: s.expiresAt,
    server: s.server || "remote",
    version: s.version || "1.0.0",
    mode: "remote",
    applyCode: s.applyCode,
    appliesImmediately: false,
    data: s.data,
    knownNodes: s.knownNodes || [],
  };
}

async function handleApi(req, res, url) {
  if (req.method === "OPTIONS") {
    cors(res);
    return;
  }
  const path = url.pathname;
  purge();

  if (req.method === "POST" && path === "/api/sessions") {
    if (!requireSecret(req, res)) return;
    let body;
    try {
      body = JSON.parse(await readBody(req) || "{}");
    } catch {
      json(res, 400, { error: "invalid json" });
      return;
    }
    const id = (body.id || randomBytes(9).toString("base64url")).replace(/[^A-Za-z0-9_-]/g, "");
    const session = {
      id,
      token: body.token || randomBytes(18).toString("base64url"),
      applyCode: body.applyCode || randomBytes(6).toString("base64url"),
      createdBy: body.createdBy || "unknown",
      expiresAt: body.expiresAt || Date.now() + TTL,
      focus: body.focus || "all",
      data: body.data || { groups: {}, users: {}, tracks: {} },
      knownNodes: body.knownNodes || [],
      server: body.server || "remote",
      version: body.version || "1.0.0",
      pending: null,
      createdAt: Date.now(),
    };
    saveSession(session);
    const link = `${PUBLIC_URL}/#/s/${session.id}?token=${encodeURIComponent(session.token)}`;
    json(res, 201, { ok: true, id: session.id, url: link, applyCode: session.applyCode });
    return;
  }

  if (req.method === "GET" && path.startsWith("/api/apply/")) {
    const code = decodeURIComponent(path.slice("/api/apply/".length));
    const s = findByCode(code);
    if (!s || expired(s) || !s.pending) {
      json(res, 404, { error: "no pending apply for that code" });
      return;
    }
    json(res, 200, { ok: true, payload: s.pending, applyCode: s.applyCode });
    return;
  }

  // Authenticated routes
  const token = tokenOf(req, url);
  let session = findByToken(token);
  if (!session && path.startsWith("/api/sessions/")) {
    const id = path.slice("/api/sessions/".length).split("/")[0];
    session = loadSession(id);
    if (session && session.token !== token) session = null;
  }
  if (!session || expired(session)) {
    json(res, 401, { error: "invalid or expired token" });
    return;
  }

  if (req.method === "GET" && (path === "/api/session" || path === "/api/sessions/" + session.id || path === "/api/")) {
    json(res, 200, sessionPayload(session));
    return;
  }
  if (req.method === "GET" && path === "/api/data") {
    json(res, 200, session.data);
    return;
  }
  if (req.method === "POST" && (path === "/api/apply" || path === "/api/sessions/" + session.id + "/pending")) {
    let body;
    try {
      body = JSON.parse(await readBody(req) || "{}");
    } catch {
      json(res, 400, { error: "invalid json" });
      return;
    }
    session.pending = body;
    session.pendingAt = Date.now();
    saveSession(session);
    json(res, 200, {
      ok: true,
      summary: "Saved. Run /rr apply " + session.applyCode + " on the server to take these changes live.",
      applyCode: session.applyCode,
    });
    return;
  }
  if (req.method === "POST" && path === "/api/diff") {
    json(res, 200, { ok: true, note: "diff is computed in the client for remote sessions" });
    return;
  }
  if (req.method === "GET" && path === "/api/search") {
    const q = (url.searchParams.get("q") || "").toLowerCase();
    const hits = [];
    const walk = (subject, nodes) => {
      for (const n of nodes || []) {
        const s = `${n.type || ""} ${n.key || ""} ${n.value || ""}`.toLowerCase();
        if (s.includes(q)) hits.push({ subject, node: `${n.type}:${n.key}` });
      }
    };
    const data = session.data || {};
    for (const [k, g] of Object.entries(data.groups || {})) walk("group:" + (g.name || k), g.nodes);
    for (const [k, u] of Object.entries(data.users || {})) walk("user:" + (u.name || k), u.nodes);
    json(res, 200, { hits, count: hits.length });
    return;
  }

  json(res, 404, { error: "not found" });
}

function safeStatic(urlPath) {
  const relative = urlPath === "/" ? "/index.html" : urlPath;
  const cleaned = normalize(relative).replace(/^(\.\.[/\\])+/, "");
  const full = resolve(DIST, "." + cleaned);
  if (!full.startsWith(DIST)) return null;
  if (existsSync(full) && statSync(full).isFile()) return full;
  const index = join(DIST, "index.html");
  if (!cleaned.includes(".") && existsSync(index)) return index;
  return null;
}

const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    if (url.pathname.startsWith("/api/")) {
      await handleApi(req, res, url);
      return;
    }
    if (req.method === "OPTIONS") {
      cors(res);
      return;
    }
    const file = safeStatic(decodeURIComponent(url.pathname));
    if (!file) {
      res.writeHead(404, { "Content-Type": "text/plain" });
      res.end("not found");
      return;
    }
    const mime = MIME[extname(file).toLowerCase()] || "application/octet-stream";
    res.writeHead(200, { "Content-Type": mime, "Cache-Control": file.endsWith("index.html") ? "no-cache" : "public, max-age=86400" });
    createReadStream(file).pipe(res);
  } catch (e) {
    json(res, 500, { error: String(e.message || e) });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`RexRanks editor host listening on http://${HOST}:${PORT}`);
  console.log(`Public URL: ${PUBLIC_URL}`);
  console.log(`Data dir:   ${DATA}`);
  console.log(`SPA dir:    ${DIST}${existsSync(join(DIST, "index.html")) ? "" : "  (dist not built yet — run npm run build)"}`);
});
