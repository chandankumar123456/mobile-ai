# Model Context Protocol (MCP) Integration

The platform provides a native Kotlin implementation of the official Anthropic Model Context Protocol (MCP) conforming to protocol version `2024-11-05`.

---

## 1. Protocol Client (`McpClient`)

The client communicates over Streamable HTTP (JSON-RPC 2.0):

### 1.1 Initialization Handshake
Dispatches `initialize` containing client details and supported capabilities:
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "roots": { "listChanged": true },
      "sampling": {}
    },
    "clientInfo": {
      "name": "MobileAI-Assistant",
      "version": "1.0.0"
    }
  }
}
```

### 1.2 Discovery & Execution APIs
- `tools/list`: Queries available tools and input schemas.
- `resources/list`: Queries accessible context files or URI endpoints.
- `prompts/list`: Queries server-provided prompt templates.
- `tools/call`: Dispatches tool invocation with JSON arguments and returns structured results:
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "database_query",
    "arguments": { "sql": "SELECT * FROM notes LIMIT 5" }
  }
}
```

---

## 2. Dynamic ToolRegistry Bridge (`McpServerManager`)

Discovered MCP tools are registered dynamically into the native `ToolRegistry`:
- **Namespace Isolation:** Tool names are prefixed with `server_` (e.g., `sqlite_query`) to avoid collision with internal tools.
- **Schema Mapping:** JSON schema properties, types, and required fields are translated into native `ToolParameterDefinition`.
- **Per-Dot Access Control:** Server configurations specify `allowedDotIds`. If an active Dot is not authorized, MCP tools are suppressed from its prompt and execution registry.
- **Security Tiers:** Discovered tools are classified into `SAFE`, `SENSITIVE`, and `CRITICAL`. Sensitive/Critical tools require human approval prior to dispatch.

