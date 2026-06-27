# Obsidian Watch NeoForge Mod - 0.2.0

Server-side-only NeoForge 1.21.1 integration for Obsidian Watch.

## 0.2.0 focus

This release cleans the command interface and documentation so the NeoForge mod can become the reference implementation for the wider Obsidian Watch platform.

## Commands

- `/ow` - show the main command menu.
- `/ow help` - show the main command menu.
- `/ow help config` - explain local and remote configuration.
- `/ow help actions` - explain supported enforcement actions.
- `/ow help exempt` - explain local exemptions.
- `/ow status` - show health summary.
- `/ow diagnostics` - show API, cache, config, and safety state.
- `/ow sync` - queue remote config and list snapshot sync.
- `/ow config` - queue remote config sync only.
- `/ow heartbeat` - queue a heartbeat post.
- `/ow check <uuid|username>` - check a player against the local snapshot.
- `/ow check <uuid> <username>` - check a UUID and display a supplied username.
- `/ow testlog <uuid|username> <action> [username]` - send a diagnostic action log.
- `/ow exempt list` - list local exemptions.
- `/ow exempt add <uuid>` - add a local UUID exemption.
- `/ow exempt remove <uuid>` - remove a local UUID exemption.

## Safe defaults

Recommended production defaults:

- Watchlist action: `NOTIFY`
- Confirmed action: `BAN_DRY_RUN` or `TEMPBAN`
- Real permanent bans: disabled until the server owner has tested config sync, exemptions, cooldowns, appeals, and disconnect messages.

## Documentation

See the `docs/` folder for installation, configuration, commands, API, security, supported platforms, and troubleshooting notes.
