# Installing Obsidian Watch for NeoForge

## Requirements

- Minecraft Java Edition 26.1.2 server
- NeoForge 26.1.2.76 or newer within the 26.1.2 line
- Java 21
- Obsidian Watch website with an approved server and API key

## Install

1. Stop the server.
2. Put the jar in the server `mods/` folder.
3. Start the server once to generate `config/obsidian_watch-server.toml`.
4. Stop the server.
5. Edit the config:

```toml
enabled = true
baseUrl = "https://your-obsidian-watch-site.com"
apiKey = "your-live-server-api-key"
```

6. Start the server.
7. Run:

```mcfunction
/ow status
/ow config
/ow sync
/ow diagnostics
```

## Do not reuse dev API keys

Generate a fresh production API key from the live website before connecting a real server.
