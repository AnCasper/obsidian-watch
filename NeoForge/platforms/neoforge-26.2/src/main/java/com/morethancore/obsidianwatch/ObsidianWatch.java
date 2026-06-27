package com.morethancore.obsidianwatch;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ObsidianWatch.MOD_ID)
public final class ObsidianWatch {
    public static final String MOD_ID = "obsidian_watch";

    private final ObsidianWatchService service = new ObsidianWatchService();

    public ObsidianWatch(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, OwConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(service::onServerStarted);
        NeoForge.EVENT_BUS.addListener(service::onServerStopping);
        NeoForge.EVENT_BUS.addListener(service::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(ObsidianWatchCommands::onRegisterCommands);

        ObsidianWatchCommands.setService(service);
    }
}
