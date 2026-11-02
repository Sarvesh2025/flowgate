export type Rule = {
  id: string;
  name: string;
  routeId: string | null;
  scope: string;
  algorithm: string;
  limitCount: number;
  windowMs: number;
  priority: number;
  redisDownPolicy: string;
  enabled: boolean;
};

export type Route = {
  id: string;
  routeKey: string;
  pathPattern: string;
  methods: string;
  rewritePrefix: string | null;
  lbAlgorithm: string;
  canaryPercent: number;
  enabled: boolean;
  targets: {
    id: string;
    url: string;
    weight: number;
    canary: boolean;
    healthy: boolean;
  }[];
};

export type KeyView = {
  id: string;
  name: string;
  prefix: string;
  tier: string;
  revoked: boolean;
};

export type CreatedKey = KeyView & { apiKey: string };

export type Audit = {
  id: string;
  actor: string;
  action: string;
  entityType: string;
  entityId: string;
  beforeJson: string | null;
  afterJson: string | null;
  at: string;
};

export type MetricSample = {
  at: string;
  proxy: number;
  rejected: number;
};

export const ALGORITHMS = [
  "TOKEN_BUCKET",
  "SLIDING_WINDOW_COUNTER",
  "SLIDING_WINDOW_LOG",
  "FIXED_WINDOW",
] as const;
