# RexRanks web editor

The Vue 3 + Vite single-page editor bundled into the plugin JAR (integrated mode) and served by the optional remote host in `server/`.

```bash
npm ci
npm test
npm run build
npm run dev          # http://127.0.0.1:5173 — proxies /api to :7781
npm run server       # after a build, serves dist + the session API on :7781
```

The colour studio is under `src/color/` (engine, presets, unit tests) and `src/views/ColorStudio.vue`. Hosting, Docker and the HTTP API are documented in [server/README.md](server/README.md).
