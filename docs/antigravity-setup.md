# Google Antigravity MCP Setup Guide

Notifyme exposes a Streamable HTTP JSON-RPC 2.0 MCP server so Google Antigravity and other MCP-compatible coding agents can report task lifecycle updates, request user decisions, and trigger instant alerts.

## 1. Register Antigravity in Notifyme
1. Open **Notifyme** and sign in with your Google account.
2. Navigate to **Agents** (or **MCP Setup**) and tap **Register**.
3. Choose **Google Antigravity** and copy the one-time bearer credential (`ntfy_...`). Notifyme stores only the SHA-256 hash of this token in Firestore.

## 2. Configure Antigravity
Add the following entry to your Antigravity MCP configuration (using the endpoint displayed on the **MCP Setup** screen — enable **Expose on Local Wi-Fi / LAN** if Antigravity runs on a workstation on the same network):

```json
{
  "mcpServers": {
    "notifyme": {
      "transport": "streamable-http",
      "url": "http://<device-ip>:8765/mcp",
      "headers": {
        "Authorization": "Bearer ${NOTIFYME_AGENT_TOKEN}"
      }
    }
  }
}
```

## 3. Agent Instruction Template
Add these instructions to your Antigravity system/workspace prompt so the agent knows when to invoke Notifyme tools:

```markdown
When you begin a task, register or identify the task in Notifyme.
When meaningful progress occurs, call update_task_progress.
When you successfully finish a task, call notify_task_completed with the task ID, project, title, and a concise summary.
If a task fails, call notify_task_failed with a useful error summary.
If you require a decision from the user, call request_user_input.
Do not report a task as completed unless the work has actually been completed and verified.
```

## 4. Troubleshooting
- **Connection Refused**: Confirm the Embedded MCP Server toggle is enabled in **MCP Setup** and LAN binding is enabled for remote hosts.
- **401 Unauthorized**: Verify `Authorization: Bearer <token>` matches an active, non-revoked agent token.
- **403 Forbidden**: The agent was revoked or `agent_id` in arguments does not match the bearer token's agent.
- **429 Too Many Requests**: Sliding-window limit is 60 requests/minute per agent.
