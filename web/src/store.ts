import { computed, reactive } from "vue";
import { currentToken, fetchSession, postApply, postDiff, searchNodes } from "./api";
import type { DeletedSets, EditorData, GroupData, ProposedPayload, RankNode, SessionInfo, TrackData, UserData } from "./types";
import { emptyData, emptyDeleted, normaliseNode } from "./types";

export interface Toast {
  id: number;
  kind: "ok" | "error" | "info";
  text: string;
}

interface Store {
  loading: boolean;
  error: string | null;
  session: SessionInfo | null;
  data: EditorData;
  baseline: string;
  deleted: DeletedSets;
  knownNodes: string[];
  toasts: Toast[];
  lastApply: string | null;
}

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T;
}

function normaliseData(raw: EditorData): EditorData {
  const groups: Record<string, GroupData> = {};
  for (const [k, g] of Object.entries(raw.groups || {})) {
    groups[k] = {
      name: g.name || k,
      weight: g.weight ?? 0,
      displayName: g.displayName ?? "",
      nodes: (g.nodes || []).map(normaliseNode),
    };
  }
  const users: Record<string, UserData> = {};
  for (const [k, u] of Object.entries(raw.users || {})) {
    users[k] = {
      uuid: u.uuid || k,
      name: u.name || k,
      primaryGroup: u.primaryGroup || "default",
      nodes: (u.nodes || []).map(normaliseNode),
    };
  }
  const tracks: Record<string, TrackData> = {};
  for (const [k, t] of Object.entries(raw.tracks || {})) {
    tracks[k] = { groups: [...(t.groups || [])] };
  }
  return { focus: raw.focus, groups, users, tracks };
}

function demoData(): EditorData {
  const g = (name: string, weight: number, displayName: string, nodes: RankNode[]): GroupData => ({
    name, weight, displayName, nodes,
  });
  const n = (type: RankNode["type"], key: string, value = "true"): RankNode => ({
    type, key, value, negated: false, expiry: 0,
  });
  return {
    groups: {
      default: g("default", 0, "<#9aa0a6>Member", [
        n("permission", "essentials.spawn"),
        n("permission", "essentials.tpa"),
        n("permission", "essentials.home"),
        n("prefix", "10", "<#9aa0a6>[M] "),
      ]),
      vip: g("vip", 20, "<gradient:#ffd36a:#ff8a5b>VIP", [
        n("inheritance", "default"),
        n("permission", "essentials.fly"),
        n("permission", "essentials.kit.vip"),
        n("prefix", "20", "<gradient:#ffd36a:#ff8a5b>[VIP] "),
      ]),
      helper: g("helper", 50, "<#6bcbff>Helper", [
        n("inheritance", "vip"),
        n("permission", "essentials.kick"),
        n("permission", "essentials.mute"),
        n("prefix", "50", "<#6bcbff>[Helper] "),
      ]),
      admin: g("admin", 100, "<gradient:#ff6b6b:#ffd36a>Admin", [
        n("inheritance", "helper"),
        n("permission", "*"),
        n("prefix", "100", "<gradient:#ff6b6b:#ffd36a>[Admin] "),
      ]),
    },
    users: {
      "11111111-1111-1111-1111-111111111111": {
        uuid: "11111111-1111-1111-1111-111111111111",
        name: "Steve",
        primaryGroup: "admin",
        nodes: [n("inheritance", "admin"), n("permission", "rexranks.admin")],
      },
      "22222222-2222-2222-2222-222222222222": {
        uuid: "22222222-2222-2222-2222-222222222222",
        name: "Alex",
        primaryGroup: "vip",
        nodes: [n("inheritance", "vip"), n("meta", "nickname", "Aly")],
      },
    },
    tracks: {
      staff: { groups: ["helper", "admin"] },
      donor: { groups: ["default", "vip"] },
    },
  };
}

const state = reactive<Store>({
  loading: true,
  error: null,
  session: null,
  data: emptyData(),
  baseline: "",
  deleted: emptyDeleted(),
  knownNodes: [],
  toasts: [],
  lastApply: null,
});

