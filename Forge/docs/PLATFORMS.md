# Platform Plan

Obsidian Watch ships as separate adapters using one shared protocol and API model.

## Current adapters

| Adapter | Minecraft | Status | Notes |
|---|---:|---|---|
| NeoForge | 1.21.1 | Working reference | Current 0.2.0 build. |
| Forge | 1.20.1 | First port scaffold | Added under `platforms/forge-1.20.1`. Build and runtime testing required. |

## Planned adapters

| Adapter | Minecraft range | Priority | Notes |
|---|---:|---:|---|
| Forge legacy | 1.19.2, 1.18.2, 1.16.5 | High | Needs version-band ports. Forge 1.16.5 is a separate legacy target, not a NeoForge build. |
| Bukkit / Spigot / Paper | 1.16.5 → latest | High | One Bukkit-family plugin can cover most of this. |
| Velocity | Proxy | High | Proxy-side notify/kick/deny and action logging. |
| BungeeCord / Waterfall | Proxy | Medium | Bungee-family adapter. |
| Fabric | Version bands | Medium | Needs Fabric API/event handling differences. |
| Quilt | Version bands | Medium | Likely close to Fabric but still separate release. |
| SpongeForge | Version bands | Later | Dedicated adapter after Forge version bands are stable. |

## Release strategy

Do not build one universal jar for every loader. Build shared core logic and platform-specific adapters.

Recommended release names:

```txt
obsidian-watch-neoforge-1.21.1-0.2.0.jar
obsidian-watch-forge-1.20.1-1.0.0-pre1.jar
obsidian-watch-forge-1.16.5-1.0.0-pre1.jar
obsidian-watch-paper-1.16.5-plus-1.0.0-pre1.jar
obsidian-watch-velocity-1.0.0-pre1.jar
obsidian-watch-bungee-1.0.0-pre1.jar
```
