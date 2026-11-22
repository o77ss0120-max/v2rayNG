import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity,
  Check,
  Copy,
  KeyRound,
  Plus,
  Power,
  RefreshCw,
  Search,
  Server,
  Settings,
  Shield,
  Trash2,
  Wifi,
} from 'lucide-react';
import { desktopApi, isTauriRuntime } from './api/tauri';
import type { DashboardSnapshot, FragmentMode, ServerNode, ServiceTier } from './types';

type NavTab = 'connection' | 'servers' | 'license' | 'settings';

const PREVIEW_SNAPSHOT: DashboardSnapshot = {
  appVersion: '2.9.8',
  deviceId: '0000000000000000',
  userUuid: '00000000-0000-4000-8000-000000000000',
  connected: false,
  connectedSinceMs: 0,
  activeServer: null,
  serviceTier: 'DEDICATED',
  licenseApiBase: 'https://penny-license.workers.dev',
  vipActive: false,
  vipSerial: '',
  vipExpireMs: 0,
  vipUsedBytes: 0,
  vipMaxBytes: 50 * 1024 * 1024 * 1024,
  dailyUsedBytes: 0,
  dailyMaxBytes: 1024 * 1024 * 1024,
  adsConsent: false,
  autostart: false,
  socksPort: 10808,
  httpPort: 10809,
  options: {
    tunMode: false,
    cleanDns: true,
    splitTunnelLocal: true,
    fragmentMode: 'NORMAL',
  },
  servers: [],
  selectedServerId: null,
  logs: ['Browser preview active — launch inside Penny VPN Windows Tauri runtime for Xray & System Proxy.'],
};

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 MB';
  const gb = bytes / (1024 * 1024 * 1024);
  if (gb >= 1) return `${gb.toFixed(2)} GB`;
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(1)} MB`;
}

function formatDuration(sinceMs: number, nowMs: number): string {
  if (!sinceMs || nowMs <= sinceMs) return '00:00:00';
  const totalSec = Math.floor((nowMs - sinceMs) / 1000);
  const hrs = Math.floor(totalSec / 3600);
  const mins = Math.floor((totalSec % 3600) / 60);
  const secs = totalSec % 60;
  return [hrs, mins, secs].map((v) => String(v).padStart(2, '0')).join(':');
}

function formatDate(ms: number): string {
  if (!ms) return '—';
  try {
    return new Date(ms).toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return '—';
  }
}

export default function App() {
  const nativeRuntime = isTauriRuntime();
  const [tab, setTab] = useState<NavTab>('connection');
  const [state, setState] = useState<DashboardSnapshot>(PREVIEW_SNAPSHOT);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ kind: 'error' | 'ok'; text: string } | null>(null);

  // Servers view controls
  const [searchQuery, setSearchQuery] = useState('');
  const [showImport, setShowImport] = useState(false);
  const [importText, setImportText] = useState('');
  const [customSubUrl, setCustomSubUrl] = useState('');

  // License view controls
  const [licenseInput, setLicenseInput] = useState('');
  const [apiBaseInput, setApiBaseInput] = useState(PREVIEW_SNAPSHOT.licenseApiBase);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const loadState = useCallback(async () => {
    if (!nativeRuntime) return;
    try {
      const snap = await desktopApi.getState();
      setState(snap);
      setApiBaseInput(snap.licenseApiBase);
    } catch (err) {
      setFeedback({ kind: 'error', text: String(err) });
    }
  }, [nativeRuntime]);

  useEffect(() => {
    void loadState();
  }, [loadState]);

  useEffect(() => {
    if (!state.connected) return;
    const id = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [state.connected]);

  const runAction = useCallback(
    async (actionName: string, fn: () => Promise<DashboardSnapshot>, successMsg?: string) => {
      if (!nativeRuntime) {
        setFeedback({
          kind: 'error',
          text: 'Native Windows Tauri runtime is required to control Xray and system proxy.',
        });
        return;
      }
      setBusyAction(actionName);
      setFeedback(null);
      try {
        const next = await fn();
        setState(next);
        setApiBaseInput(next.licenseApiBase);
        if (successMsg) {
          setFeedback({ kind: 'ok', text: successMsg });
        }
      } catch (err) {
        setFeedback({ kind: 'error', text: String(err) });
      } finally {
        setBusyAction(null);
      }
    },
    [nativeRuntime]
  );

  const handleCopy = useCallback((key: string, value: string) => {
    if (!value) return;
    void navigator.clipboard?.writeText(value);
    setCopiedKey(key);
    window.setTimeout(() => setCopiedKey((prev) => (prev === key ? null : prev)), 1600);
  }, []);

  const handleToggleConnection = useCallback(() => {
    if (state.connected) {
      void runAction('disconnect', () => desktopApi.disconnectVpn(), 'Disconnected and system proxy restored.');
    } else {
      void runAction(
        'connect',
        () => desktopApi.connectVpn(state.selectedServerId || undefined),
        'Tunnel established via Xray-core.'
      );
    }
  }, [runAction, state.connected, state.selectedServerId]);

  const handleTierChange = useCallback(
    (serviceTier: ServiceTier) => {
      void runAction('settings', () => desktopApi.updateSettings({ serviceTier }));
    },
    [runAction]
  );

  const handleOptionChange = useCallback(
    (patch: Partial<DashboardSnapshot['options']>) => {
      void runAction('settings', () =>
        desktopApi.updateSettings({
          options: { ...state.options, ...patch },
        })
      );
    },
    [runAction, state.options]
  );

  const handleImportSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!importText.trim()) return;
      await runAction(
        'import',
        () => desktopApi.importServerUris(importText, state.serviceTier),
        'Server nodes imported.'
      );
      setImportText('');
      setShowImport(false);
    },
    [importText, runAction, state.serviceTier]
  );

  const handleActivateLicense = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const trimmed = licenseInput.trim();
      if (!trimmed) return;
      await runAction(
        'license',
        () => desktopApi.activateVipLicense(trimmed),
        'VIP license verified and activated.'
      );
      setLicenseInput('');
    },
    [licenseInput, runAction]
  );

  const handleSaveApiBase = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const trimmed = apiBaseInput.trim();
      if (!trimmed.startsWith('https://')) {
        setFeedback({ kind: 'error', text: 'License server endpoint must use HTTPS.' });
        return;
      }
      await runAction(
        'api-base',
        () => desktopApi.updateSettings({ licenseApiBase: trimmed }),
        'License server URL saved.'
      );
    },
    [apiBaseInput, runAction]
  );

  const filteredServers = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return state.servers;
    return state.servers.filter(
      (s) =>
        s.label.toLowerCase().includes(q) ||
        s.host.toLowerCase().includes(q) ||
        s.protocol.toLowerCase().includes(q) ||
        s.tier.toLowerCase().includes(q)
    );
  }, [searchQuery, state.servers]);

  const activeNode: ServerNode | null =
    state.activeServer ??
    state.servers.find((s) => s.id === state.selectedServerId) ??
    state.servers[0] ??
    null;

  const usedBytes = state.vipActive ? state.vipUsedBytes : state.dailyUsedBytes;
  const maxBytes = state.vipActive ? state.vipMaxBytes : state.dailyMaxBytes;
  const quotaPercent = maxBytes > 0 ? Math.min(100, (usedBytes / maxBytes) * 100) : 0;

  const navItems: { id: NavTab; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
    { id: 'connection', label: 'Connection', icon: Shield },
    { id: 'servers', label: 'Servers', icon: Server },
    { id: 'license', label: 'License', icon: KeyRound },
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  const pageTitle = navItems.find((n) => n.id === tab)?.label ?? 'Connection';

  return (
    <div className="flex h-screen w-screen bg-[#0D0F14] text-[#F1F3F8] select-none overflow-hidden">
      {/* Left Navigation Sidebar */}
      <aside className="w-[236px] shrink-0 border-r border-[#242836] bg-[#10131A] flex flex-col justify-between">
        <div>
          <div className="h-14 px-5 flex items-center gap-2.5 border-b border-[#242836]">
            <div className="w-7 h-7 rounded-md bg-[#E6CFAF]/15 border border-[#E6CFAF]/40 flex items-center justify-center shrink-0">
              <Shield className="w-4 h-4 text-[#E6CFAF]" />
            </div>
            <div className="min-w-0">
              <div className="text-sm font-semibold tracking-tight text-[#F1F3F8] whitespace-nowrap">
                Penny VPN
              </div>
              <div className="text-[11px] font-mono text-[#8B93A7] tabular-nums">
                Windows · v{state.appVersion}
              </div>
            </div>
          </div>

          <nav className="p-3 space-y-1">
            {navItems.map((item) => {
              const Icon = item.icon;
              const active = tab === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setTab(item.id)}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-md text-sm font-medium transition-colors cursor-pointer ${
                    active
                      ? 'bg-[#1A1F2B] text-[#E6CFAF]'
                      : 'text-[#A0A8BC] hover:bg-[#161A24] hover:text-[#F1F3F8]'
                  }`}
                >
                  <Icon className="w-4 h-4 shrink-0" />
                  <span className="truncate">{item.label}</span>
                  {item.id === 'servers' && state.servers.length > 0 && (
                    <span className="ml-auto text-xs font-mono text-[#7C849A] tabular-nums">
                      {state.servers.length}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>

        {/* Sidebar Footer Summary */}
        <div className="p-4 border-t border-[#242836] space-y-2 text-xs">
          <div className="flex items-center justify-between text-[#8B93A7]">
            <span>Session</span>
            <span className={state.connected ? 'text-[#5FD19A] font-medium' : 'text-[#8B93A7]'}>
              {state.connected ? 'Protected' : 'Idle'}
            </span>
          </div>
          <div className="flex items-center justify-between text-[#8B93A7]">
            <span>Plan</span>
            <span className={state.vipActive ? 'text-[#E6CFAF] font-medium' : 'text-[#CBD2E1]'}>
              {state.vipActive ? 'VIP 50 GB' : 'Free 1 GB/day'}
            </span>
          </div>
          <div className="flex items-center justify-between text-[#8B93A7]">
            <span>Mode</span>
            <span className="text-[#CBD2E1] font-mono">
              {state.options.tunMode ? 'TUN' : `HTTP :${state.httpPort}`}
            </span>
          </div>
        </div>
      </aside>

      {/* Main Workspace */}
      <div className="flex-1 min-w-0 flex flex-col">
        {/* Top Bar */}
        <header className="h-14 shrink-0 px-6 border-b border-[#242836] bg-[#0D0F14] flex items-center justify-between gap-4">
          <div className="flex items-center gap-2 text-sm min-w-0">
            <span className="text-[#7C849A]">Penny VPN</span>
            <span className="text-[#3C4357]">/</span>
            <h1 className="font-semibold text-[#F1F3F8] truncate">{pageTitle}</h1>
          </div>

          <button
            type="button"
            disabled={busyAction === 'connect' || busyAction === 'disconnect'}
            onClick={handleToggleConnection}
            className={`px-4 py-1.5 rounded-md text-xs font-semibold tracking-wide transition-colors whitespace-nowrap shrink-0 flex items-center gap-2 cursor-pointer disabled:opacity-50 ${
              state.connected
                ? 'bg-[#2A1D21] text-[#F28B82] border border-[#5B2E36] hover:bg-[#362228]'
                : 'bg-[#E6CFAF] text-[#0D0F14] hover:bg-[#F0DEC4]'
            }`}
          >
            <Power className="w-3.5 h-3.5" />
            {busyAction === 'connect'
              ? 'Connecting...'
              : busyAction === 'disconnect'
              ? 'Disconnecting...'
              : state.connected
              ? 'Disconnect'
              : 'Connect'}
          </button>
        </header>

        {/* Scrollable Content Canvas */}
        <main className="flex-1 overflow-y-auto px-6 py-6">
          <div className="max-w-4xl mx-auto space-y-6">
            {!nativeRuntime && (
              <div className="border border-[#3E3524] bg-[#191610] px-4 py-3 rounded-lg text-xs text-[#E6CFAF]">
                Web preview detected. Build and launch the Windows Tauri desktop binary to control Xray-core and Windows system proxy settings.
              </div>
            )}

            {feedback && (
              <div
                className={`px-4 py-3 rounded-lg border text-xs flex items-center justify-between gap-3 ${
                  feedback.kind === 'error'
                    ? 'bg-[#1D1417] border-[#4A252C] text-[#F28B82]'
                    : 'bg-[#121D18] border-[#234334] text-[#7AD9A8]'
                }`}
              >
                <span className="break-all">{feedback.text}</span>
                <button
                  type="button"
                  onClick={() => setFeedback(null)}
                  className="text-[11px] underline shrink-0 cursor-pointer"
                >
                  Dismiss
                </button>
              </div>
            )}

            {/* ───────────────────────── CONNECTION TAB ───────────────────────── */}
            {tab === 'connection' && (
              <>
                <section className="bg-[#141720] border border-[#242836] rounded-lg p-6">
                  <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                    <div className="space-y-2 min-w-0">
                      <div className="text-xs font-medium text-[#8B93A7]">
                        {state.connected ? 'Tunnel active' : 'Tunnel standby'}
                      </div>
                      <div className="text-2xl font-semibold tracking-tight text-[#F1F3F8] truncate">
                        {activeNode ? activeNode.label : 'No server loaded'}
                      </div>
                      <div className="text-xs text-[#9AA2B7] font-mono tabular-nums flex flex-wrap items-center gap-x-2 gap-y-1">
                        {activeNode ? (
                          <>
                            <span>{activeNode.protocol.toUpperCase()}</span>
                            <span>·</span>
                            <span>
                              {activeNode.host}:{activeNode.port}
                            </span>
                            <span>·</span>
                            <span>
                              {activeNode.latencyMs !== null ? `${activeNode.latencyMs} ms` : 'Not probed'}
                            </span>
                          </>
                        ) : (
                          <span>Sync the signed server list or import a URI in the Servers tab.</span>
                        )}
                      </div>
                    </div>

                    <button
                      type="button"
                      disabled={busyAction === 'connect' || busyAction === 'disconnect'}
                      onClick={handleToggleConnection}
                      className={`h-14 px-7 rounded-lg font-semibold text-sm transition-colors whitespace-nowrap shrink-0 flex items-center justify-center gap-2.5 cursor-pointer disabled:opacity-50 ${
                        state.connected
                          ? 'bg-[#251A1E] text-[#F28B82] border border-[#5B2E36] hover:bg-[#312026]'
                          : 'bg-[#E6CFAF] text-[#0D0F14] hover:bg-[#F0DEC4]'
                      }`}
                    >
                      <Power className="w-4 h-4" />
                      {state.connected ? 'Disconnect VPN' : 'Connect VPN'}
                    </button>
                  </div>

                  {/* Runtime Readout Strip */}
                  <div className="mt-6 pt-5 border-t border-[#242836] grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
                    <div>
                      <div className="text-[#7C849A]">Session Timer</div>
                      <div className="mt-1 font-mono text-sm text-[#F1F3F8] tabular-nums">
                        {state.connected ? formatDuration(state.connectedSinceMs, nowMs) : '00:00:00'}
                      </div>
                    </div>
                    <div>
                      <div className="text-[#7C849A]">Local Proxy Endpoints</div>
                      <div className="mt-1 font-mono text-sm text-[#F1F3F8] tabular-nums">
                        SOCKS :{state.socksPort} · HTTP :{state.httpPort}
                      </div>
                    </div>
                    <div>
                      <div className="text-[#7C849A]">Active Protection</div>
                      <div className="mt-1 text-sm text-[#F1F3F8]">
                        {state.options.fragmentMode === 'OFF'
                          ? 'Standard TLS'
                          : `TLS Fragment (${state.options.fragmentMode})`}
                      </div>
                    </div>
                  </div>
                </section>

                {/* Routing & Tier Controls */}
                <section className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="bg-[#141720] border border-[#242836] rounded-lg p-5 space-y-3">
                    <div>
                      <h2 className="text-sm font-semibold text-[#F1F3F8]">Service Tier</h2>
                      <p className="text-xs text-[#8B93A7] mt-0.5">
                        Dedicated requires an active VIP key; Public and Ghost use signed mirrors.
                      </p>
                    </div>
                    <div className="grid grid-cols-3 gap-2">
                      {(['DEDICATED', 'GHOST', 'PUBLIC'] as ServiceTier[]).map((tier) => {
                        const selected = state.serviceTier === tier;
                        return (
                          <button
                            key={tier}
                            type="button"
                            onClick={() => handleTierChange(tier)}
                            className={`py-2 px-3 rounded-md text-xs font-medium border transition-colors cursor-pointer ${
                              selected
                                ? 'bg-[#E6CFAF]/15 border-[#E6CFAF]/60 text-[#E6CFAF]'
                                : 'bg-[#0F1219] border-[#242836] text-[#A0A8BC] hover:text-[#F1F3F8]'
                            }`}
                          >
                            {tier === 'DEDICATED' ? 'Dedicated' : tier === 'GHOST' ? 'Ghost' : 'Public'}
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <div className="bg-[#141720] border border-[#242836] rounded-lg p-5 space-y-3">
                    <div>
                      <h2 className="text-sm font-semibold text-[#F1F3F8]">Windows Routing Mode</h2>
                      <p className="text-xs text-[#8B93A7] mt-0.5">
                        System Proxy needs no elevation; TUN Mode routes all TCP/UDP apps (requires Admin).
                      </p>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() => handleOptionChange({ tunMode: false })}
                        className={`py-2 px-3 rounded-md text-xs font-medium border transition-colors cursor-pointer ${
                          !state.options.tunMode
                            ? 'bg-[#E6CFAF]/15 border-[#E6CFAF]/60 text-[#E6CFAF]'
                            : 'bg-[#0F1219] border-[#242836] text-[#A0A8BC] hover:text-[#F1F3F8]'
                        }`}
                      >
                        System Proxy
                      </button>
                      <button
                        type="button"
                        onClick={() => handleOptionChange({ tunMode: true })}
                        className={`py-2 px-3 rounded-md text-xs font-medium border transition-colors cursor-pointer ${
                          state.options.tunMode
                            ? 'bg-[#E6CFAF]/15 border-[#E6CFAF]/60 text-[#E6CFAF]'
                            : 'bg-[#0F1219] border-[#242836] text-[#A0A8BC] hover:text-[#F1F3F8]'
                        }`}
                      >
                        TUN Mode (Admin)
                      </button>
                    </div>
                  </div>
                </section>

                {/* Quota Allowance */}
                <section className="bg-[#141720] border border-[#242836] rounded-lg p-5 space-y-3">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-sm text-[#F1F3F8]">
                      {state.vipActive ? 'VIP Allowance' : 'Daily Free Allowance'}
                    </span>
                    <span className="font-mono text-[#CBD2E1] tabular-nums">
                      {formatBytes(usedBytes)} / {formatBytes(maxBytes)}
                    </span>
                  </div>
                  <div className="h-2 w-full rounded-full bg-[#0D0F14] overflow-hidden border border-[#242836]">
                    <div
                      className="h-full bg-[#E6CFAF] transition-all"
                      style={{ width: `${quotaPercent}%` }}
                    />
                  </div>
                  <div className="flex items-center justify-between text-xs text-[#8B93A7]">
                    <span>
                      {state.vipActive
                        ? `Serial ${state.vipSerial} · Expires ${formatDate(state.vipExpireMs)}`
                        : 'Activate a VIP license key for 50 GB monthly allowance and zero sponsor banners.'}
                    </span>
                    {!state.vipActive && (
                      <button
                        type="button"
                        onClick={() => setTab('license')}
                        className="text-[#E6CFAF] hover:underline whitespace-nowrap cursor-pointer"
                      >
                        Upgrade to VIP
                      </button>
                    )}
                  </div>
                </section>

                {/* Consent-Gated Sponsor Banner (Free Tier Only) */}
                {!state.vipActive && (
                  <section className="bg-[#141720] border border-[#242836] rounded-lg p-5 space-y-3">
                    <div className="flex items-center justify-between gap-4">
                      <div>
                        <h2 className="text-sm font-semibold text-[#F1F3F8]">Community Infrastructure Support</h2>
                        <p className="text-xs text-[#8B93A7] mt-0.5">
                          Sponsor frames only load after explicit opt-in consent and never expose device identifiers.
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() =>
                          void runAction('consent', () =>
                            desktopApi.updateSettings({ adsConsent: !state.adsConsent })
                          )
                        }
                        className={`px-3 py-1.5 rounded-md text-xs font-medium border whitespace-nowrap shrink-0 cursor-pointer ${
                          state.adsConsent
                            ? 'bg-[#E6CFAF]/15 border-[#E6CFAF]/50 text-[#E6CFAF]'
                            : 'bg-[#0F1219] border-[#242836] text-[#A0A8BC] hover:text-[#F1F3F8]'
                        }`}
                      >
                        {state.adsConsent ? 'Sponsor Enabled' : 'Enable Sponsor Banner'}
                      </button>
                    </div>

                    {state.adsConsent && (
                      <div className="pt-2 flex flex-wrap justify-center gap-4">
                        <iframe
                          title="Penny VPN Sponsor Banner"
                          src="https://acceptable.a-ads.com/2462374/?size=Adaptive"
                          sandbox="allow-scripts allow-same-origin allow-popups"
                          className="w-full max-w-[468px] h-[64px] border border-[#242836] rounded bg-[#0D0F14]"
                        />
                      </div>
                    )}
                  </section>
                )}
              </>
            )}

            {/* ───────────────────────── SERVERS TAB ───────────────────────── */}
            {tab === 'servers' && (
              <section className="space-y-4">
                {/* Action Toolbar */}
                <div className="flex flex-wrap items-center gap-2.5">
                  <div className="relative flex-1 min-w-[200px]">
                    <Search className="w-4 h-4 text-[#7C849A] absolute left-3 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder="Filter servers by label, host, or protocol..."
                      className="w-full pl-9 pr-3 py-2 rounded-md bg-[#141720] border border-[#242836] text-xs text-[#F1F3F8] placeholder-[#6F778C] focus:outline-none focus:border-[#E6CFAF]/60"
                    />
                  </div>

                  <button
                    type="button"
                    disabled={busyAction !== null}
                    onClick={() =>
                      void runAction(
                        'sync',
                        () => desktopApi.syncSignedServers(customSubUrl.trim() || undefined),
                        'ECDSA-signed server list verified and synced.'
                      )
                    }
                    className="px-3.5 py-2 rounded-md bg-[#E6CFAF] text-[#0D0F14] text-xs font-semibold hover:bg-[#F0DEC4] whitespace-nowrap shrink-0 flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${busyAction === 'sync' ? 'animate-spin' : ''}`} />
                    {busyAction === 'sync' ? 'Syncing...' : 'Sync Signed List'}
                  </button>

                  <button
                    type="button"
                    disabled={busyAction !== null || state.servers.length === 0}
                    onClick={() =>
                      void runAction(
                        'probe',
                        () => desktopApi.probeAllServers(),
                        'TCP latency probe finished.'
                      )
                    }
                    className="px-3.5 py-2 rounded-md bg-[#141720] border border-[#242836] text-[#CBD2E1] text-xs font-medium hover:border-[#3A4157] whitespace-nowrap shrink-0 flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                  >
                    <Activity className="w-3.5 h-3.5" />
                    {busyAction === 'probe' ? 'Probing...' : 'Probe Latency'}
                  </button>

                  <button
                    type="button"
                    onClick={() => setShowImport((v) => !v)}
                    className="px-3.5 py-2 rounded-md bg-[#141720] border border-[#242836] text-[#CBD2E1] text-xs font-medium hover:border-[#3A4157] whitespace-nowrap shrink-0 flex items-center gap-1.5 cursor-pointer"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    Import URI
                  </button>
                </div>

                {/* Optional Signed Subscription URL */}
                <div className="bg-[#141720] border border-[#242836] rounded-lg px-4 py-3 flex flex-wrap items-center gap-3">
                  <label htmlFor="sub-url" className="text-xs text-[#8B93A7] whitespace-nowrap">
                    Signed List URL (optional)
                  </label>
                  <input
                    id="sub-url"
                    type="url"
                    value={customSubUrl}
                    onChange={(e) => setCustomSubUrl(e.target.value)}
                    placeholder="Default official Penny ECDSA signed mirror (HTTPS)"
                    className="flex-1 min-w-[240px] px-3 py-1.5 rounded bg-[#0D0F14] border border-[#242836] text-xs font-mono text-[#F1F3F8] placeholder-[#60687C] focus:outline-none focus:border-[#E6CFAF]/60"
                  />
                </div>

                {/* Collapsible Manual URI Importer */}
                {showImport && (
                  <form
                    onSubmit={handleImportSubmit}
                    className="bg-[#141720] border border-[#242836] rounded-lg p-4 space-y-3"
                  >
                    <div className="text-xs font-medium text-[#CBD2E1]">
                      Paste one or more share URIs (`vless://`, `vmess://`, `trojan://`, `ss://`)
                    </div>
                    <textarea
                      rows={4}
                      value={importText}
                      onChange={(e) => setImportText(e.target.value)}
                      placeholder="vless://uuid@example.com:443?security=reality&sni=..."
                      className="w-full p-3 rounded-md bg-[#0D0F14] border border-[#242836] text-xs font-mono text-[#F1F3F8] placeholder-[#5F667A] focus:outline-none focus:border-[#E6CFAF]/60"
                    />
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => setShowImport(false)}
                        className="px-3 py-1.5 rounded-md text-xs text-[#8B93A7] hover:text-[#F1F3F8] cursor-pointer"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={busyAction !== null || !importText.trim()}
                        className="px-4 py-1.5 rounded-md bg-[#E6CFAF] text-[#0D0F14] text-xs font-semibold hover:bg-[#F0DEC4] cursor-pointer disabled:opacity-50"
                      >
                        Import Nodes
                      </button>
                    </div>
                  </form>
                )}

                {/* High-Density Server Table */}
                <div className="bg-[#141720] border border-[#242836] rounded-lg overflow-hidden">
                  {filteredServers.length === 0 ? (
                    <div className="p-10 text-center space-y-2">
                      <Wifi className="w-6 h-6 text-[#646D82] mx-auto" />
                      <div className="text-sm font-medium text-[#CBD2E1]">No servers available</div>
                      <p className="text-xs text-[#8B93A7]">
                        Click “Sync Signed List” to download ECDSA-authenticated nodes or paste URIs via “Import URI”.
                      </p>
                    </div>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-left border-collapse text-xs">
                        <thead>
                          <tr className="border-b border-[#242836] text-[#7C849A] bg-[#10131B]">
                            <th className="py-2.5 px-4 font-medium">Node</th>
                            <th className="py-2.5 px-3 font-medium">Protocol</th>
                            <th className="py-2.5 px-3 font-medium">Endpoint</th>
                            <th className="py-2.5 px-3 font-medium text-right">Latency</th>
                            <th className="py-2.5 px-4 font-medium text-right">Actions</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-[#1E2230]">
                          {filteredServers.map((node) => {
                            const isCurrent = state.activeServer?.id === node.id && state.connected;
                            const isSelected = state.selectedServerId === node.id;
                            return (
                              <tr
                                key={node.id}
                                className={`transition-colors ${
                                  isCurrent
                                    ? 'bg-[#E6CFAF]/8'
                                    : isSelected
                                    ? 'bg-[#191E2B]'
                                    : 'hover:bg-[#171B26]'
                                }`}
                              >
                                <td className="py-3 px-4 font-medium text-[#F1F3F8]">
                                  <div className="truncate max-w-[220px]">{node.label}</div>
                                  <div className="text-[11px] text-[#7C849A]">{node.tier}</div>
                                </td>
                                <td className="py-3 px-3 font-mono uppercase text-[#CBD2E1]">
                                  {node.protocol}
                                </td>
                                <td className="py-3 px-3 font-mono text-[#9AA2B7] tabular-nums">
                                  {node.host}:{node.port}
                                </td>
                                <td className="py-3 px-3 font-mono text-right tabular-nums">
                                  {node.latencyMs === null ? (
                                    <span className="text-[#6B7388]">—</span>
                                  ) : (
                                    <span
                                      className={
                                        node.latencyMs < 250
                                          ? 'text-[#5FD19A]'
                                          : node.latencyMs < 600
                                          ? 'text-[#E6CFAF]'
                                          : 'text-[#F28B82]'
                                      }
                                    >
                                      {node.latencyMs} ms
                                    </span>
                                  )}
                                </td>
                                <td className="py-3 px-4 text-right whitespace-nowrap">
                                  <div className="inline-flex items-center gap-2">
                                    <button
                                      type="button"
                                      disabled={busyAction !== null}
                                      onClick={() =>
                                        void runAction(
                                          'connect',
                                          () => desktopApi.connectVpn(node.id),
                                          `Connected to ${node.label}.`
                                        )
                                      }
                                      className={`px-2.5 py-1 rounded text-[11px] font-semibold cursor-pointer ${
                                        isCurrent
                                          ? 'bg-[#1F352A] text-[#7AD9A8]'
                                          : 'bg-[#E6CFAF]/15 text-[#E6CFAF] hover:bg-[#E6CFAF]/25'
                                      }`}
                                    >
                                      {isCurrent ? 'Active' : 'Connect'}
                                    </button>
                                    <button
                                      type="button"
                                      aria-label={`Remove ${node.label}`}
                                      disabled={busyAction !== null}
                                      onClick={() =>
                                        void runAction('remove', () => desktopApi.removeServer(node.id))
                                      }
                                      className="p-1.5 rounded text-[#7C849A] hover:text-[#F28B82] hover:bg-[#23191D] cursor-pointer"
                                    >
                                      <Trash2 className="w-3.5 h-3.5" />
                                    </button>
                                  </div>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </section>
            )}

            {/* ───────────────────────── LICENSE TAB ───────────────────────── */}
            {tab === 'license' && (
              <div className="space-y-4">
                {/* Hardware Identity */}
                <section className="bg-[#141720] border border-[#242836] rounded-lg p-5 space-y-4">
                  <div>
                    <h2 className="text-sm font-semibold text-[#F1F3F8]">Windows Device Identity</h2>
                    <p className="text-xs text-[#8B93A7] mt-0.5">
                      Provide your 64-bit Device ID when requesting a device-bound ECDSA P-256 VIP key.
                    </p>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div className="bg-[#0D0F14] border border-[#242836] rounded-md px-3.5 py-2.5 flex items-center justify-between gap-2">
                      <div className="min-w-0">
                        <div className="text-[11px] text-[#7C849A]">Device ID (64-bit)</div>
                        <div className="font-mono text-xs text-[#E6CFAF] truncate tabular-nums">
                          {state.deviceId}
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleCopy('deviceId', state.deviceId)}
                        className="p-1.5 rounded text-[#8B93A7] hover:text-[#F1F3F8] cursor-pointer shrink-0"
                        title="Copy Device ID"
                      >
                        {copiedKey === 'deviceId' ? (
                          <Check className="w-4 h-4 text-[#5FD19A]" />
                        ) : (
                          <Copy className="w-4 h-4" />
                        )}
                      </button>
                    </div>

                    <div className="bg-[#0D0F14] border border-[#242836] rounded-md px-3.5 py-2.5 flex items-center justify-between gap-2">
                      <div className="min-w-0">
                        <div className="text-[11px] text-[#7C849A]">User UUID</div>
                        <div className="font-mono text-xs text-[#CBD2E1] truncate tabular-nums">
                          {state.userUuid}
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleCopy('userUuid', state.userUuid)}
                        className="p-1.5 rounded text-[#8B93A7] hover:text-[#F1F3F8] cursor-pointer shrink-0"
                        title="Copy User UUID"
                      >
                        {copiedKey === 'userUuid' ? (
                          <Check className="w-4 h-4 text-[#5FD19A]" />
                        ) : (
                          <Copy className="w-4 h-4" />
                        )}
                      </button>
                    </div>
                  </div>
                </section>

                {/* Activate VIP Key */}
                <section className="bg-[#141720] border border-[#242836] rounded-lg p-5 space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h2 className="text-sm font-semibold text-[#F1F3F8]">Activate VIP License</h2>
                      <p className="text-xs text-[#8B93A7] mt-0.5">
                        Supports signed `PL1.` ECDSA P-256 tokens compatible with Penny VPN Android & Windows.
                      </p>
                    </div>
                    <span className="text-xs font-mono text-[#E6CFAF]">
                      {state.vipActive ? 'VIP Active' : 'Free Tier'}
                    </span>
                  </div>

                  <form onSubmit={handleActivateLicense} className="flex flex-col sm:flex-row gap-2.5">
                    <input
                      type="text"
                      value={licenseInput}
                      onChange={(e) => setLicenseInput(e.target.value)}
                      placeholder="PL1.<payload>.<ecdsa_signature>"
                      className="flex-1 px-3.5 py-2 rounded-md bg-[#0D0F14] border border-[#242836] text-xs font-mono text-[#F1F3F8] placeholder-[#5F667A] focus:outline-none focus:border-[#E6CFAF]/60"
                    />
                    <button
                      type="submit"
                      disabled={busyAction !== null || !licenseInput.trim()}
                      className="px-5 py-2 rounded-md bg-[#E6CFAF] text-[#0D0F14] text-xs font-semibold hover:bg-[#F0DEC4] whitespace-nowrap shrink-0 cursor-pointer disabled:opacity-50"
                    >
                      {busyAction === 'license' ? 'Verifying...' : 'Activate Key'}
                    </button>
                  </form>

                  {state.vipActive && (
                    <div className="pt-3 border-t border-[#242836] grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
                      <div>
                        <div className="text-[#7C849A]">Serial</div>
                        <div className="mt-1 font-mono text-[#F1F3F8]">{state.vipSerial}</div>
                      </div>
                      <div>
                        <div className="text-[#7C849A]">Expires</div>
                        <div className="mt-1 font-mono text-[#F1F3F8]">{formatDate(state.vipExpireMs)}</div>
                      </div>
                      <div>
                        <div className="text-[#7C849A]">Allowance Used</div>
                        <div className="mt-1 font-mono text-[#F1F3F8] tabular-nums">
                          {formatBytes(state.vipUsedBytes)} / {formatBytes(state.vipMaxBytes)}
                        </div>
                      </div>
                    </div>
                  )}
                </section>

                {/* License API Endpoint */}
                <section className="bg-[#141720] border border-[#242836] rounded-lg p-5 space-y-3">
                  <div>
                    <h2 className="text-sm font-semibold text-[#F1F3F8]">License Verification Server</h2>
                    <p className="text-xs text-[#8B93A7] mt-0.5">
                      HTTPS Cloudflare Worker or dedicated API endpoint used for online license binding and quota sync.
                    </p>
                  </div>
                  <form onSubmit={handleSaveApiBase} className="flex flex-col sm:flex-row gap-2.5">
                    <input
                      type="url"
                      value={apiBaseInput}
                      onChange={(e) => setApiBaseInput(e.target.value)}
                      placeholder="https://license.yourdomain.com"
                      className="flex-1 px-3.5 py-2 rounded-md bg-[#0D0F14] border border-[#242836] text-xs font-mono text-[#F1F3F8] focus:outline-none focus:border-[#E6CFAF]/60"
                    />
                    <button
                      type="submit"
                      disabled={busyAction !== null}
                      className="px-4 py-2 rounded-md bg-[#1B202D] border border-[#2D3448] text-xs font-medium text-[#F1F3F8] hover:border-[#495370] whitespace-nowrap shrink-0 cursor-pointer disabled:opacity-50"
                    >
                      Save Endpoint
                    </button>
                  </form>
                </section>
              </div>
            )}

            {/* ───────────────────────── SETTINGS TAB ───────────────────────── */}
            {tab === 'settings' && (
              <div className="space-y-4">
                <section className="bg-[#141720] border border-[#242836] rounded-lg divide-y divide-[#242836]">
                  {/* Autostart */}
                  <div className="p-5 flex items-center justify-between gap-4">
                    <div>
                      <div className="text-sm font-medium text-[#F1F3F8]">Launch on Windows Startup</div>
                      <div className="text-xs text-[#8B93A7] mt-0.5">
                        Registers Penny VPN in HKCU Run so it stays ready in the system tray.
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() =>
                        void runAction('autostart', () =>
                          desktopApi.updateSettings({ autostart: !state.autostart })
                        )
                      }
                      className={`px-3.5 py-1.5 rounded-md text-xs font-medium border whitespace-nowrap shrink-0 cursor-pointer ${
                        state.autostart
                          ? 'bg-[#E6CFAF]/15 border-[#E6CFAF]/60 text-[#E6CFAF]'
                          : 'bg-[#0D0F14] border-[#242836] text-[#8B93A7]'
                      }`}
                    >
                      {state.autostart ? 'Enabled' : 'Disabled'}
                    </button>
                  </div>

                  {/* Clean DNS */}
                  <div className="p-5 flex items-center justify-between gap-4">
                    <div>
                      <div className="text-sm font-medium text-[#F1F3F8]">Clean DNS-over-HTTPS (1.1.1.2)</div>
                      <div className="text-xs text-[#8B93A7] mt-0.5">
                        Routes DNS queries through Cloudflare Malware-Blocking DoH inside Xray.
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleOptionChange({ cleanDns: !state.options.cleanDns })}
                      className={`px-3.5 py-1.5 rounded-md text-xs font-medium border whitespace-nowrap shrink-0 cursor-pointer ${
                        state.options.cleanDns
                          ? 'bg-[#E6CFAF]/15 border-[#E6CFAF]/60 text-[#E6CFAF]'
                          : 'bg-[#0D0F14] border-[#242836] text-[#8B93A7]'
                      }`}
                    >
                      {state.options.cleanDns ? 'Enabled' : 'Disabled'}
                    </button>
                  </div>

                  {/* Domestic Split Tunnel */}
                  <div className="p-5 flex items-center justify-between gap-4">
                    <div>
                      <div className="text-sm font-medium text-[#F1F3F8]">Bypass Domestic (.ir) & Local LAN</div>
                      <div className="text-xs text-[#8B93A7] mt-0.5">
                        Sends local network traffic and Iranian banking/domestic `.ir` domains directly.
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() =>
                        handleOptionChange({ splitTunnelLocal: !state.options.splitTunnelLocal })
                      }
                      className={`px-3.5 py-1.5 rounded-md text-xs font-medium border whitespace-nowrap shrink-0 cursor-pointer ${
                        state.options.splitTunnelLocal
                          ? 'bg-[#E6CFAF]/15 border-[#E6CFAF]/60 text-[#E6CFAF]'
                          : 'bg-[#0D0F14] border-[#242836] text-[#8B93A7]'
                      }`}
                    >
                      {state.options.splitTunnelLocal ? 'Enabled' : 'Disabled'}
                    </button>
                  </div>

                  {/* TLS Fragmentation */}
                  <div className="p-5 space-y-3">
                    <div>
                      <div className="text-sm font-medium text-[#F1F3F8]">TLS ClientHello Fragmentation</div>
                      <div className="text-xs text-[#8B93A7] mt-0.5">
                        Splits TLS handshakes into smaller TCP segments to mitigate SNI DPI filtering.
                      </div>
                    </div>
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                      {(
                        [
                          { id: 'NORMAL', label: 'Normal (100–200B)' },
                          { id: 'SMART', label: 'Smart (10–30B)' },
                          { id: 'GHOST', label: 'Ghost (5–12B)' },
                          { id: 'OFF', label: 'Off' },
                        ] as { id: FragmentMode; label: string }[]
                      ).map((mode) => (
                        <button
                          key={mode.id}
                          type="button"
                          onClick={() => handleOptionChange({ fragmentMode: mode.id })}
                          className={`py-2 px-3 rounded-md text-xs font-medium border transition-colors cursor-pointer ${
                            state.options.fragmentMode === mode.id
                              ? 'bg-[#E6CFAF]/15 border-[#E6CFAF]/60 text-[#E6CFAF]'
                              : 'bg-[#0D0F14] border-[#242836] text-[#A0A8BC] hover:text-[#F1F3F8]'
                          }`}
                        >
                          {mode.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </section>

                {/* Diagnostic Logs */}
                <section className="bg-[#141720] border border-[#242836] rounded-lg p-5 space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <div>
                      <h2 className="text-sm font-semibold text-[#F1F3F8]">Diagnostic Log</h2>
                      <p className="text-xs text-[#8B93A7] mt-0.5">
                        Local in-memory session events (zero server log uploads).
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => handleCopy('logs', state.logs.join('\n'))}
                        className="px-3 py-1.5 rounded-md bg-[#0D0F14] border border-[#242836] text-xs text-[#CBD2E1] hover:border-[#3A4157] cursor-pointer"
                      >
                        {copiedKey === 'logs' ? 'Copied' : 'Copy'}
                      </button>
                      <button
                        type="button"
                        onClick={() => void runAction('clear-logs', () => desktopApi.clearLogs())}
                        className="px-3 py-1.5 rounded-md bg-[#0D0F14] border border-[#242836] text-xs text-[#8B93A7] hover:text-[#F28B82] cursor-pointer"
                      >
                        Clear
                      </button>
                    </div>
                  </div>
                  <pre className="p-3.5 rounded-md bg-[#0B0D12] border border-[#242836] text-[11px] font-mono text-[#9AA2B7] max-h-56 overflow-y-auto whitespace-pre-wrap break-words leading-relaxed select-text">
                    {state.logs.length > 0 ? state.logs.join('\n') : 'No diagnostic entries yet.'}
                  </pre>
                </section>
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
