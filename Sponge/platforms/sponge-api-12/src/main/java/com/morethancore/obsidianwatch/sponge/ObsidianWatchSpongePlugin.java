package com.morethancore.obsidianwatch.sponge;

import org.spongepowered.api.Server;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import com.google.inject.Inject;

@Plugin("obsidianwatch")
public final class ObsidianWatchSpongePlugin {
    private final PluginContainer container;
    private final ObsidianWatchSpongeService service;

    @Inject
    public ObsidianWatchSpongePlugin(PluginContainer container) {
        this.container = container;
        this.service = new ObsidianWatchSpongeService();
    }

    @Listener
    public void onServerStarted(final StartedEngineEvent<Server> event) {
        this.service.start(event.engine());
    }

    @Listener
    public void onServerStopping(final StoppingEngineEvent<Server> event) {
        this.service.stop();
    }

    @Listener
    public void onRegisterCommands(final RegisterCommandEvent<Command.Raw> event) {
        event.register(this.container, new SpongeWatchRawCommand(this.service), "ow", "obsidianwatch");
    }

    @Listener
    public void onPlayerJoin(final ServerSideConnectionEvent.Join event) {
        this.service.handleJoin(event.player());
    }
}
