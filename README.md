# Obsidian Watch

Obsidian Watch is a Minecraft moderation intelligence platform with server-side integrations for multiple loaders and proxy/plugin platforms.

## Current source layout

```txt
Obsidian-Watch/
  NeoForge/
    build.gradle
    gradle.properties
    settings.gradle
    src/
    docs/

  Forge/
    platforms/
      forge-1.20.1/
        build.gradle
        gradle.properties
        settings.gradle
        src/

  scripts/
  docs/
```

The scripts also detect the future cleaner layout:

```txt
platforms/neoforge-1.21.1/
platforms/forge-1.20.1/
```

## Build NeoForge

```powershell
.\scripts\build-neoforge.ps1
```

## Build Forge 1.20.1

```powershell
.\scripts\build-forge-1.20.1.ps1
```

## Build everything

```powershell
.\scripts\build-all.ps1
```

## Clean everything

```powershell
.\scripts\clean-all.ps1
```

## GitHub hygiene

Commit source, docs, Gradle files, and scripts.

Do not commit:

```txt
build/
.gradle/
run/
logs/
server folders
compiled jars
API keys
config files containing live API keys
```

Compiled jars belong in GitHub Releases, not the source tree.
