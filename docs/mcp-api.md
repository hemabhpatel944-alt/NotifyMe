# Notifyme MCP API Reference

Endpoint: `POST /mcp` (JSON-RPC 2.0 over Streamable HTTP)  
Health Check: `GET /health`  
Authentication: `Authorization: Bearer <agent_token>` (verified via SHA-256 constant-time hash comparison)  
Payload Limit: 64 KB max body • Rate Limit: 60 req/min per agent

## Supported MCP Methods
- `initialize`
- `notifications/initialized`
- `ping`
- `tools/list`
- `tools/call`

## Exposed Tools
1. `notify_task_completed` — Marks a task `completed` (100%), logs an immutable event, and dispatches notification if enabled.
2. `notify_task_failed` — Marks a task `failed`, stores error summary, and dispatches failure alert.
3. `update_task_progress` — Updates task progress (`0..100`) and status `running`.
4. `request_user_input` — Transitions task to `waiting_for_input` with `question`, `context`, and up to 10 `options`.
5. `get_task_status` — Returns latest task status, progress, summary, and any `user_response` submitted from the Notifyme UI.
6. `register_agent` — Registers an additional agent under the authenticated user (never returns raw secret tokens).
7. `notify_agent_connected` — Marks agent `connected` and logs session start.
8. `notify_agent_disconnected` — Marks agent `disconnected` with optional reason.

## Idempotency
Pass `idempotency_key` (or `event_id`) in `arguments`. Duplicate retries return `"deduplicated": true` without generating duplicate notifications.
