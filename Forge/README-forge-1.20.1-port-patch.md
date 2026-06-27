Obsidian Watch Forge 1.20.1 port scaffold

Changed / added files:
- platforms/forge-1.20.1/settings.gradle
- platforms/forge-1.20.1/build.gradle
- platforms/forge-1.20.1/gradle.properties
- platforms/forge-1.20.1/README.md
- platforms/forge-1.20.1/src/main/java/com/morethancore/obsidianwatch/ObsidianWatch.java
- platforms/forge-1.20.1/src/main/java/com/morethancore/obsidianwatch/ObsidianWatchCommands.java
- platforms/forge-1.20.1/src/main/java/com/morethancore/obsidianwatch/ObsidianWatchService.java
- platforms/forge-1.20.1/src/main/java/com/morethancore/obsidianwatch/ObsidianWatchText.java
- platforms/forge-1.20.1/src/main/java/com/morethancore/obsidianwatch/OwConfig.java
- platforms/forge-1.20.1/src/main/java/com/morethancore/obsidianwatch/WatchEntry.java
- platforms/forge-1.20.1/src/main/resources/META-INF/mods.toml
- platforms/forge-1.20.1/src/main/resources/pack.mcmeta
- docs/PLATFORMS.md
- docs/FORGE_1_20_1.md
- scripts/build-forge-1.20.1.ps1

What this is:
- A standalone Forge 1.20.1 adapter project under platforms/forge-1.20.1.
- It ports the working NeoForge 0.2.0 command and service behavior to Forge APIs.
- It uses Forge 47.4.10 and Java 17.
- It does not modify the working NeoForge 1.21.1 build.

Next:
- Build locally.
- Fix any Forge compile/runtime API differences if they appear.
- Test on a clean Forge 1.20.1 dedicated server.
