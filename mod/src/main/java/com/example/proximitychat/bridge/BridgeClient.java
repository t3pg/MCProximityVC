package com.example.proximitychat.bridge;

import com.example.proximitychat.ProximityChatMod;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BridgeClient {

    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int READ_TIMEOUT_MS = 2000;
    private static final long STATUS_CHECK_INTERVAL_SEC = 30;

    private final String baseUrl;
    private volatile boolean available = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ProximityVC-Status-Checker");
        t.setDaemon(true);
        return t;
    });

    public BridgeClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        ProximityChatMod.LOGGER.info("[ProximityVC] BridgeClient initialized on port {}", port);
    }

    public void setVolume(String discordUserId, int volume) {
        Thread.ofVirtual().name("ProximityVC-Volume").start(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("userId", discordUserId);
                json.addProperty("volume", volume);
                int code = sendPost("/volume", json.toString());
                if (code != 200) {
                    ProximityChatMod.LOGGER.warn("[ProximityVC] setVolume response {}: userId={} vol={}", code, discordUserId, volume);
                } else {
                    ProximityChatMod.LOGGER.debug("[ProximityVC] setVolume ok: userId={} vol={}", discordUserId, volume);
                }
            } catch (Exception e) {
                ProximityChatMod.LOGGER.warn("[ProximityVC] setVolume failed: {}", e.getMessage());
            }
        });
    }

    public boolean checkStatus() {
        try {
            var url = URI.create(baseUrl + "/status").toURL();
            var conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            available = (code == 200);
        } catch (Exception e) {
            available = false;
        }
        return available;
    }

    public boolean isAvailable() {
        return available;
    }

    public void startStatusCheckLoop() {
        // Immediate first check (no chat notification — player is not in-world yet)
        scheduler.execute(() -> {
            checkStatus();
            ProximityChatMod.LOGGER.info("[ProximityVC] Bridge status on startup: {}", available ? "connected" : "not reachable");
        });

        // Periodic recheck: notify player only on state change
        scheduler.scheduleAtFixedRate(() -> {
            boolean was = available;
            boolean now = checkStatus();
            if (!was && now) {
                ProximityChatMod.LOGGER.info("[ProximityVC] Bridge connection established.");
                notifyPlayer("[ProximityVC] Bridge connected.");
            } else if (was && !now) {
                ProximityChatMod.LOGGER.warn("[ProximityVC] Bridge connection lost.");
                notifyPlayer("[ProximityVC] Bridge not reachable. Start proximityvc-bridge.");
            }
        }, STATUS_CHECK_INTERVAL_SEC, STATUS_CHECK_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private void notifyPlayer(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(message));
            }
        });
    }

    private int sendPost(String path, String jsonBody) throws Exception {
        var url = URI.create(baseUrl + path).toURL();
        var conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }
}
