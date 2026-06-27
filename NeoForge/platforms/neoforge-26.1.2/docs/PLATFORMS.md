# Platform Plan

Obsidian Watch should be shipped as separate adapters using one shared protocol and API model.

## Current adapter

- NeoForge 26.1.2

## Planned adapters

- Bukkit / Spigot / Paper 1.16+
- Velocity
- BungeeCord / Waterfall
- Forge version bands
- Fabric version bands
- Quilt
- SpongeForge

## Release strategy

Do not build one universal jar for every loader. Build shared core logic and platform-specific adapters.
