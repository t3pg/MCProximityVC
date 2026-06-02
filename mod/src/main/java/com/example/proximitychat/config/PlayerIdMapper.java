package com.example.proximitychat.config;

import com.example.proximitychat.ProximityChatMod;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlayerIdMapper {

    private static final Gson GSON = new Gson();

    private Map<UUID, String> cache = new HashMap<>();

    public void load() {
        Path file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("proximitychat")
                .resolve("minecraft_discord_map.json");

        if (!Files.exists(file)) {
            ProximityChatMod.LOGGER.warn("[ProximityVC] minecraft_discord_map.json not found. Creating empty template...");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "{\n  \"version\": 1,\n  \"mappings\": {}\n}\n");
                ProximityChatMod.LOGGER.warn("[ProximityVC] Fill in minecraft_discord_map.json with your Minecraft UUID -> Discord ID mappings.");
            } catch (IOException e) {
                ProximityChatMod.LOGGER.warn("[ProximityVC] Could not write template: {}", e.getMessage());
            }
            cache = new HashMap<>();
            return;
        }

        try {
            String json = Files.readString(file);
            // Phase 4: full implementation
            MapFile mapFile = GSON.fromJson(json, MapFile.class);
            if (mapFile == null || mapFile.mappings == null) {
                ProximityChatMod.LOGGER.warn("[ProximityVC] minecraft_discord_map.json is empty or malformed.");
                cache = new HashMap<>();
                return;
            }
            Map<UUID, String> newCache = new HashMap<>();
            mapFile.mappings.forEach((uuidStr, discordId) -> {
                try {
                    newCache.put(UUID.fromString(uuidStr), discordId);
                } catch (IllegalArgumentException e) {
                    ProximityChatMod.LOGGER.warn("[ProximityVC] Invalid UUID in map: {}", uuidStr);
                }
            });
            cache = newCache;
            ProximityChatMod.LOGGER.info("[ProximityVC] Loaded {} player mappings.", cache.size());
        } catch (IOException | JsonSyntaxException e) {
            ProximityChatMod.LOGGER.error("[ProximityVC] Failed to parse minecraft_discord_map.json: {}", e.getMessage());
            cache = new HashMap<>();
        }
    }

    public Optional<String> getDiscordId(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public int getMappingCount() {
        return cache.size();
    }

    private static class MapFile {
        int version;
        Map<String, String> mappings;
    }
}
