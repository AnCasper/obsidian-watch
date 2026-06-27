package com.morethancore.obsidianwatch.bukkit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ObsidianWatchListener implements Listener {
    private final ObsidianWatchService service;

    public ObsidianWatchListener(ObsidianWatchService service) {
        this.service = service;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        service.handleJoin(event.getPlayer());
    }
}