let toastSeq = 1;

export const store = state;

export const dirty = computed(() => serialise() !== state.baseline);

export const changeCount = computed(() => {
  if (!dirty.value) return 0;
  try {
    const original = JSON.parse(state.baseline) as { data: EditorData; deleted: DeletedSets };
    let n = state.deleted.groups.length + state.deleted.users.length + state.deleted.tracks.length;
    const keys = (a: object, b: object) => new Set([...Object.keys(a), ...Object.keys(b)]);
    for (const k of keys(original.data.groups, state.data.groups)) {
      if (JSON.stringify((original.data.groups as any)[k]) !== JSON.stringify((state.data.groups as any)[k])) n++;
    }
    for (const k of keys(original.data.users, state.data.users)) {
      if (JSON.stringify((original.data.users as any)[k]) !== JSON.stringify((state.data.users as any)[k])) n++;
    }
    for (const k of keys(original.data.tracks, state.data.tracks)) {
      if (JSON.stringify((original.data.tracks as any)[k]) !== JSON.stringify((state.data.tracks as any)[k])) n++;
    }
    return n;
  } catch {
    return 1;
  }
});

function serialise(): string {
  return JSON.stringify({ data: state.data, deleted: state.deleted });
}

function snapshotBaseline() {
  state.baseline = serialise();
}

export function toast(kind: Toast["kind"], text: string) {
  const id = toastSeq++;
  state.toasts.push({ id, kind, text });
  setTimeout(() => {
    state.toasts = state.toasts.filter((t) => t.id !== id);
  }, 4200);
}

export function proposed(): ProposedPayload {
  return {
    ...clone(state.data),
    deleted: clone(state.deleted),
  };
}

export async function boot() {
  state.loading = true;
  state.error = null;
  try {
    if (!currentToken()) {
      loadDemo();
      return;
    }
    const session = await fetchSession();
    applySession(session);
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    // Fall back to a local demo so the colour studio is still usable.
    loadDemo();
    if (currentToken()) {
      state.error = message;
      toast("error", "Could not load the live session: " + message + ". Showing a demo instead.");
    }
  } finally {
    state.loading = false;
  }
}

function applySession(session: SessionInfo) {
  const data = normaliseData(session.data || emptyData());
  state.session = {
    ...session,
    mode: session.mode || "integrated",
    appliesImmediately: session.appliesImmediately ?? session.mode !== "remote",
    data,
    knownNodes: session.knownNodes || [],
  };
  state.data = clone(data);
  state.deleted = emptyDeleted();
  state.knownNodes = session.knownNodes || [];
  snapshotBaseline();
}

function loadDemo() {
  const data = demoData();
  state.session = {
    ok: true,
    createdBy: "demo",
    focus: "all",
    expiresAt: Date.now() + 86_400_000,
    server: "demo",
    version: "1.0.0",
    mode: "demo",
    appliesImmediately: false,
    data,
    knownNodes: [
      "essentials.fly",
      "essentials.gamemode",
      "essentials.teleport",
      "minecraft.command.op",
      "rexranks.*",
      "*",
    ],
  };
  state.data = clone(data);
  state.deleted = emptyDeleted();
  state.knownNodes = state.session.knownNodes;
  snapshotBaseline();
}

export function discardChanges() {
  if (!state.session) return;
  state.data = clone(state.session.data);
  state.deleted = emptyDeleted();
}

export function upsertGroup(group: GroupData) {
  state.data.groups[group.name] = group;
  state.deleted.groups = state.deleted.groups.filter((n) => n !== group.name);
}

export function createGroup(name: string) {
  const id = name.trim().toLowerCase();
  if (!id) throw new Error("Group name required");
  if (state.data.groups[id]) throw new Error("That group already exists");
  upsertGroup({ name: id, weight: 0, displayName: "", nodes: [] });
  return id;
}

