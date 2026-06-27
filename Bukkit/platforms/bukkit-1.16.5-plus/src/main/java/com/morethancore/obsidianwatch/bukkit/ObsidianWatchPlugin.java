package com.morethancore.obsidianwatch.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ObsidianWatchPlugin extends JavaPlugin {
    private ObsidianWatchService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        service = new ObsidianWatchService(this);
        service.start();

        ObsidianWatchCommand command = new ObsidianWatchCommand(service);
        if (getCommand("ow") != null) {
            getCommand("ow").setExecutor(command);
            getCommand("ow").setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(new ObsidianWatchListener(service), this);
        getLogger().info("Obsidian Watch Bukkit adapter enabled.");
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.stop();
            service = null;
        }
        getLogger().info("Obsidian Watch Bukkit adapter disabled.");
    }
}
