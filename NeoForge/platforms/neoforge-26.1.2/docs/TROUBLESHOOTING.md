# Troubleshooting

## The mod says API key missing

Check `config/obsidian_watch-server.toml` and confirm `apiKey` is set.

## Config does not match the website

Run:

```mcfunction
/ow config
/ow diagnostics
```

Check the API base URL, API key, and last config sync.

## Snapshot does not include a player

Run:

```mcfunction
/ow sync
/ow check <uuid>
```

Public lookup and mod snapshot depend on active list entries from the website.

## BAN still behaves like BAN_DRY_RUN

Real permanent bans require the website setting `Allow real permanent bans` to be enabled. This is intentional.

## Players relog and do not trigger repeated messages

Check `joinActionCooldownSeconds`. The cooldown prevents relog spam.
