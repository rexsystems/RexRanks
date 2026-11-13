# RexRanks editor host

A tiny Node.js server that hosts the RexRanks web editor for servers that cannot (or would rather not) expose the plugin's integrated HTTP port.

## What it does

1. A Minecraft server with `editor.mode: remote` and `editor.remote-url` pointing here runs `/rr editor`.
2. The plugin POSTs a snapshot to `/api/sessions` and receives a one-time URL.
3. Staff open the link, edit groups / users / tracks, and use the colour studio.
4. "Save for /rr apply" stores a pending payload.
5. An operator runs `/rr apply <code>` in game. The plugin GETs `/api/apply/<code>` and writes the changes.

The colour studio is also usable without a session (demo mode) — handy as a public MiniMessage playground.

## Run from source

```bash
cd web
npm ci
npm run build
PUBLIC_URL=https://editor.example.com PORT=7781 npm run server
```

## Docker

From the `web/` directory, after `npm run build`:

```bash
PUBLIC_URL=https://editor.example.com docker compose -f server/docker-compose.yml up --build -d
```

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `7781` | Listen port |
| `HOST` | `0.0.0.0` | Bind address |
| `PUBLIC_URL` | `http://127.0.0.1:7781` | URL written into session links |
| `DATA_DIR` | `./server/data` | Session JSON files |
| `DIST_DIR` | `./dist` | Built SPA |
| `EDITOR_SECRET` | _(empty)_ | If set, `POST /api/sessions` requires header `X-RexRanks-Secret` |
| `SESSION_TTL_MS` | two hours | Fallback lifetime |

On the Minecraft server, set:

```yaml
editor:
  enabled: true
  mode: remote
  remote-url: "https://editor.example.com"
```

## API

- `POST /api/sessions` — create a session (plugin). Body: `{ token, applyCode, createdBy, expiresAt, focus, data, knownNodes }`. Returns `{ url, id, applyCode }`.
- `GET /api/session?token=` — load the snapshot (SPA).
- `POST /api/apply?token=` — store a pending payload.
- `GET /api/apply/:code` — plugin fetches the pending payload.
- `GET /api/search?q=&token=` — node search.
