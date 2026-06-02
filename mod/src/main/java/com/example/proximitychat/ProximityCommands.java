package com.example.proximitychat;

import com.example.proximitychat.bridge.BridgeClient;
import com.example.proximitychat.config.PlayerIdMapper;
import com.example.proximitychat.config.ProximityConfig;
import com.example.proximitychat.proximity.VolumeCalculator;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class ProximityCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                BridgeClient bridgeClient,
                                PlayerIdMapper playerIdMapper,
                                ProximityConfig config) {
        dispatcher.register(
            Commands.literal("proximitychat")
                .then(Commands.literal("reload")
                    .executes(ctx -> {
                        playerIdMapper.load();
                        ctx.getSource().sendSystemMessage(Component.literal(
                            "[ProximityVC] Reloaded. Mappings: " + playerIdMapper.getMappingCount()));
                        return 1;
                    }))
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        String bridgeStatus = bridgeClient.isAvailable() ? "connected" : "not reachable";
                        ctx.getSource().sendSystemMessage(Component.literal(
                            "[ProximityVC] Bridge: " + bridgeStatus +
                            " | Mappings: " + playerIdMapper.getMappingCount()));
                        return 1;
                    }))
                .then(Commands.literal("debug")
                    .executes(ctx -> {
                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                        Vec3 pos = self.position();
                        int count = 0;
                        for (ServerPlayer other : ctx.getSource().getLevel().players()) {
                            if (other.getUUID().equals(self.getUUID())) continue;
                            double dx = other.getX() - pos.x;
                            double dy = other.getY() - pos.y;
                            double dz = other.getZ() - pos.z;
                            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                            int vol = VolumeCalculator.calculate(dist, config);
                            self.sendSystemMessage(Component.literal(String.format(
                                "[ProximityVC] %s — dist: %.1f, vol: %d",
                                other.getGameProfile().getName(), dist, vol)));
                            count++;
                        }
                        if (count == 0) {
                            self.sendSystemMessage(Component.literal(
                                "[ProximityVC] No other players in this dimension."));
                        }
                        return 1;
                    }))
        );
    }
}
