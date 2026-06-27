# API Contract

The mod currently uses these endpoints:

```text
GET  /api/v1/server/config
POST /api/v1/server/heartbeat
GET  /api/v1/lists/snapshot
POST /api/v1/mod/diagnostics
POST /api/v1/mod/action-log
```

Authentication uses a server API key:

```text
Authorization: Bearer <server-api-key>
```

The website remains the source of truth for list entries, server config, action rules, message templates, and appeal URL.
