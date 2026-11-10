# Project: Mobile AI — Generative UI & Persistent Specialized AI Assistants (Dots)

## Architecture
The application is a native Android project with Jetpack Compose, Kotlin 2.0, Clean Architecture, and MVVM. It operates 100% local-first, supporting offline local GGUF models via `:llama-cpp-bridge` and remote OpenAI-compatible endpoints via `OpenAiCompatibleClient`.

### Subsystems:
1. **Generative UI Subsystem (`com.mobileai.assistant.domain.genui`, `com.mobileai.assistant.presentation.genui`)**:
   - Declarative, data-driven UI protocol embedded via fenced blocks (` ```genui ... ``` `) and compact DSL.
   - Streaming JSON repair parser (`StreamingJsonRepair`) for flicker-free progressive partial rendering.
   - 4-tier validation and graceful degradation engine (Syntax Repair -> Component-level Fallback -> Value Coercion -> Safe Text/Code Degradation).
   - Pure Jetpack Compose renderers: Charts (Canvas Line/Bar/Pie/Area), Tables (frozen headers, sortable, scrollable), Forms (inputs, validation, submit), Cards (stat/metric, column/row), Diagrams (Canvas node-edge graph with auto-layout), and Markdown.
   - Zero runtime code execution (no eval, no reflection, no dynamic scripts).
   - Bidirectional interaction event bus (`GenUiEvent`) routing user actions back into conversation and agent contexts.

2. **Persistent Specialized AI Assistants (Dots) Subsystem (`com.mobileai.assistant.domain.dot`, `com.mobileai.assistant.data.dot`, `com.mobileai.assistant.presentation.dot`)**:
   - Persistent `DotProfile` (name, role, instructions, avatar, theme color, model preferences, tool permissions).
   - Four built-in starter personas: General Assistant, Code Architect, Research Analyst, Technical Writer.
   - Strict thread, task, and memory isolation per Dot.
   - Persistent `DotMemory` with categories (user preferences, project facts, core instructions, session notes) dynamically injected into prompts via `<dot_memory>`.
   - Workspace / Document Artifacts system (`WorkspaceArtifact`) supporting creation, revision tracking, diff versioning, and Android SAF export.

3. **Multi-Step Task Execution & Human-in-the-Loop Engine (`com.mobileai.assistant.domain.task`, `com.mobileai.assistant.data.task`, `com.mobileai.assistant.presentation.task`)**:
   - Formal task state machine (`CREATED` -> `PLANNING` -> `QUEUED` -> `RUNNING` <-> `AWAITING_APPROVAL` -> `COMPLETED`/`PAUSED`/`FAILED`).
   - Durable step-by-step checkpointing with `TaskRecoveryCoordinator` enabling seamless resume after app restarts and process interruption.
   - 3-tier tool sensitivity classification (`SAFE`, `SENSITIVE`, `CRITICAL`).
   - Interactive Jetpack Compose `ToolApprovalCard` requiring explicit user approval before executing sensitive tool operations.
   - Immutable `tool_audit_logs` persistence for auditing.
   - Dual model execution: native JSON schema tools for OpenAI endpoints and structured `<tool_call>` tag parsing for local llama.cpp models.

4. **Persistence Layer (`com.mobileai.assistant.data.persistence`)**:
   - Relational Room / SQLite database with Write-Ahead Logging (WAL) and atomic transactions for Dots, tasks, steps, memories, artifacts, and tool logs, while maintaining zero-loss compatibility and interoperability with existing JSON files (`conversations_v2.json`, `settings_v2.json`, `remote_endpoints.json`).

5. **Presentation & Navigation Layer (`com.mobileai.assistant.presentation`)**:
   - Seamlessly integrated with `MainActivity.kt`, `AppNavGraph.kt`, `ChatScreen.kt`, `MessageBubble.kt`, and `ConversationDrawer.kt`.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F1 | GenUI Protocol & Data Models | Polymorphic `@Serializable` models for Card, Metric, Chart, Table, Form, Diagram, Column, Row | M1 | Survey E2 |
