import { invoke } from '@tauri-apps/api/core';
import type { DashboardSnapshot, ServiceTier, TunnelOptions } from '../types';

export function isTauriRuntime(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;
}

export const desktopApi = {
  getState: (): Promise<DashboardSnapshot> => invoke('get_dashboard_state'),

  updateSettings: (payload: {
    serviceTier?: ServiceTier;
    options?: TunnelOptions;
    autostart?: boolean;
    adsConsent?: boolean;
    licenseApiBase?: string;
  }): Promise<DashboardSnapshot> => invoke('update_settings', payload),

  importServerUris: (rawText: string, tier: ServiceTier): Promise<DashboardSnapshot> =>
    invoke('import_server_uris', { rawText, tier }),

  syncSignedServers: (subscriptionUrl?: string): Promise<DashboardSnapshot> =>
    invoke('sync_signed_servers', { subscriptionUrl: subscriptionUrl || null }),

  probeAllServers: (): Promise<DashboardSnapshot> => invoke('probe_all_servers'),

  connectVpn: (serverId?: string): Promise<DashboardSnapshot> =>
    invoke('connect_vpn', { serverId: serverId || null }),

  disconnectVpn: (): Promise<DashboardSnapshot> => invoke('disconnect_vpn'),

  activateVipLicense: (code: string): Promise<DashboardSnapshot> =>
    invoke('activate_vip_license', { code }),

  removeServer: (serverId: string): Promise<DashboardSnapshot> =>
    invoke('remove_server', { serverId }),

  clearLogs: (): Promise<DashboardSnapshot> => invoke('clear_logs'),
};
