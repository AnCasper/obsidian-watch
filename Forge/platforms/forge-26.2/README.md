# Obsidian Watch - Forge 26.2

Standalone Forge 26.2 adapter scaffold for Obsidian Watch.

## Target

```txt
Minecraft: 26.2
Forge: 65.0.1
Java: 25
Gradle: 9.1.0
```

## Build

From repository root:

```powershell
.\scripts\bootstrap-gradle-9.ps1

$env:OW_JAVA_25_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot"

.\scripts\build-forge-26.2.ps1
```

Expected output:

```txt
Forge\platforms\forge-26.2\build\libs\obsidian_watch-1.0.0-pre1+forge.26.2.jar
```

## Test commands

```mcfunction
/ow
/ow help
/ow status
/ow diagnostics
/ow config
/ow sync
/ow check <uuid> <username>
/ow testlog <uuid> notify
```

This is a first-pass 26.x scaffold. Expect compile-time API drift, because Minecraft versioning apparently needed a boss fight.