| F2 | Progressive Streaming JSON Repair | Real-time auto-closing syntax repair for partial JSON chunks during active token streaming | M1 | Survey E2 |
| F3 | GenUI Schema Validation & Graceful Fallback | 4-tier degradation ensuring invalid payloads safely fallback to text/code without crashing | M1 | Survey E2, R1 |
| F4 | Native Compose GenUI Renderers | Canvas Charts (Line, Bar, Pie, Area), Scrollable/Sortable Tables, Cards, and Diagrams | M1 | Survey E2, R1 |
| F5 | Interactive Forms & Controls | Compose Form inputs (Text, Select, Slider, Switch), validation, and submission states | M1 | Survey E2, R1 |
| F6 | GenUI Interaction Event Bus | Typed `GenUiEvent` dispatching user clicks and form data back into active conversation context | M1 | Survey E2, R1 |
| F7 | Zero-Code Execution Enforcement | Strict declarative data rendering; no eval, dynamic bytecode, or unrestricted scripts | M1 | Survey E2, R1 |
| F8 | Dot Profile Architecture | `DotProfile` data models, color/avatar presets, role instructions, model preference overrides | M2 | Survey E3, R2 |
| F9 | Built-in Starter Dot Personas | Seeded profiles for General Assistant, Code Architect, Research Analyst, Technical Writer | M2 | Survey E3, R2 |
| F10 | Dot-Scoped Conversation & Context Isolation | Independent conversation sessions, message histories, and context memory partitioned per Dot | M2 | Survey E3, R2 |
| F11 | Persistent Dot Memory Subsystem | Long-term memory store (preferences, project facts, instructions) with `<dot_memory>` prompt injection | M2 | Survey E3, R2 |
| F12 | Workspace & Document Artifacts | Creating, editing, versioning, viewing, and exporting workspace documents and code artifacts | M2 | Survey E3, R2 |
| F13 | Multi-Step Task State Machine | Task engine with states (`CREATED`, `RUNNING`, `AWAITING_APPROVAL`, `PAUSED`, `COMPLETED`, `FAILED`) | M3 | Survey E3, R2 |
| F14 | Resumable Task Checkpointing | Durable step checkpointing and recovery coordinator enabling task resume across app restarts | M3 | Survey E3, R2 |
| F15 | Tool Classification & Registry | Catalog of agent tools categorized as `SAFE`, `SENSITIVE`, or `CRITICAL` with permission scopes | M3 | Survey E3, R2 |
| F16 | Human-in-the-Loop Sensitive Tool Approval | Interactive Compose approval card requiring explicit user confirmation before executing sensitive tools | M3 | Survey E3, R2 |
| F17 | Immutable Tool Audit Logging | Persistent audit trail recording all tool invocations, inputs, approval states, and results | M3 | Survey E3, R2 |
| F18 | Dual Model Inference Tool Calling | Remote OpenAI tool schema serialization and local llama.cpp `<tool_call>` tag parser | M3 | Survey E1, E3, R3 |
| F19 | UI Navigation & Dot Management | Dot selector, profile editor, active Dot indicator, task progress sheets, drawer integration | M4 | Survey E1, E3 |
| F20 | Local-First & Zero Regression Verification | Verification that offline llama.cpp and remote OpenAI endpoints function with zero regressions | M4 | Survey E1, R3 |
| F21 | E2E Automated Verification & Hardening | 100% pass on comprehensive E2E test suite (Tiers 1-4) followed by Tier 5 adversarial hardening | M5 | R1, R2, R3 |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Generative UI Protocol, Parser & Native Compose Components | F1, F2, F3, F4, F5, F6, F7 | none | DONE |
| M2 | Persistent Dots Architecture, Memory & Workspace Artifacts | F8, F9, F10, F11, F12 | none (interfaces defined) | DONE |
| M3 | Multi-Step Task Engine, Sensitive Tool Approval & Model Calling | F13, F14, F15, F16, F17, F18 | M1, M2 | DONE |
| M4 | UI Navigation, Dot Management & Full Application Integration | F19, F20 | M1, M2, M3 | DONE |
| M5 | Final Milestone: E2E Test Suite Pass (Tiers 1-4) & Adversarial Hardening (Tier 5) | F21 | M1, M2, M3, M4, TEST_READY.md | DONE |

