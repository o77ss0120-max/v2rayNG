// ─────────────────────────────────────────────────────────────────────────────
// Penny VPN — Secure Cloudflare Worker (VLESS-over-WS + Server-Side License & Quota API)
// تغییرات امنیتی اعمال‌شده (v2.9.4):
// 1. هیچ UUID ثابتی در کد هاردکد نشده است؛ مقدار UUID باید در Variables & Secrets کلودفلر تنظیم شود.
// 2. تمام دامنه‌های fallback شخص ثالث حذف شده‌اند؛ ProxyIP پیش‌فرض غیرفعال است.
// 3. بررسی لایسنس ECDSA P-256 و شمارش حجم ۵۰ گیگ (/api/v1/license/activate و /api/v1/license/heartbeat)
//    و همگام‌سازی سهمیه روزانه (/api/v1/quota/daily-sync) با الزام اتصال PENNY_KV (Fail-Closed).
// 4. مسدودسازی IPهای خصوصی/لوپ‌بک در VLESS جهت جلوگیری از SSRF و حذف نشت جزئیات خطای داخلی.
// ─────────────────────────────────────────────────────────────────────────────

import { connect } from 'cloudflare:sockets';

// کلید عمومی جدید ECDSA P-256 (چرخانده‌شده پس از ابطال کلید قبلی) برای بررسی امضای لایسنس‌ها روی سرور
const ROTATED_PUBLIC_KEY_B64 =
  'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEf1YXmwm/4ojudflyQNF470nc0kSAIdSM+K5OUIWqHHhxNCusYcvAygnbn9OFs6CGEpxTRB7AK6xfyyStgOE11Q==';

const DAILY_FREE_DEDICATED_MAX_BYTES = 1024 * 1024 * 1024; // 1 GB per day

