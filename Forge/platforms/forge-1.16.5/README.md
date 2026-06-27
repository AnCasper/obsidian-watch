# Obsidian Watch - Forge 1.16.5

Legacy Forge 1.16.5 adapter for Obsidian Watch.

## Important

This is a legacy source port scaffold. Minecraft/Forge 1.16.5 has older ForgeGradle and API differences, so the first build may expose compatibility errors that need targeted fixes.

## Build

From repository root:

```powershell
.\scripts\bootstrap-gradle-7.ps1

$env:OW_JAVA_17_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"

.\scripts\build-forge-1.16.5.ps1
```

Expected output:

```txt
Forge\platforms\forge-1.16.5\build\libs\obsidian_watch-1.0.0-pre1+forge.1.16.5.jar
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