---

## Interface Contracts

### Generative UI ↔ Chat Presentation (`GenUiContract`)
- **Payload Extraction**:
  `StreamingGenUiParser.feedChunk(chunk: String): GenUiParseResult`
  `enum class GenUiParseResult { Text(val content: String), PartialUi(val component: GenUiComponent), CompleteUi(val component: GenUiComponent), Malformed(val rawContent: String) }`
- **Component Rendering**:
  `@Composable fun GenUiRenderer(component: GenUiComponent, onEvent: (GenUiEvent) -> Unit, isStreaming: Boolean)`
- **Interaction Events**:
  `sealed interface GenUiEvent { data class FormSubmit(val formId: String, val values: Map<String, String>) : GenUiEvent; data class ButtonClick(val actionId: String, val payload: Map<String, String>) : GenUiEvent }`
- **Feedback Loop**:
  `ChatViewModel.handleGenUiEvent(event: GenUiEvent)` generates follow-up turn or tool result.

### Dots Subsystem ↔ Chat & Persistence (`DotContract`)
- **Dot Profile Management**:
  `interface DotRepository { fun getDots(): Flow<List<DotProfile>>; suspend fun getDotById(id: String): DotProfile?; suspend fun saveDot(dot: DotProfile); suspend fun deleteDot(id: String); fun getMemories(dotId: String): Flow<List<DotMemory>>; suspend fun addMemory(memory: DotMemory) }`
- **Session Scoping**:
  `ConversationSession.dotId: String?` associates conversations with a specific Dot profile.
- **Prompt Injection**:
  `DotPromptBuilder.buildSystemPrompt(dot: DotProfile, memories: List<DotMemory>): String`

### Task Engine ↔ Sensitive Tool Approval (`TaskEngineContract`)
- **Task Management**:
  `interface TaskManager { fun getTasks(dotId: String?): Flow<List<Task>>; suspend fun createTask(title: String, goal: String, dotId: String): Task; suspend fun executeStep(taskId: String): TaskStepResult; suspend fun pauseTask(taskId: String); suspend fun resumeTask(taskId: String) }`
- **Sensitive Tool Approval**:
  `interface ToolApprovalHandler { suspend fun requestApproval(request: ToolApprovalRequest): ToolApprovalResponse }`
  `enum class ToolApprovalResponse { APPROVED, ALWAYS_ALLOWED, REJECTED }`
- **Tool Audit Logging**:
  `interface ToolAuditLogger { suspend fun log(entry: ToolAuditLogEntry) }`

---

## Code Layout
- `app/src/main/java/com/mobileai/assistant/domain/genui/` — Generative UI data models, schemas, and event definitions.
- `app/src/main/java/com/mobileai/assistant/domain/dot/` — Dot profiles, memory entities, and repository interfaces.
- `app/src/main/java/com/mobileai/assistant/domain/task/` — Task, TaskStep, tool sensitivity models, and execution contracts.
- `app/src/main/java/com/mobileai/assistant/data/genui/` — Streaming JSON repair parser, schema validator, DSL parser.
- `app/src/main/java/com/mobileai/assistant/data/dot/` — Dot persistence implementation, memory management, starter profile seeding.
- `app/src/main/java/com/mobileai/assistant/data/task/` — Multi-step task engine, recovery coordinator, tool registry, audit logger.
- `app/src/main/java/com/mobileai/assistant/presentation/genui/` — Native Compose renderers (Charts, Tables, Forms, Cards, Diagrams).
- `app/src/main/java/com/mobileai/assistant/presentation/dot/` — Dot switcher, Dot profile manager, avatar icons.
- `app/src/main/java/com/mobileai/assistant/presentation/task/` — Task progress card, step timeline, sensitive tool approval card.
- `app/src/test/java/com/mobileai/assistant/` — Unit tests for GenUI, Dots, Task Engine, Persistence, and E2E scenarios.
