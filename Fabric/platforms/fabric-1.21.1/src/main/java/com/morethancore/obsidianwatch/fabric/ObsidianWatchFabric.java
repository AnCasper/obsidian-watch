package com.morethancore.obsidianwatch.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public final class ObsidianWatchFabric implements ModInitializer {
    public static final String MOD_ID = "obsidianwatch";

    private static ObsidianWatchFabricService service;

    @Override
    public void onInitialize() {
        service = new ObsidianWatchFabricService();

        ServerLifecycleEvents.SERVER_STARTED.register(new ServerLifecycleEvents.ServerStarted() {
            @Override
            public void onServerStarted(MinecraftServer server) {
                service.start(server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(new ServerLifecycleEvents.ServerStopping() {
            @Override
            public void onServerStopping(MinecraftServer server) {
                service.stop();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            service.handleJoin(handler.player);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FabricWatchCommands.register(dispatcher, service);
        });
    }
}
