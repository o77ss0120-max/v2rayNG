export type ServiceTier = 'DEDICATED' | 'GHOST' | 'PUBLIC';
export type FragmentMode = 'NORMAL' | 'SMART' | 'GHOST' | 'OFF';

export interface ServerNode {
  id: string;
  label: string;
  protocol: string;
  host: string;
  port: number;
  rawUri: string;
  latencyMs: number | null;
  tier: string;
}

export interface TunnelOptions {
  tunMode: boolean;
  cleanDns: boolean;
  splitTunnelLocal: boolean;
  fragmentMode: FragmentMode;
}

export interface DashboardSnapshot {
  appVersion: string;
  deviceId: string;
  userUuid: string;
  connected: boolean;
  connectedSinceMs: number;
  activeServer: ServerNode | null;
  serviceTier: ServiceTier;
  licenseApiBase: string;
  vipActive: boolean;
  vipSerial: string;
  vipExpireMs: number;
  vipUsedBytes: number;
  vipMaxBytes: number;
  dailyUsedBytes: number;
  dailyMaxBytes: number;
  adsConsent: boolean;
  autostart: boolean;
  socksPort: number;
  httpPort: number;
  options: TunnelOptions;
  servers: ServerNode[];
  selectedServerId: string | null;
  logs: string[];
}
