package com.morethancore.obsidianwatch.bungee;

import net.md_5.bungee.api.plugin.Plugin;

public final class ObsidianWatchBungeePlugin extends Plugin {
    private ObsidianWatchBungeeService service;

    @Override
    public void onEnable() {
        service = new ObsidianWatchBungeeService(this);
        service.start();

        getProxy().getPluginManager().registerCommand(this, new BungeeWatchCommand(service));
        getProxy().getPluginManager().registerListener(this, new BungeeWatchListener(service));

        getLogger().info("Obsidian Watch Bungee/Waterfall adapter enabled.");
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.stop();
            service = null;
        }
        getLogger().info("Obsidian Watch Bungee/Waterfall adapter disabled.");
    }
}
