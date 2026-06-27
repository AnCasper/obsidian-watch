# Obsidian Watch Commands

Base permission level: operator permission level 2.

## Main menu

```mcfunction
/ow
/ow help
```

Shows the main command menu.

## Health and diagnostics

```mcfunction
/ow status
```

Shows connection health, server name, entry count, last heartbeat, last config sync, last snapshot sync, and current actions.

```mcfunction
/ow diagnostics
```

Shows API readiness, base URL, API key status, cache state, cooldowns, remote config state, tempban duration, real-ban safety, and last status.

## Sync

```mcfunction
/ow sync
```

Queues a remote config sync and a list snapshot sync.

```mcfunction
/ow config
```

Queues a remote config sync only.

```mcfunction
/ow heartbeat
```

Queues a heartbeat post to the website.

## Player checks

```mcfunction
/ow check <uuid|username>
/ow check <uuid> <username>
```

Checks the local synced snapshot. Username lookup only works for usernames already present in the local snapshot.

## Test action logs

```mcfunction
/ow testlog <uuid|username> <action> [username]
```

Supported actions:

- `notify`
- `kick`
- `tempban`
- `ban_dry_run`
- `ban`
- `exempted`

This only posts a diagnostic action log. It does not enforce against the player.

## Local exemptions

```mcfunction
/ow exempt list
/ow exempt add <uuid>
/ow exempt remove <uuid>
```

Local exemptions skip join actions for the listed UUID. They are stored in `localExemptUuids` in the server config.
