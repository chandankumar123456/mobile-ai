# Observability & Execution Tracing

The platform incorporates an on-device tracing subsystem engineered for low overhead and deep diagnostic visibility.

---

## 1. Trace Event Architecture

Every significant runtime event generates a structured `TraceEvent`:
- **`id`:** Unique UUID.
- **`timestamp`:** Unix timestamp in milliseconds.
- **`correlationId`:** Shared ID linking user prompts, model generations, multi-step agent plans, tool calls, and UI renders into a single traceable transaction.
- **`category`:** Domain category (`INFERENCE`, `AGENT_PLANNING`, `TOOL_EXECUTION`, `MCP`, `GENUI`, `OPENUI`, `A2UI`, `STORAGE`, `VOICE`).
- **`severity`:** Log severity (`DEBUG`, `INFO`, `WARN`, `ERROR`).
- **`message`:** Human-readable diagnostic description.
- **`metadata`:** Key-value map holding latencies, token counts, error types, tool names, or schema versions.

---

## 2. Memory-Constrained Circular Ring Buffer

Mobile operating systems enforce strict heap limits. Logging millions of events into memory would cause out-of-memory (OOM) crashes.

`TraceManager` implements an in-memory circular ring buffer:
- **Fixed Capacity:** Defaults to `500` entries.
- **Thread Safety:** Synchronized access via Kotlin coroutines Mutex / atomic pointer increments.
- **Zero-Allocation Eviction:** When capacity is exceeded, oldest events are overwritten without GC pause spikes.

---

## 3. Real-Time Diagnostics UI (`TraceObservabilityScreen`)

Users and engineers can inspect live traces on device:
- **Filtering:** Filter by Category or minimum Severity level (`INFO`, `WARN`, `ERROR`).
- **Correlation Grouping:** Tap any event to filter the timeline by its `correlationId`, isolating the entire lifecycle of a single query.
- **JSON Export:** Export complete trace logs to local storage or share via Android Intent for offline post-mortem debugging.

