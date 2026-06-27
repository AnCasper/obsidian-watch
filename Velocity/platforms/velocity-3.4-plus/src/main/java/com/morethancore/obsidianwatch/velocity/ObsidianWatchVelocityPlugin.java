package com.morethancore.obsidianwatch.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "obsidianwatch",
        name = "Obsidian Watch",
        version = "1.0.0-pre1+velocity.3.4-plus",
        description = "Velocity proxy integration for Obsidian Watch.",
        authors = {"More Than Core"}
)
public final class ObsidianWatchVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private ObsidianWatchVelocityService service;

    @Inject
    public ObsidianWatchVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        service = new ObsidianWatchVelocityService(proxy, logger, dataDirectory, this);
        service.start();

        VelocityCommand command = new VelocityCommand(service);
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("ow")
                        .aliases("obsidianwatch")
                        .plugin(this)
                        .build(),
                command
        );

        proxy.getEventManager().register(this, new VelocityListener(service));
        logger.info("Obsidian Watch Velocity adapter enabled.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (service != null) {
            service.stop();
            service = null;
        }
        logger.info("Obsidian Watch Velocity adapter disabled.");
    }
}
