/** Catalog of node types: labels, icons, categories, defaults and palette groups. */
import {
  CalendarClock,
  Clock,
  FileText,
  GitBranch,
  Globe,
  Hand,
  Mail,
  Shuffle,
  Webhook,
  type LucideIcon,
} from "lucide-react";
import type { NodeType } from "@/lib/api/types";

export type NodeCategory = "trigger" | "action" | "logic";
/** React Flow node component keys. */
export type FlowNodeKind = "trigger" | "action" | "condition" | "delay";

export interface NodeCatalogEntry {
  type: NodeType;
  label: string;
  description: string;
  category: NodeCategory;
  kind: FlowNodeKind;
  icon: LucideIcon;
  /** Prefix for generated node ids (`http_1`, ...). */
  idPrefix: string;
}

export const NODE_CATALOG: Record<NodeType, NodeCatalogEntry> = {
  MANUAL_TRIGGER: {
    type: "MANUAL_TRIGGER",
    label: "Manual",
    description: "Start runs on demand with a JSON input.",
    category: "trigger",
    kind: "trigger",
    icon: Hand,
    idPrefix: "manual",
  },
  WEBHOOK_TRIGGER: {
    type: "WEBHOOK_TRIGGER",
    label: "Webhook",
    description: "Start when an HTTP request hits a unique URL.",
    category: "trigger",
    kind: "trigger",
    icon: Webhook,
    idPrefix: "webhook",
  },
  SCHEDULE_TRIGGER: {
    type: "SCHEDULE_TRIGGER",
    label: "Schedule",
    description: "Start on a cron schedule in a timezone.",
    category: "trigger",
    kind: "trigger",
    icon: CalendarClock,
    idPrefix: "schedule",
  },
  HTTP_REQUEST: {
    type: "HTTP_REQUEST",
    label: "HTTP Request",
    description: "Call an external API and capture the response.",
    category: "action",
    kind: "action",
    icon: Globe,
    idPrefix: "http",
  },
  TRANSFORM: {
    type: "TRANSFORM",
    label: "Transform",
    description: "Pick, rename and set fields to shape data.",
    category: "action",
    kind: "action",
    icon: Shuffle,
    idPrefix: "transform",
  },
  LOG: {
    type: "LOG",
    label: "Log",
    description: "Write a message to the execution log.",
    category: "action",
    kind: "action",
    icon: FileText,
    idPrefix: "log",
  },
  NOTIFICATION: {
    type: "NOTIFICATION",
    label: "Notification",
    description: "Send an e-mail or a log notification.",
    category: "action",
    kind: "action",
    icon: Mail,
    idPrefix: "notify",
  },
  CONDITION: {
    type: "CONDITION",
    label: "Condition",
    description: "Branch into true / false paths.",
    category: "logic",
    kind: "condition",
    icon: GitBranch,
    idPrefix: "condition",
  },
  DELAY: {
    type: "DELAY",
    label: "Delay",
    description: "Wait durably before continuing.",
    category: "logic",
    kind: "delay",
    icon: Clock,
    idPrefix: "delay",
  },
};

export const PALETTE_GROUPS: { title: string; category: NodeCategory; types: NodeType[] }[] = [
  { title: "Triggers", category: "trigger", types: ["MANUAL_TRIGGER", "WEBHOOK_TRIGGER", "SCHEDULE_TRIGGER"] },
  { title: "Actions", category: "action", types: ["HTTP_REQUEST", "TRANSFORM", "LOG", "NOTIFICATION"] },
  { title: "Logic", category: "logic", types: ["CONDITION", "DELAY"] },
];

export function defaultNodeName(type: NodeType): string {
  const entry = NODE_CATALOG[type];
  return entry.category === "trigger" ? `${entry.label} trigger` : entry.label;
}
