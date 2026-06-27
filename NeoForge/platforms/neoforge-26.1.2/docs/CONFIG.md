# Configuration

Local config file:

```text
config/obsidian_watch-server.toml
```

Remote config source:

```text
Owner Dashboard > Servers > Config
```

Remote config overrides local action settings after sync. Local settings are used as fallback when the website is unavailable.

## API

- `enabled`: enables integration.
- `baseUrl`: website base URL with no trailing slash.
- `apiKey`: server API key from the website.
- `syncIntervalSeconds`: heartbeat/config/snapshot interval.

## Actions

Watchlist actions:

- `NONE`
- `NOTIFY`
- `KICK`

Confirmed actions:

- `NONE`
- `NOTIFY`
- `KICK`
- `TEMPBAN`
- `BAN_DRY_RUN`
- `BAN`

Permanent `BAN` is downgraded to `BAN_DRY_RUN` unless real bans are explicitly allowed on the website.

## Safety

- `joinActionCooldownSeconds`: prevents relog spam.
- `tempbanDurationSeconds`: duration for TEMPBAN.
- `exemptOps`: skip operators.
- `exemptCreative`: skip creative players.
- `localExemptUuids`: UUIDs skipped by local exemption rules.