export function deleteGroup(name: string) {
  if (name === "default") throw new Error("The default group cannot be deleted");
  delete state.data.groups[name];
  if (!state.deleted.groups.includes(name)) state.deleted.groups.push(name);
}

export function upsertUser(user: UserData) {
  state.data.users[user.uuid] = user;
  state.deleted.users = state.deleted.users.filter((n) => n !== user.uuid);
}

export function deleteUser(uuid: string) {
  delete state.data.users[uuid];
  if (!state.deleted.users.includes(uuid)) state.deleted.users.push(uuid);
}

export function upsertTrack(name: string, groups: string[]) {
  state.data.tracks[name] = { groups };
  state.deleted.tracks = state.deleted.tracks.filter((n) => n !== name);
}

export function createTrack(name: string) {
  const id = name.trim().toLowerCase();
  if (!id) throw new Error("Track name required");
  if (state.data.tracks[id]) throw new Error("That track already exists");
  upsertTrack(id, []);
  return id;
}

export function deleteTrack(name: string) {
  delete state.data.tracks[name];
  if (!state.deleted.tracks.includes(name)) state.deleted.tracks.push(name);
}

export function setNodes(kind: "group" | "user", id: string, nodes: RankNode[]) {
  if (kind === "group") {
    const g = state.data.groups[id];
    if (g) g.nodes = nodes;
  } else {
    const u = state.data.users[id];
    if (u) u.nodes = nodes;
  }
}

export async function applyChanges(): Promise<string> {
  if (state.session?.mode === "demo") {
    snapshotBaseline();
    const summary = "Demo session — changes kept locally. Connect a live /rr editor link to apply them to a server.";
    state.lastApply = summary;
    toast("info", summary);
    return summary;
  }
  const payload = proposed();
  const result = await postApply(payload);
  const summary = result.summary || "Applied.";
  state.lastApply = summary;
  if (state.session) {
    state.session.data = clone(state.data);
  }
  snapshotBaseline();
  toast("ok", summary);
  return summary;
}

export async function previewDiff() {
  if (state.session?.mode === "demo" || state.session?.mode === "remote") {
    return { local: true, payload: proposed(), changes: changeCount.value };
  }
  return postDiff(proposed());
}

export async function runSearch(q: string) {
  if (state.session?.mode === "demo" || !currentToken()) {
    const hits: { subject: string; node: string }[] = [];
    const needle = q.toLowerCase();
    const walk = (subject: string, nodes: RankNode[]) => {
      for (const node of nodes) {
        const s = `${node.type} ${node.key} ${node.value}`.toLowerCase();
        if (s.includes(needle)) hits.push({ subject, node: `${node.type}:${node.key}` });
      }
    };
    for (const g of Object.values(state.data.groups)) walk("group:" + g.name, g.nodes);
    for (const u of Object.values(state.data.users)) walk("user:" + u.name, u.nodes);
    return { hits, count: hits.length };
  }
  return searchNodes(q);
}

export function applyChatMeta(kind: "group" | "user", id: string, which: "prefix" | "suffix", text: string, priority = 100) {
  const subject = kind === "group" ? state.data.groups[id] : state.data.users[id];
  if (!subject) throw new Error("Subject not found");
  const existing = subject.nodes.findIndex((n) => n.type === which && n.key === String(priority));
  const node: RankNode = {
    type: which,
    key: String(priority),
    value: text,
    negated: false,
    expiry: 0,
  };
  if (existing >= 0) subject.nodes[existing] = node;
  else subject.nodes.push(node);
  toast("ok", `Applied ${which} to ${kind} ${kind === "group" ? id : (subject as UserData).name}.`);
}

export function groupList() {
  return Object.values(state.data.groups).sort((a, b) => b.weight - a.weight || a.name.localeCompare(b.name));
}

export function userList() {
  return Object.values(state.data.users).sort((a, b) => a.name.localeCompare(b.name));
}

export function trackList() {
  return Object.entries(state.data.tracks)
    .map(([name, t]) => ({ name, ...t }))
    .sort((a, b) => a.name.localeCompare(b.name));
}
