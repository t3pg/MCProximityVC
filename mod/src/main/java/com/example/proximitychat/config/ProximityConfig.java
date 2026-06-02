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
            ProximityConfig cfg = new Gson().fromJson(json, ProximityConfig.class);
            if (cfg == null) throw new JsonSyntaxException("null result");
            return cfg;
        } catch (IOException | JsonSyntaxException e) {
            ProximityChatMod.LOGGER.error("[ProximityVC] Failed to parse proximity_config.json: {}", e.getMessage());
            return new ProximityConfig();
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
