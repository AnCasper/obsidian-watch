# Forge 1.20.1 Adapter

## Why this target first

Forge 1.20.1 is the closest Forge target to the current NeoForge 1.21.1 reference build. It lets the project prove the Forge adapter without immediately dragging the code through the older 1.16.5 API swamp.

## Location

```txt
platforms/forge-1.20.1
```

## Build

```powershell
cd "C:\Users\CASte\OneDrive\Desktop\Obsidian-Watch\platforms\forge-1.20.1"

& "$env:USERPROFILE\Downloads\gradle-portable\gradle-8.10.2\bin\gradle.bat" clean build
```

## Expected jar

```txt
platforms\forge-1.20.1\build\libs\obsidian_watch-1.0.0-pre1+forge.1.20.1.jar
```

## Test order

```mcfunction
/ow
/ow help
/ow status
/ow diagnostics
/ow config
/ow sync
/ow check <uuid> <username>
/ow exempt list
/ow testlog <uuid> <username> notify
```

## Runtime features intended for this first Forge port

- Server start/stop lifecycle
- Config file generation
- API heartbeat
- Remote config sync
- Snapshot sync
- `/ow` command family
- Player join lookup
- Operator notification
- Kick
- Tempban
- Ban dry-run
- Real ban safety gate
- Action logs
- Diagnostics logs

## Known limits

This is not the Forge 1.16.5 port. Forge 1.16.5 needs a separate legacy adapter because the Gradle setup, Java target, events, mappings, and text APIs differ enough to justify a version-band build.
