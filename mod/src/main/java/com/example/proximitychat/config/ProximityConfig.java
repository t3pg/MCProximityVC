package com.example.proximitychat.config;

import com.example.proximitychat.ProximityChatMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProximityConfig {

    private int bridgePort = 7878;
    private float maxDistance = 32.0f;
    private float minDistance = 2.0f;
    private String falloffType = "LINEAR";
    private int updateIntervalTicks = 10;
    private int maxVolume = 200;
    private int minVolume = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ProximityConfig load() {
        Path configDir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("proximitychat");
        Path file = configDir.resolve("proximity_config.json");

        if (!Files.exists(file)) {
            ProximityConfig defaults = new ProximityConfig();
            try {
                Files.createDirectories(configDir);
                Files.writeString(file, GSON.toJson(defaults));
                ProximityChatMod.LOGGER.info("[ProximityVC] Created default proximity_config.json");
            } catch (IOException e) {
                ProximityChatMod.LOGGER.warn("[ProximityVC] Could not write default config: {}", e.getMessage());
            }
            return defaults;
        }

        try {
            String json = Files.readString(file);
            ProximityConfig cfg = GSON.fromJson(json, ProximityConfig.class);
            if (cfg == null) throw new JsonSyntaxException("null result");
            ProximityChatMod.LOGGER.info("[ProximityVC] Config loaded: port={} maxDist={} minDist={} falloff={} interval={}t maxVol={} minVol={}",
                    cfg.bridgePort, cfg.maxDistance, cfg.minDistance,
                    cfg.falloffType, cfg.updateIntervalTicks, cfg.maxVolume, cfg.minVolume);
            return cfg;
        } catch (IOException | JsonSyntaxException e) {
            ProximityChatMod.LOGGER.error("[ProximityVC] Failed to parse proximity_config.json: {}", e.getMessage());
            return new ProximityConfig();
        }
    }

    public void reload() {
        Path file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("proximitychat")
                .resolve("proximity_config.json");
        try {
            String json = Files.readString(file);
            ProximityConfig fresh = GSON.fromJson(json, ProximityConfig.class);
            if (fresh == null) throw new JsonSyntaxException("null result");
            this.bridgePort = fresh.bridgePort;
            this.maxDistance = fresh.maxDistance;
            this.minDistance = fresh.minDistance;
            this.falloffType = fresh.falloffType;
            this.updateIntervalTicks = fresh.updateIntervalTicks;
            this.maxVolume = fresh.maxVolume;
            this.minVolume = fresh.minVolume;
            ProximityChatMod.LOGGER.info("[ProximityVC] Config reloaded: port={} maxDist={} minDist={} falloff={} interval={}t maxVol={} minVol={}",
                    this.bridgePort, this.maxDistance, this.minDistance,
                    this.falloffType, this.updateIntervalTicks, this.maxVolume, this.minVolume);
        } catch (IOException | JsonSyntaxException e) {
            ProximityChatMod.LOGGER.error("[ProximityVC] Failed to reload proximity_config.json: {}", e.getMessage());
        }
    }

    public int getBridgePort() { return bridgePort; }
    public float getMaxDistance() { return maxDistance; }
    public float getMinDistance() { return minDistance; }
    public String getFalloffType() { return falloffType; }
    public int getUpdateIntervalTicks() { return updateIntervalTicks; }
    public int getMaxVolume() { return maxVolume; }
    public int getMinVolume() { return minVolume; }
}
