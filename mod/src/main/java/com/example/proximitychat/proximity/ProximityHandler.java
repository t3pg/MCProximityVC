package com.example.proximitychat.proximity;

import com.example.proximitychat.bridge.BridgeClient;
import com.example.proximitychat.config.PlayerIdMapper;
import com.example.proximitychat.config.ProximityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProximityHandler {

    private final BridgeClient bridgeClient;
    private final PlayerIdMapper playerIdMapper;
    private final ProximityConfig config;

    private int tickCounter = 0;
    private final Map<UUID, Integer> lastVolume = new HashMap<>();

    public ProximityHandler(BridgeClient bridgeClient, PlayerIdMapper playerIdMapper, ProximityConfig config) {
        this.bridgeClient = bridgeClient;
        this.playerIdMapper = playerIdMapper;
        this.config = config;
    }

    public void onClientTick(ClientTickEvent.Post event) {
        if (++tickCounter < config.getUpdateIntervalTicks()) return;
        tickCounter = 0;

        if (!bridgeClient.isAvailable()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        double selfX = mc.player.getX();
        double selfY = mc.player.getY();
        double selfZ = mc.player.getZ();
        UUID selfId = mc.player.getUUID();

        Set<UUID> currentlyVisible = new HashSet<>();

        for (AbstractClientPlayer other : mc.level.players()) {
            UUID otherId = other.getUUID();
            if (otherId.equals(selfId)) continue;

            String discordId = playerIdMapper.getDiscordId(otherId).orElse(null);
            if (discordId == null) continue;

            double dx = other.getX() - selfX;
            double dy = other.getY() - selfY;
            double dz = other.getZ() - selfZ;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            int volume = VolumeCalculator.calculate(distance, config);

            currentlyVisible.add(otherId);

            if (!Integer.valueOf(volume).equals(lastVolume.get(otherId))) {
                bridgeClient.setVolume(discordId, volume);
                lastVolume.put(otherId, volume);
            }
        }

        // Players who left render range: mute per spec §7-3
        Set<UUID> gone = new HashSet<>(lastVolume.keySet());
        gone.removeAll(currentlyVisible);
        for (UUID id : gone) {
            playerIdMapper.getDiscordId(id).ifPresent(discordId ->
                bridgeClient.setVolume(discordId, config.getMinVolume())
            );
            lastVolume.remove(id);
        }
    }

    public void resetAllVolumes() {
        lastVolume.forEach((uuid, vol) ->
            playerIdMapper.getDiscordId(uuid).ifPresent(discordId ->
                bridgeClient.setVolume(discordId, config.getMaxVolume())
            )
        );
        lastVolume.clear();
    }
}
