'use strict';

const { Client } = require('@xhayper/discord-rpc');
const fs = require('fs');
const path = require('path');

const CONFIG_FILE = path.join(process.isBun ? path.dirname(process.execPath) : __dirname, 'config.json');
const TOKEN_FILE = path.join(process.isBun ? path.dirname(process.execPath) : __dirname, 'token.json');
const SCOPES = ['rpc', 'rpc.voice.read', 'rpc.voice.write'];
const RECONNECT_DELAY_MS = 30_000;

let client = null;
let connected = false;
let reconnectTimer = null;

function loadConfig() {
  return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
}

function loadCachedToken() {
  if (!fs.existsSync(TOKEN_FILE)) return null;
  try {
    return JSON.parse(fs.readFileSync(TOKEN_FILE, 'utf-8')).accessToken ?? null;
  } catch {
    return null;
  }
}

function saveToken(accessToken) {
  fs.writeFileSync(TOKEN_FILE, JSON.stringify({ accessToken }, null, 2));
}

function clearToken() {
  if (fs.existsSync(TOKEN_FILE)) fs.unlinkSync(TOKEN_FILE);
}

function scheduleReconnect() {
  if (reconnectTimer) return;
  console.log(`[ProximityVC] Discord disconnected. Reconnecting in ${RECONNECT_DELAY_MS / 1000}s...`);
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connect().catch((err) => console.error('[ProximityVC] Reconnect error:', err.message));
  }, RECONNECT_DELAY_MS);
}

async function connect() {
  const config = loadConfig();
  const cachedToken = loadCachedToken();

  if (client) {
    try { client.destroy(); } catch {}
    client = null;
  }
  connected = false;

  client = new Client({ clientId: config.clientId, clientSecret: config.clientSecret });

  client.on('disconnected', () => {
    connected = false;
    scheduleReconnect();
  });

  try {
    if (cachedToken) {
      await client.login({ accessToken: cachedToken });
    } else {
      await client.login({
        scopes: SCOPES,
        clientSecret: config.clientSecret,
        redirectUri: 'http://localhost',
      });
      if (client.accessToken) saveToken(client.accessToken);
    }
    connected = true;
    console.log('[ProximityVC] Discord connected and authenticated.');
  } catch (err) {
    connected = false;

    if (cachedToken) {
      console.error('[ProximityVC] Cached token is invalid. Clearing and retrying full auth...');
      clearToken();
      // Use scheduleReconnect so the reconnectTimer guard prevents a concurrent
      // 'disconnected' event from also scheduling a connect().
      scheduleReconnect();
    } else {
      console.error('[ProximityVC] Authentication failed:', err.message);
      console.error('[ProximityVC] Ensure Discord is running and config.json is correct.');
      scheduleReconnect();
    }
  }
}

async function setVolume(userId, volume) {
  if (!connected || !client?.user) throw new Error('not connected');
  await client.user.setVoiceSettings({ user_id: userId, volume });
}

function isConnected() {
  return connected;
}

module.exports = { connect, setVolume, isConnected };
