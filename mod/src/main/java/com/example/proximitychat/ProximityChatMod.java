package com.example.proximitychat;

import com.example.proximitychat.bridge.BridgeClient;
import com.example.proximitychat.config.PlayerIdMapper;
import com.example.proximitychat.config.ProximityConfig;
import com.example.proximitychat.proximity.ProximityHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(ProximityChatMod.MOD_ID)
public class ProximityChatMod {

    public static final String MOD_ID = "proximitychat";
    public static final Logger LOGGER = LogUtils.getLogger();

    private ProximityConfig config;
    private BridgeClient bridgeClient;
    private PlayerIdMapper playerIdMapper;
    private ProximityHandler proximityHandler;

    public ProximityChatMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // FMLClientSetupEvent fires on parallel loading threads; enqueueWork() runs on main thread
        event.enqueueWork(() -> {
            config = ProximityConfig.load();
            playerIdMapper = new PlayerIdMapper();
            playerIdMapper.load();
            bridgeClient = new BridgeClient(config.getBridgePort());
            proximityHandler = new ProximityHandler(bridgeClient, playerIdMapper, config);

            NeoForge.EVENT_BUS.addListener(proximityHandler::onClientTick);
            NeoForge.EVENT_BUS.addListener(this::onGameShuttingDown);
            NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

            bridgeClient.startStatusCheckLoop();
        });
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        if (bridgeClient != null) {
            ProximityCommands.register(event.getDispatcher(), bridgeClient, playerIdMapper, config);
        }
    }

    private void onGameShuttingDown(GameShuttingDownEvent event) {
        if (proximityHandler != null) {
            proximityHandler.resetAllVolumes();
        }
    }
}
