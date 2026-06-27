# Obsidian Watch - NeoForge 26.1.2

Standalone NeoForge 26.1.2 adapter scaffold for Obsidian Watch.

## Target

```txt
Minecraft: 26.1.2
NeoForge: 26.1.2.76
Java: 25
Gradle: 9.1.0
ModDevGradle: 2.0.141
```

## Build

From repository root:

```powershell
.\scripts\bootstrap-gradle-9.ps1

$env:OW_JAVA_25_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot"

.\scripts\build-neoforge-26.1.2.ps1
```

Expected output:

```txt
NeoForge\platforms\neoforge-26.1.2\build\libs\obsidian_watch-1.0.0-pre1+neoforge.26.1.2.jar
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

This is a first-pass 26.x scaffold. Expect compile-time API drift.
