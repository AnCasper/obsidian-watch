# Obsidian Watch - Forge 1.18.2

Standalone Forge 1.18.2 adapter for Obsidian Watch.

## Build

From repository root:

```powershell
$env:OW_JAVA_17_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
.\scripts\build-forge-1.18.2.ps1
```

Or directly:

```powershell
cd "C:\Users\CASte\OneDrive\Desktop\Obsidian-Watch\Forge\platforms\forge-1.18.2"

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

& "C:\Users\CASte\OneDrive\Desktop\Obsidian-Watch\.tools\gradle-8.10.2\bin\gradle.bat" clean build --no-daemon
```

Expected output:

```txt
Forge\platforms\forge-1.18.2\build\libs\obsidian_watch-1.0.0-pre1+forge.1.18.2.jar
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
