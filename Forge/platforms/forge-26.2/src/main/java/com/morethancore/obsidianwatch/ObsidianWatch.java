package com.morethancore.obsidianwatch;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

@Mod(ObsidianWatch.MOD_ID)
public final class ObsidianWatch {
    public static final String MOD_ID = "obsidian_watch";

    private final ObsidianWatchService service = new ObsidianWatchService();

    public ObsidianWatch() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, OwConfig.SPEC);

        ServerStartedEvent.BUS.addListener(service::onServerStarted);
        ServerStoppingEvent.BUS.addListener(service::onServerStopping);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(service::onPlayerLogin);
        RegisterCommandsEvent.BUS.addListener(ObsidianWatchCommands::onRegisterCommands);

        ObsidianWatchCommands.setService(service);
    }
}
