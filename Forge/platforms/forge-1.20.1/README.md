# Obsidian Watch Forge 1.20.1

Server-side-only Forge 1.20.1 adapter for Obsidian Watch.

This is the first Forge port target because Forge 1.20.1 is much closer to the current NeoForge reference implementation than Forge 1.16.5.

## Requirements

- Minecraft Java Edition 1.20.1 dedicated server
- Forge 47.4.10 or newer in the 1.20.1 line
- Java 17
- Obsidian Watch website with an approved server and API key

## Build

From this folder:

```powershell
& "$env:USERPROFILE\Downloads\gradle-portable\gradle-8.10.2\bin\gradle.bat" clean build
```

Expected jar:

```txt
build\libs\obsidian_watch-1.0.0-pre1+forge.1.20.1.jar
```

## Install

1. Stop the Forge 1.20.1 server.
2. Put the jar into the server `mods/` folder.
3. Start the server once to generate config.
4. Stop the server.
5. Edit `config/obsidian_watch-server.toml`.
6. Start the server again.

Minimum config:

```toml
[api]
enabled = true
baseUrl = "https://your-obsidian-watch-site.com"
apiKey = "your-live-server-api-key"
```

## Test commands

```mcfunction
/ow
/ow status
/ow diagnostics
/ow config
/ow sync
/ow check <uuid> <username>
```

## Safety

Keep real permanent bans disabled until the server has tested config sync, exemptions, cooldowns, appeal links, and disconnect messages.
