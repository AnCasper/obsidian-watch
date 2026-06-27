package com.morethancore.obsidianwatch.bungee;

import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public final class BungeeWatchListener implements Listener {
    private final ObsidianWatchBungeeService service;

    public BungeeWatchListener(ObsidianWatchBungeeService service) {
        this.service = service;
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        service.handlePostLogin(event.getPlayer());
    }
}