export default {
  async fetch(request, env, _ctx) {
    try {
      const url = new URL(request.url);

      // ۱. هندل کردن درخواست‌های API بررسی لایسنس و شمارش حجم ۵۰ گیگ و سهمیه روزانه روی سرور
      if (url.pathname === '/api/v1/license/activate' && request.method === 'POST') {
        return await handleLicenseActivate(request, env);
      }
      if (url.pathname === '/api/v1/license/heartbeat' && request.method === 'POST') {
        return await handleLicenseHeartbeat(request, env);
      }
      if (url.pathname === '/api/v1/quota/daily-sync' && request.method === 'POST') {
        return await handleDailyQuotaSync(request, env);
      }

      // ۲. دریافت UUID از متغیر محیطی کلودفلر (هیچ UUID ثابتی در کد وجود ندارد)
      const userID = (env.UUID || '').trim();
      const proxyIP = (env.PROXYIP || '').trim();

      const upgradeHeader = request.headers.get('Upgrade');
      if (!upgradeHeader || upgradeHeader !== 'websocket') {
        if (!userID) {
          return new Response(
            JSON.stringify({
              service: 'Penny VPN Edge Worker',
              status: 'unconfigured',
              message:
                'UUID secret is not set. Go to Cloudflare Dashboard -> Worker -> Settings -> Variables and add UUID.',
            }),
            {
              status: 503,
              headers: { 'Content-Type': 'application/json;charset=utf-8' },
            }
          );
        }

        if (url.pathname === `/sub/${userID}`) {
          const host = request.headers.get('Host') || url.hostname;
          const nodes = [
            { addr: 'www.speedtest.net', port: 443, tag: 'Node-A1' },
            { addr: 'www.visa.com', port: 443, tag: 'Node-A2' },
            { addr: 'icook.tw', port: 2053, tag: 'Server-A1' },
            { addr: host, port: 443, tag: 'Server-A2' },
          ];
          const lines = nodes.map(
            (c) =>
              `vless://${userID}@${c.addr}:${c.port}?encryption=none&security=tls&sni=${host}&fp=chrome&type=ws&host=${host}&path=%2F%3Fed%3D2048#${c.tag}`
          );
          return new Response(lines.join('\n'), {
            status: 200,
            headers: { 'Content-Type': 'text/plain;charset=utf-8' },
          });
        }

        return new Response(
          JSON.stringify({
            service: 'Penny VPN Edge Worker',
            status: 'ok',
            proxyFallbackConfigured: Boolean(proxyIP),
            timestamp: new Date().toISOString(),
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json;charset=utf-8' },
          }
        );
      }

      if (!userID) {
        return new Response('Service Unavailable', { status: 503 });
      }

      return await vlessOverWSHandler(request, userID, proxyIP);
    } catch (_err) {
      return new Response(JSON.stringify({ ok: false, error: 'Internal Server Error' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json;charset=utf-8' },
      });
    }
  },
};

// ───────────────────────── بررسی لایسنس و شمارش حجم ۵۰ گیگ روی سرور (Cloudflare KV) ─────────────────────────

function derSigToP1363(derBytes) {
  if (derBytes[0] !== 0x30) return derBytes;
  let offset = 2;
  if (derBytes[1] & 0x80) offset += derBytes[1] & 0x7f;
  if (derBytes[offset] !== 0x02) return derBytes;
  const rLen = derBytes[offset + 1];
  let r = derBytes.slice(offset + 2, offset + 2 + rLen);
  offset += 2 + rLen;
  if (derBytes[offset] !== 0x02) return derBytes;
  const sLen = derBytes[offset + 1];
  let s = derBytes.slice(offset + 2, offset + 2 + sLen);
  if (r.length > 32) r = r.slice(r.length - 32);
  if (s.length > 32) s = s.slice(s.length - 32);
  const out = new Uint8Array(64);
  out.set(r, 32 - r.length);
  out.set(s, 64 - s.length);
  return out;
}

async function verifyEcdsaToken(rawCode) {
  const trimmed = String(rawCode || '').trim();
  if (!trimmed.startsWith('PL1.')) return { ok: false, message: 'Invalid license prefix' };
  const parts = trimmed.split('.');
  if (parts.length !== 3) return { ok: false, message: 'Malformed token' };

  const b64ToBytes = (b64url) => {
    const b64 = b64url.replace(/-/g, '+').replace(/_/g, '/');
    const pad = b64.length % 4 === 0 ? '' : '='.repeat(4 - (b64.length % 4));
    const bin = atob(b64 + pad);
    return Uint8Array.from(bin, (c) => c.charCodeAt(0));
  };

  const payloadBytes = b64ToBytes(parts[1]);
  const sigDer = b64ToBytes(parts[2]);
  const sigP1363 = derSigToP1363(sigDer);
  const pubKeyBytes = b64ToBytes(ROTATED_PUBLIC_KEY_B64);

  const cryptoKey = await crypto.subtle.importKey(
    'spki',
    pubKeyBytes.buffer,
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['verify']
  );

  const valid = await crypto.subtle.verify(
    { name: 'ECDSA', hash: 'SHA-256' },
    cryptoKey,
    sigP1363,
    payloadBytes
  );
  if (!valid) return { ok: false, message: 'Invalid or revoked ECDSA P-256 signature' };

  const payloadStr = new TextDecoder().decode(payloadBytes);
  const sep = payloadStr.includes('|') ? '|' : ':';
  const fields = payloadStr.split(sep);
  if (fields.length < 3) return { ok: false, message: 'Invalid payload fields' };

  const serial = fields[0].trim().toUpperCase();
  const targetDevice = fields[1].trim().toUpperCase();
  const rawDeadline = Number(fields[2].trim()) || 0;
  const activateBeforeMs = rawDeadline > 0 && rawDeadline < 10_000_000_000 ? rawDeadline * 1000 : rawDeadline;
  return { ok: true, serial, targetDevice, activateBeforeMs };
}

async function handleLicenseActivate(request, env) {
  if (!env.PENNY_KV) {
    return Response.json(
      { ok: false, active: false, message: 'Server KV storage (PENNY_KV) is not configured' },
      { status: 503 }
    );
  }

  const body = await request.json().catch(() => ({}));
  const code = String(body.code || '').trim();
  const deviceId = String(body.deviceId || '').trim().toUpperCase();
  if (!code || !deviceId) {
    return Response.json({ ok: false, message: 'Missing code or deviceId' }, { status: 400 });
  }

  const verified = await verifyEcdsaToken(code);
  if (!verified.ok) {
    return Response.json({ ok: false, message: verified.message }, { status: 403 });
  }
  if (
    verified.targetDevice !== 'ANY' &&
    verified.targetDevice !== '*' &&
    verified.targetDevice !== deviceId
  ) {
    return Response.json(
      { ok: false, message: `License is locked to Device ID ${verified.targetDevice}` },
      { status: 403 }
    );
  }

  const now = Date.now();
  const maxBytes = 50 * 1024 * 1024 * 1024; // 50 GB
  const durationMs = 30 * 24 * 3600 * 1000; // 30 Days
  const kvKey = `lic:${verified.serial}`;

  let record = null;
  const existing = await env.PENNY_KV.get(kvKey, 'json');
  if (existing) {
    if (existing.deviceId !== deviceId) {
      return Response.json(
        { ok: false, message: 'This license is already bound to another device.' },
        { status: 403 }
      );
    }
    if (now >= existing.expiresAtMs || existing.usedBytes >= existing.maxBytes) {
      return Response.json(
        { ok: false, message: 'This license has already expired or exhausted its 50 GB quota.' },
        { status: 403 }
      );
    }
    record = existing;
  }

  if (!record) {
    if (verified.activateBeforeMs > 0 && now > verified.activateBeforeMs) {
      return Response.json(
        { ok: false, message: 'License activation deadline has passed.' },
        { status: 403 }
      );
    }
    record = {
      serial: verified.serial,
      deviceId,
      activatedAtMs: now,
      expiresAtMs: now + durationMs,
      maxBytes,
      usedBytes: 0,
    };
    await env.PENNY_KV.put(kvKey, JSON.stringify(record));
  }

  return Response.json({
    ok: true,
    active: true,
    serial: record.serial,
    deviceId: record.deviceId,
    expiresAtMs: record.expiresAtMs,
    maxBytes: record.maxBytes,
    usedBytes: record.usedBytes,
    message: '1 Month 50 GB Plan verified and active on server',
  });
}

async function handleLicenseHeartbeat(request, env) {
  if (!env.PENNY_KV) {
    return Response.json(
      { ok: false, active: false, message: 'Server KV storage (PENNY_KV) is not configured' },
      { status: 503 }
    );
  }

  const body = await request.json().catch(() => ({}));
  const serial = String(body.serial || '').trim().toUpperCase();
  const deviceId = String(body.deviceId || '').trim().toUpperCase();
  const deltaBytes = Math.min(5 * 1024 * 1024 * 1024, Math.max(0, Number(body.deltaBytes || 0)));

  if (!serial || !deviceId) {
    return Response.json({ ok: false, active: false, message: 'Missing serial or deviceId' }, { status: 400 });
  }

  const kvKey = `lic:${serial}`;
  const record = await env.PENNY_KV.get(kvKey, 'json');
  if (!record || record.deviceId !== deviceId) {
    return Response.json({ ok: false, active: false, message: 'License not found for this device' }, { status: 403 });
  }

  record.usedBytes = Math.min(record.maxBytes, (record.usedBytes || 0) + deltaBytes);
  const now = Date.now();
  const active = now < record.expiresAtMs && record.usedBytes < record.maxBytes;
  await env.PENNY_KV.put(kvKey, JSON.stringify(record));

  return Response.json({
    ok: true,
    active,
    serial: record.serial,
    deviceId: record.deviceId,
    expiresAtMs: record.expiresAtMs,
    maxBytes: record.maxBytes,
    usedBytes: record.usedBytes,
    message: active ? 'Quota synced' : 'Quota or time expired',
  });
}

async function handleDailyQuotaSync(request, env) {
  if (!env.PENNY_KV) {
    return Response.json(
      { ok: false, allowed: false, error: 'Server KV storage (PENNY_KV) is not configured' },
      { status: 503 }
    );
  }

  const body = await request.json().catch(() => ({}));
  const userUuid = String(body.userUuid || '').trim().toLowerCase();
  const deviceId = String(body.deviceId || '').trim().toUpperCase();
  const localUsedBytes = Math.max(0, Number(body.localUsedBytes || 0));
  const activeMode = String(body.activeMode || 'PUBLIC').trim().toUpperCase();

  if (!userUuid || !deviceId) {
    return Response.json({ ok: false, error: 'Missing userUuid or deviceId' }, { status: 400 });
  }

  const d = new Date();
  const todayKey = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(
    d.getUTCDate()
  ).padStart(2, '0')}`;
  const kvKey = `daily:${userUuid}`;
  const existing = await env.PENNY_KV.get(kvKey, 'json');

  let dailyUsedBytes = localUsedBytes;
  if (existing && existing.dayKey === todayKey) {
    dailyUsedBytes = Math.max(Number(existing.dailyUsedBytes || 0), localUsedBytes);
  }

  const allowed = dailyUsedBytes < DAILY_FREE_DEDICATED_MAX_BYTES;
  const updated = {
    userUuid,
    deviceId,
    dayKey: todayKey,
    dailyUsedBytes,
    activeMode,
    lastSeenMs: Date.now(),
  };
  await env.PENNY_KV.put(kvKey, JSON.stringify(updated), { expirationTtl: 172800 });

  return Response.json({
    ok: true,
    dayKey: todayKey,
    dailyUsedBytes,
    maxDailyBytes: DAILY_FREE_DEDICATED_MAX_BYTES,
    remainingBytes: Math.max(0, DAILY_FREE_DEDICATED_MAX_BYTES - dailyUsedBytes),
    allowed,
  });
}

function isPrivateOrBlockedHost(host) {
  const h = String(host || '').trim().toLowerCase();
  if (!h) return true;
  if (h === 'localhost' || h.endsWith('.local') || h.endsWith('.internal')) return true;
  if (h === '::1' || h === '0:0:0:0:0:0:0:1' || h.startsWith('fc') || h.startsWith('fd') || h.startsWith('fe80:')) {
    return true;
  }
  const ipv4Match = h.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipv4Match) {
    const [a, b] = [Number(ipv4Match[1]), Number(ipv4Match[2])];
    if (a === 0 || a === 10 || a === 127) return true;
    if (a === 169 && b === 254) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && b === 168) return true;
    if (a === 100 && b >= 64 && b <= 127) return true;
  }
  return false;
}

// ───────────────────────── هسته VLESS-over-WebSocket ─────────────────────────

async function vlessOverWSHandler(request, userID, proxyIP) {
  const webSocketPair = new WebSocketPair();
  const [client, webSocket] = Object.values(webSocketPair);
  webSocket.accept();

  let address = '';
  let portWithRandomLog = '';
  const log = (info, event) => {
    console.log(`[${address}:${portWithRandomLog}] ${info}`, event || '');
  };
  const earlyDataHeader = request.headers.get('sec-websocket-protocol') || '';
  const readableWebSocketStream = makeReadableWebSocketStream(webSocket, earlyDataHeader, log);

  let remoteSocketWapper = { value: null };
  let udpStreamWrite = null;
  let isDns = false;

  readableWebSocketStream
    .pipeTo(
      new WritableStream({
        async write(chunk) {
          if (isDns && udpStreamWrite) {
            return udpStreamWrite(chunk);
          }
          if (remoteSocketWapper.value) {
            const writer = remoteSocketWapper.value.writable.getWriter();
            await writer.write(chunk);
            writer.releaseLock();
            return;
          }

          const {
            hasError,
            message,
            portRemote = 443,
            addressRemote = '',
            rawDataIndex,
            vlessVersion = new Uint8Array([0, 0]),
            isUDP,
          } = processVlessHeader(chunk, userID);

          address = addressRemote;
          portWithRandomLog = `${portRemote}--${Math.random()} ${isUDP ? 'udp ' : 'tcp '}`;

          if (hasError) {
            throw new Error(message);
          }

          if (isUDP) {
            if (portRemote === 53) {
              isDns = true;
            } else {
              throw new Error('UDP proxy is only enabled for DNS (port 53)');
            }
          }

          const vlessResponseHeader = new Uint8Array([vlessVersion[0], 0]);
          const rawClientData = chunk.slice(rawDataIndex);

          if (isDns) {
            const { write } = await handleUDPOutBound(webSocket, vlessResponseHeader, log);
            udpStreamWrite = write;
            udpStreamWrite(rawClientData);
            return;
          }

          handleTCPOutBound(
            remoteSocketWapper,
            addressRemote,
            portRemote,
            rawClientData,
            webSocket,
            vlessResponseHeader,
            log,
            proxyIP
          );
        },
        close() {
          log('readableWebSocketStream closed');
        },
        abort(reason) {
          log('readableWebSocketStream aborted', JSON.stringify(reason));
        },
      })
    )
    .catch((err) => {
      log('readableWebSocketStream pipeTo error', err);
    });

  return new Response(null, {
    status: 101,
    webSocket: client,
  });
}

async function handleTCPOutBound(
  remoteSocket,
  addressRemote,
  portRemote,
  rawClientData,
  webSocket,
  vlessResponseHeader,
  log,
  proxyIP
) {
  async function connectAndWrite(address, port) {
    const tcpSocket = connect({ hostname: address, port });
    remoteSocket.value = tcpSocket;
    log(`connected to ${address}:${port}`);
    const writer = tcpSocket.writable.getWriter();
    await writer.write(rawClientData);
    writer.releaseLock();
    return tcpSocket;
  }

  const retry =
    proxyIP && !isPrivateOrBlockedHost(proxyIP)
      ? async function () {
          const tcpSocket = await connectAndWrite(proxyIP, portRemote);
          tcpSocket.closed
            .catch((error) => {
              console.log('retry tcpSocket closed error', error);
            })
            .finally(() => {
              safeCloseWebSocket(webSocket);
            });
          remoteSocketToWS(tcpSocket, webSocket, vlessResponseHeader, null, log);
        }
      : null;

  const tcpSocket = await connectAndWrite(addressRemote, portRemote);
  remoteSocketToWS(tcpSocket, webSocket, vlessResponseHeader, retry, log);
}

function makeReadableWebSocketStream(webSocketServer, earlyDataHeader, log) {
  let readableStreamCancel = false;
  return new ReadableStream({
    start(controller) {
      webSocketServer.addEventListener('message', (event) => {
        if (readableStreamCancel) return;
        controller.enqueue(event.data);
      });
      webSocketServer.addEventListener('close', () => {
        safeCloseWebSocket(webSocketServer);
        if (readableStreamCancel) return;
        controller.close();
      });
      webSocketServer.addEventListener('error', (err) => {
        log('webSocketServer error');
        controller.error(err);
      });
      const { earlyData, error } = base64ToArrayBuffer(earlyDataHeader);
      if (error) {
        controller.error(error);
      } else if (earlyData) {
        controller.enqueue(earlyData);
      }
    },
    cancel(reason) {
      if (readableStreamCancel) return;
      log(`ReadableStream cancelled: ${reason}`);
      readableStreamCancel = true;
      safeCloseWebSocket(webSocketServer);
    },
  });
}

function processVlessHeader(vlessBuffer, userID) {
  if (vlessBuffer.byteLength < 24) {
    return { hasError: true, message: 'invalid data' };
  }
  const version = new Uint8Array(vlessBuffer.slice(0, 1));
  const slicedBuffer = new Uint8Array(vlessBuffer.slice(1, 17));
  const slicedBufferString = stringify(slicedBuffer);
  const uuids = userID.includes(',') ? userID.split(',') : [userID];
  const isValidUser = uuids.some((userUuid) => slicedBufferString === userUuid.trim().toLowerCase());
  if (!isValidUser) {
    return { hasError: true, message: 'invalid user' };
  }

  const optLength = new Uint8Array(vlessBuffer.slice(17, 18))[0];
  const command = new Uint8Array(vlessBuffer.slice(18 + optLength, 18 + optLength + 1))[0];

  let isUDP = false;
  if (command === 1) {
    isUDP = false;
  } else if (command === 2) {
    isUDP = true;
  } else {
    return { hasError: true, message: `command ${command} is not supported` };
  }

  const portIndex = 18 + optLength + 1;
  const portBuffer = vlessBuffer.slice(portIndex, portIndex + 2);
  const portRemote = new DataView(portBuffer).getUint16(0);

  let addressIndex = portIndex + 2;
  const addressBuffer = new Uint8Array(vlessBuffer.slice(addressIndex, addressIndex + 1));
  const addressType = addressBuffer[0];
  let addressLength = 0;
  let addressValueIndex = addressIndex + 1;
  let addressValue = '';

  switch (addressType) {
    case 1:
      addressLength = 4;
      addressValue = new Uint8Array(
        vlessBuffer.slice(addressValueIndex, addressValueIndex + addressLength)
      ).join('.');
      break;
    case 2:
      addressLength = new Uint8Array(
        vlessBuffer.slice(addressValueIndex, addressValueIndex + 1)
      )[0];
      addressValueIndex += 1;
      addressValue = new TextDecoder().decode(
        vlessBuffer.slice(addressValueIndex, addressValueIndex + addressLength)
      );
      break;
    case 3:
      addressLength = 16;
      const dataView = new DataView(
        vlessBuffer.slice(addressValueIndex, addressValueIndex + addressLength)
      );
      const ipv6 = [];
      for (let i = 0; i < 8; i++) {
        ipv6.push(dataView.getUint16(i * 2).toString(16));
      }
      addressValue = ipv6.join(':');
      break;
    default:
      return { hasError: true, message: `invalid addressType: ${addressType}` };
  }

  if (!addressValue || isPrivateOrBlockedHost(addressValue)) {
    return { hasError: true, message: 'target address is empty or restricted' };
  }

  return {
    hasError: false,
    addressRemote: addressValue,
    addressType,
    portRemote,
    rawDataIndex: addressValueIndex + addressLength,
    vlessVersion: version,
    isUDP,
  };
}

async function remoteSocketToWS(remoteSocket, webSocket, vlessResponseHeader, retry, log) {
  let vlessHeader = vlessResponseHeader;
  let hasIncomingData = false;
  await remoteSocket.readable
    .pipeTo(
      new WritableStream({
        async write(chunk, controller) {
          hasIncomingData = true;
          if (webSocket.readyState !== 1) {
            controller.error('webSocket is not open');
          }
          if (vlessHeader) {
            webSocket.send(await new Blob([vlessHeader, chunk]).arrayBuffer());
            vlessHeader = null;
          } else {
            webSocket.send(chunk);
          }
        },
        close() {
          log(`remoteConnection!.readable closed with hasIncomingData=${hasIncomingData}`);
        },
        abort(reason) {
          console.error('remoteConnection!.readable abort', reason);
        },
      })
    )
    .catch((error) => {
      console.error('remoteSocketToWS exception', error);
      safeCloseWebSocket(webSocket);
    });

  if (hasIncomingData === false && retry) {
    log('retry via user-configured PROXYIP');
    retry();
  }
}

function base64ToArrayBuffer(base64Str) {
  if (!base64Str) {
    return { earlyData: null, error: null };
  }
  try {
    base64Str = base64Str.replace(/-/g, '+').replace(/_/g, '/');
    const decode = atob(base64Str);
    const arryBuffer = Uint8Array.from(decode, (c) => c.charCodeAt(0));
    return { earlyData: arryBuffer.buffer, error: null };
  } catch (error) {
    return { earlyData: null, error };
  }
}

const byteToHex = [];
for (let i = 0; i < 256; ++i) {
  byteToHex.push((i + 256).toString(16).slice(1));
}

function unsafeStringify(arr, offset = 0) {
  return (
    byteToHex[arr[offset + 0]] +
    byteToHex[arr[offset + 1]] +
    byteToHex[arr[offset + 2]] +
    byteToHex[arr[offset + 3]] +
    '-' +
    byteToHex[arr[offset + 4]] +
    byteToHex[arr[offset + 5]] +
    '-' +
    byteToHex[arr[offset + 6]] +
    byteToHex[arr[offset + 7]] +
    '-' +
    byteToHex[arr[offset + 8]] +
    byteToHex[arr[offset + 9]] +
    '-' +
    byteToHex[arr[offset + 10]] +
    byteToHex[arr[offset + 11]] +
    byteToHex[arr[offset + 12]] +
    byteToHex[arr[offset + 13]] +
    byteToHex[arr[offset + 14]] +
    byteToHex[arr[offset + 15]]
  ).toLowerCase();
}

function stringify(arr, offset = 0) {
  return unsafeStringify(arr, offset);
}

async function handleUDPOutBound(webSocket, vlessResponseHeader, log) {
  let isVlessHeaderSent = false;
  const transformStream = new TransformStream({
    start() {},
    transform(chunk, controller) {
      for (let index = 0; index < chunk.byteLength; ) {
        const lengthBuffer = chunk.slice(index, index + 2);
        const udpPakcetLength = new DataView(lengthBuffer).getUint16(0);
        const udpData = new Uint8Array(chunk.slice(index + 2, index + 2 + udpPakcetLength));
        index = index + 2 + udpPakcetLength;
        controller.enqueue(udpData);
      }
    },
    flush() {},
  });

  transformStream.readable
    .pipeTo(
      new WritableStream({
        async write(chunk) {
          const resp = await fetch('https://1.1.1.1/dns-query', {
            method: 'POST',
            headers: { 'content-type': 'application/dns-message' },
            body: chunk,
          });
          const dnsQueryResult = await resp.arrayBuffer();
          const udpSize = dnsQueryResult.byteLength;
          const udpSizeBuffer = new Uint8Array([(udpSize >> 8) & 0xff, udpSize & 0xff]);
          if (webSocket.readyState === 1) {
            log(`doh success, dns message length is ${udpSize}`);
            if (isVlessHeaderSent) {
              webSocket.send(await new Blob([udpSizeBuffer, dnsQueryResult]).arrayBuffer());
            } else {
              webSocket.send(
                await new Blob([vlessResponseHeader, udpSizeBuffer, dnsQueryResult]).arrayBuffer()
              );
              isVlessHeaderSent = true;
            }
          }
        },
      })
    )
    .catch((error) => {
      log('dns udp error ' + error);
    });

  const writer = transformStream.writable.getWriter();
  return {
    write(chunk) {
      writer.write(chunk);
    },
  };
}

function safeCloseWebSocket(socket) {
  try {
    if (socket.readyState === 1 || socket.readyState === 2) {
      socket.close();
    }
  } catch (error) {
    console.error('safeCloseWebSocket error', error);
  }
}
