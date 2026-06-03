'use strict';

const express = require('express');
const fs = require('fs');
const path = require('path');
const rpc = require('./discordRpc');

const CONFIG_FILE = path.join(__dirname, 'config.json');

function loadAndValidateConfig() {
  let config;
  try {
    config = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
  } catch (err) {
    console.error('[ProximityVC] Failed to read config.json:', err.message);
    process.exit(1);
  }

  if (!config.clientId || config.clientId === 'YOUR_CLIENT_ID' ||
      !config.clientSecret || config.clientSecret === 'YOUR_CLIENT_SECRET') {
    console.error('[ProximityVC] config.json is not configured. Set clientId and clientSecret from your Discord Developer Portal.');
    process.exit(1);
  }

  return config;
}

function startHttpServer(port) {
  const app = express();
  app.use(express.json());

  app.post('/volume', async (req, res) => {
    const { userId, volume } = req.body ?? {};

    if (typeof userId !== 'string' || typeof volume !== 'number') {
      return res.status(400).json({ ok: false, error: 'invalid fields' });
    }
    const vol = Math.max(0, Math.min(200, Math.round(volume)));

    try {
      await rpc.setVolume(userId, vol);
      res.json({ ok: true });
    } catch (err) {
      res.status(503).json({ ok: false, error: err.message });
    }
  });

  app.get('/status', (_req, res) => {
    res.json({ connected: rpc.isConnected() });
  });

  return new Promise((resolve, reject) => {
    app.listen(port, '127.0.0.1', () => {
      console.log(`[ProximityVC] HTTP server listening on 127.0.0.1:${port}`);
      resolve();
    }).on('error', reject);
  });
}

async function main() {
  const config = loadAndValidateConfig();
  await startHttpServer(config.port ?? 7878);
  await rpc.connect();
}

main();
