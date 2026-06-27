package com.morethancore.obsidianwatch;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(ObsidianWatch.MOD_ID)
public final class ObsidianWatch {
    public static final String MOD_ID = "obsidian_watch";

    private final ObsidianWatchService service = new ObsidianWatchService();

    public ObsidianWatch() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, OwConfig.SPEC);

        MinecraftForge.EVENT_BUS.addListener(service::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(service::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(service::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(ObsidianWatchCommands::onRegisterCommands);

        ObsidianWatchCommands.setService(service);
    }
}
