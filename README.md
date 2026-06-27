# Obsidian Watch
## READ: Due to the nature of how fast this was built, I have had zero time to test all of them on each platform. If you run into any issues submit them! Thanks <3

![Status](https://img.shields.io/badge/status-preview_release-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.16.5--latest-44cc44)
![Forge](https://img.shields.io/badge/Forge-supported-2ea44f)
![NeoForge](https://img.shields.io/badge/NeoForge-supported-2ea44f)
![Fabric](https://img.shields.io/badge/Fabric-supported-2ea44f)
![Quilt](https://img.shields.io/badge/Quilt-compatible-2ea44f)
![Paper](https://img.shields.io/badge/Paper%2FSpigot%2FBukkit-supported-2ea44f)
![Velocity](https://img.shields.io/badge/Velocity-supported-2ea44f)
![BungeeCord](https://img.shields.io/badge/Bungee%2FWaterfall-supported-2ea44f)
![Sponge](https://img.shields.io/badge/Sponge_API_12-supported-2ea44f)
![Java](https://img.shields.io/badge/Java-8%20%7C%2017%20%7C%2021%20%7C%2025-orange)

Check out the Obssidian Watch website, create an account, register your server and then create an API key that you can paste into your generated config on your server.

Address: https://obsidianwatch.app/

Obsidian Watch is a cross-platform moderation sync system for Minecraft servers and networks.

It lets trusted server owners connect their server to the Obsidian Watch API, sync watchlist/confirmed player data, and apply configurable enforcement actions such as notifications, kicks, temporary bans, dry-run bans, and gated permanent bans.

The project is designed around safe defaults. Real permanent bans are disabled unless explicitly enabled.

---

## Supported platforms

| Platform                        | Status | Notes                       |
| ------------------------------- | -----: | --------------------------- |
| Forge 1.16.5                    |      ✅ | Legacy Forge support        |
| Forge 1.18.2                    |      ✅ | Supported                   |
| Forge 1.19.2                    |      ✅ | Supported                   |
| Forge 1.20.1                    |      ✅ | Supported                   |
| Forge 1.21.11                   |      ✅ | Supported                   |
| Forge 26.1.2                    |      ✅ | Modern Forge line           |
| Forge 26.2                      |      ✅ | Modern Forge line           |
| NeoForge 1.21.1                 |      ✅ | Supported                   |
| NeoForge 26.1.2                 |      ✅ | Modern NeoForge line        |
| NeoForge 26.2                   |      ✅ | Modern NeoForge line        |
| Bukkit / Spigot / Paper 1.16.5+ |      ✅ | Server plugin               |
| Velocity 3.4+                   |      ✅ | Proxy plugin                |
| BungeeCord / Waterfall 1.16+    |      ✅ | Proxy plugin                |
| Fabric 1.21.1                   |      ✅ | Server-side mod             |
| Quilt                           |      ✅ | Compatible via Fabric build |
| Sponge API 12                   |      ✅ | Sponge plugin               |

---

## Features

* API key authentication
* Server heartbeat
* Remote config sync
* Watchlist and confirmed-list sync
* Join-time enforcement
* Staff notifications
* Kick enforcement
* Temporary ban/block support
* Ban dry-run mode
* Gated permanent ban support
* Action logging
* Diagnostics commands
* Local UUID exemptions
* Proxy-side UUID blocking for Velocity and Bungee/Waterfall
* Sponge-side local UUID blocking

---

## Safety defaults

Obsidian Watch ships with conservative enforcement defaults:

```properties
watchlistAction=NOTIFY
confirmedAction=BAN_DRY_RUN
allowRealBans=false
```

Permanent bans require explicit configuration:

```properties
allowRealBans=true
confirmedAction=BAN
```

Do not enable permanent bans until diagnostics, sync, notifications, kicks, and temporary bans have been tested.

---

## Commands

Most platforms support:

```mcfunction
/ow
/ow help
/ow status
/ow diagnostics
/ow config
/ow reload
/ow sync
/ow heartbeat
/ow check <uuid|cached-username> [username]
/ow testlog <uuid|cached-username> <action> [username]
/ow exempt list
/ow exempt add <uuid>
/ow exempt remove <uuid>
```

Some proxy/platform builds also include:

```mcfunction
/ow blocks
```

---

## Permissions

```txt
obsidianwatch.admin
obsidianwatch.notify
obsidianwatch.exempt
```

---

## Source layout

```txt
Forge/       Forge platform builds
NeoForge/    NeoForge platform builds
Fabric/      Fabric server-side build
Bukkit/      Bukkit, Spigot, and Paper plugin
Velocity/    Velocity proxy plugin
Bungee/      BungeeCord and Waterfall proxy plugin
Sponge/      Sponge API 12 plugin
docs/        Install notes, release notes, and platform matrix
release/     Generated local release output
```

Generated build outputs and release zips are not intended to be committed to the repository.

---

## Release layout

Release packages are grouped by platform:

```txt
release/obsidian-watch-1.0.0-pre1/
  forge/
  neoforge/
  bukkit-spigot-paper/
  proxy/
  fabric/
  quilt/
  sponge/
  docs/
  README.md
  MANIFEST.txt
  manifest.json
```

Each platform folder contains the matching jar and a short install note.

---

## Install summary

Use the jar that matches your platform.

```txt
Forge / NeoForge / Fabric / Quilt:
  mods/

Bukkit / Spigot / Paper:
  plugins/

Velocity:
  plugins/

BungeeCord / Waterfall:
  plugins/

Sponge:
  plugins/
```

Start the server once, edit the generated Obsidian Watch config, then run:

```mcfunction
/ow diagnostics
/ow sync
/ow status
```

---

## Licensing

Obsidian Watch uses a split licensing model.

Minecraft adapters are licensed under Apache-2.0:

```txt
Forge/
NeoForge/
Fabric/
Bukkit/
Velocity/
Bungee/
Sponge/
```

The hosted platform, API, dashboard, database, report system, appeal system, and related backend service code remain protected unless separately licensed.

---

## Project status

Obsidian Watch is currently in preview release.

The core focus is stable multi-platform support, safe enforcement defaults, reliable diagnostics, and clean integration with the Obsidian Watch API.
