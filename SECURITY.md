# Security, Privacy & Safety Architecture

The Mobile AI Assistant platform is designed around strict privacy-first, on-device principles.

---

## 1. Threat Model & Mitigations

### 1.1 Untrusted Model Output & Prompt Injection
- **Threat:** Malicious prompts attempting to hijack tool execution or induce harmful file alterations.
- **Mitigation:**
  - Strict JSON schema validation on all tool arguments.
  - Three-tier tool risk classification (`SAFE`, `SENSITIVE`, `CRITICAL`).
  - Mandatory human approval dialogs for file modifications, document exports, or network transmissions.
  - Complete elimination of synthetic success echoes (unregistered tools always throw typed failures).

### 1.2 Zero-Leakage Persona Isolation
- **Threat:** Cross-contamination of personal data, memories, or API keys between different agent personas ("Dots").
- **Mitigation:**
  - Cryptographically isolated directory paths per Dot.
  - Dynamic memory injection only retrieves memories matching `activeDot.id`.
  - Tool registries dynamically filter MCP tools and built-in capabilities based on the active Dot's explicit permission whitelist.

### 1.3 Execution Sandbox & Zero-Eval Policy
- **Threat:** Dynamic UI protocols attempting code execution (e.g. JavaScript injection).
- **Mitigation:**
  - Zero-eval execution model: No dynamic code execution engine exists in the app.
  - All UI elements (GenUI, OpenUI, A2UI) map to static native Jetpack Compose composables.
  - Interaction actions only dispatch predefined application events.

---

## 2. Tool Audit Logging

Every tool execution creates a permanent entry in `ToolAuditLogger`:
- Initiating Dot ID and Dot Name.
- Tool Name and JSON Arguments.
- Execution Timestamp and Duration.
- User Approval Status (`APPROVED`, `REJECTED`, `BYPASS_SAFE`).
- Result Status (`SUCCESS`, `FAILURE`) and Error Details.

