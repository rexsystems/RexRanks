export type NodeType =
  | "permission"
  | "regex_permission"
  | "inheritance"
  | "prefix"
  | "suffix"
  | "meta"
  | "weight"
  | "display_name";

export interface RankNode {
  type: NodeType;
  key: string;
  value: string;
  negated: boolean;
  expiry: number;
  contexts?: Record<string, string[]>;
}

export interface GroupData {
  name: string;
  weight: number;
  displayName: string;
  nodes: RankNode[];
}

export interface UserData {
  uuid: string;
  name: string;
  primaryGroup: string;
  nodes: RankNode[];
}

export interface TrackData {
  groups: string[];
}

export interface EditorData {
  focus?: string;
  groups: Record<string, GroupData>;
  users: Record<string, UserData>;
  tracks: Record<string, TrackData>;
}

export interface DeletedSets {
  groups: string[];
  users: string[];
  tracks: string[];
}

export interface ProposedPayload extends EditorData {
  deleted?: DeletedSets;
}

export interface SessionInfo {
  ok: boolean;
  createdBy: string;
  focus: string;
  expiresAt: number;
  server: string;
  version: string;
  mode: "integrated" | "remote" | "demo";
  applyCode?: string;
  appliesImmediately?: boolean;
  data: EditorData;
  knownNodes: string[];
}

export type SubjectKind = "group" | "user" | "track";

export function emptyDeleted(): DeletedSets {
  return { groups: [], users: [], tracks: [] };
}

export function emptyData(): EditorData {
  return { groups: {}, users: {}, tracks: {} };
}

export function nodeShorthand(node: RankNode): string {
  switch (node.type) {
    case "permission":
      return (node.negated ? "-" : "") + node.key;
    case "regex_permission":
      return (node.negated ? "-" : "") + "r=" + node.key;
    case "inheritance":
      return "group." + node.key;
    case "prefix":
      return "prefix." + node.key + "." + node.value;
    case "suffix":
      return "suffix." + node.key + "." + node.value;
    case "meta":
      return "meta." + node.key + "." + node.value;
    case "weight":
      return "weight." + node.value;
    case "display_name":
      return "displayname." + node.value;
    default:
      return node.key;
  }
}

export function normaliseNode(raw: Partial<RankNode> & { type?: string; key?: string }): RankNode {
  const type = (raw.type || "permission").toLowerCase() as NodeType;
  return {
    type,
    key: raw.key ?? "",
    value: raw.value == null ? "true" : String(raw.value),
    negated: !!raw.negated,
    expiry: typeof raw.expiry === "number" ? raw.expiry : 0,
    contexts: raw.contexts,
  };
}
