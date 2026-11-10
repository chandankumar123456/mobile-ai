# Build Plan: Portfolio-Grade On-Device Agentic AI Platform

## Transformation Strategy & Milestones

This document tracks the end-to-end execution of the transformation plan elevating the native Android on-device LLM platform into an industry-grade, portfolio-ready Agentic AI Platform.

---

### Milestone 1: Engineering Foundation & Architecture Quality
- [x] **Baseline Verification**: Verified baseline test suite (488 tests passing: 485 app, 3 bridge) and Gradle build.
- [x] **CI/CD Pipeline**: Added GitHub Actions workflow (`.github/workflows/ci.yml`) for NDK CMake build, unit tests, linting, and debug APK artifact archiving.
- [x] **Static Analysis & Tooling**: Configured ProGuard/R8 rules (`app/proguard-rules.pro`) covering JNI, Kotlinx serialization, Compose, MCP, OpenUI, and A2UI.
- [x] **Dependency Injection**: Implemented DI container in `com.mobileai.assistant.di` (`AppContainer`, `AppContainerImpl`, `EngineModule`, `RepositoryModule`, `ServiceModule`, `UseCaseModule`, `ViewModelFactory`) with `MobileAiApplication`. Refactored `MainActivity.kt` to clean container retrieval.
- [x] **Decompose ChatViewModel**: Extracted 10 specialized coordinators in `com.mobileai.assistant.presentation.chat.coordinator`:
  - `ChatSessionCoordinator` (session lifecycle, active session switching, conversation history)
  - `StreamingGenerationCoordinator` (streaming, engine invocation, cancel/stop generation)
  - `GenUiCoordinator` (GenUI parsing, state management, streaming repair events)
  - `OpenUiCoordinator` (OpenUI Lang parsing and reactive state)
  - `A2UiCoordinator` (A2UI surfaces, catalogs, and JSONL events)
  - `DotSessionCoordinator` (Dot selection, context isolation, memory injection)
  - `ToolExecutionCoordinator` (tool calling, approval gating, audit logs)
  - `AgentTaskCoordinator` (task lifecycle, planning, multi-step execution, checkpoints)
  - `VoiceCoordinator` (speech-to-text, text-to-speech)
  - `RuntimeMetricsCoordinator` (token rates, latencies, memory tracking)
- [x] **Fix Critical Dot Memory Bug**: Injected active Dot memories into `DotPromptBuilder.buildSystemPrompt` (previously passed `emptyList()`).
- [x] **Structured Error Architecture**: Created typed `AppError` hierarchy in `com.mobileai.assistant.domain.error` covering 10 domains (Inference, ModelLoad, Streaming, ToolExecution, MCP, AgentTask, GenUI, OpenUI, A2UI, Persistence).
- [x] **Persistence Reliability**: Built `ReliableStorageManager` featuring atomic temp-file writes, `.bak` file rotation, and SHA-256 integrity validation.

---

### Milestone 2: Unified Agentic AI Runtime & MCP Integration
- [x] **Complete Dot Management**:
  - Independent system instructions, isolated conversation histories, persistent memories.
  - Active memory retrieval and context injection verified by unit tests.
  - Zero cross-Dot memory or state leakage verified by tests.
  - Per-Dot tool permissions and model preferences.
- [x] **Robust Agent Runtime**:
  - Implemented `AgentPlanner` adhering to Observe -> Understand -> Plan -> Act -> Verify -> Recover workflow.
  - Multi-step planning with JSON schema validation against registered tools.
  - Execution checkpoints with process-death pause/resume/cancellation.
  - Strict tool execution: removed synthetic success echoes in `TaskManagerImpl` so missing tool handlers deterministically fail.
  - Provided genuine built-in tool handlers in `DefaultToolRegistry`.
- [x] **Model Context Protocol (MCP) Integration**:
  - Official MCP client architecture for Kotlin (`McpClient`) implementing JSON-RPC 2.0 and protocol version `2024-11-05` over Streamable HTTP.
  - Server configuration management (`McpServerManager`) with tool, resource, and prompt discovery.
  - Tool schema conversion to engine-compatible schemas.
  - Bridge into existing `ToolRegistry` with uniform approval and audit logging.
  - Per-Dot MCP server permissions and risk level categorization (`SAFE`, `SENSITIVE`, `CRITICAL`).

---

### Milestone 3: Intelligent Native UI Ecosystem (GenUI, OpenUI Lang, Google A2UI)
- [x] **Preserve Existing GenUI**:
  - Maintained backwards compatibility for existing JSON-based GenUI and streaming repair.
  - Isolated coordinator (`GenUiCoordinator`) handling JSON validation and fallback handling.
- [x] **Implement Actual OpenUI Lang**:
  - Domain AST in `com.mobileai.assistant.domain.openui.OpenUiAst`.
  - Incremental, tolerant streaming parser in `com.mobileai.assistant.data.openui.OpenUiLangParser` handling dangling tags, unclosed blocks, and `{{state.key}}` bindings.
  - Native Jetpack Compose renderer in `com.mobileai.assistant.presentation.components.openui.OpenUiRenderer`.
- [x] **Implement Official Google A2UI (v0.8)**:
  - Protocol models in `com.mobileai.assistant.domain.a2ui.A2UiProtocol` (`surfaceUpdate`, `dataModelUpdate`, `action`, `deleteSurface`).
  - Flat component catalog resolution and cycle-safe hierarchy builder in `com.mobileai.assistant.data.a2ui.A2UiManager`.
  - Native Jetpack Compose surface renderer in `com.mobileai.assistant.presentation.components.a2ui.A2UiSurfaceView`.
  - Bidirectional action handling and multi-surface lifecycle management.

---

### Milestone 4: Inference Benchmarking, Evaluation & Tracing
- [x] **Benchmark Subsystem**:
  - Standardized evaluation datasets in `app/src/main/assets/benchmark/prompts/` (short QA, medium reasoning, long generation, tool calling, GenUI, A2UI).
  - `BenchmarkRunner` measuring TTFT, prefill/decode tokens/sec, peak RAM, native memory, and context utilization.
  - Hardware & environment capture (SoC, RAM, Android version, model quant, context size).
  - Statistical calculations (Mean, Median, P50, P95, P99, StdDev).
  - CSV and JSON report export capabilities.
- [x] **Benchmark UI**: Dedicated `BenchmarkScreen` in Jetpack Compose with comparative metrics and trial runs history.
- [x] **AI Evaluation Engine**:
  - `EvaluationEngine` calculating schema conformance, tool selection precision, plan soundness, and relevance rubrics (0.0 to 1.0).
- [x] **Evaluation Dashboard**: Dedicated `EvaluationScreen` in Jetpack Compose.
- [x] **Execution Tracing & Observability**:
  - `TraceManager` thread-safe circular ring buffer (500 events) with correlation IDs and spans.
  - `TraceObservabilityScreen` with real-time timeline, category/severity filtering, and JSON export.
- [x] **Remote Model Budget Management**:
  - `TokenBudgetManager` tracking prompt/completion tokens, pricing calculation, per-model cost rates, 80% warning thresholds, and hard spending caps.

---

### Milestone 5: Professional Release Quality & Portfolio Presentation
- [x] **Release Hardening**: Verified clean debug and release compilation (`./gradlew assembleDebug` succeeded with native NDK libraries).
- [x] **Comprehensive Technical Documentation**:
  - `ARCHITECTURE.md` (System architecture diagram, clean MVVM, native boundary, cognitive loop, 3-tier GenUI).
  - `BUILD_AND_RUN.md` (Prerequisites, local build commands, GGUF deployment instructions).
  - `TEST_INFRA.md` (5-tier testing architecture, test taxonomy, 512 passing tests).
  - `BENCHMARKING.md` (Methodology, TTFT, decode throughput, statistical formulas, zero-fabrication pledge).
  - `EVALUATION.md` (Quality rubrics, tool precision scoring, zero-leakage persona verification).
  - `OBSERVABILITY.md` (Structured trace events, circular ring buffer, diagnostic UI).
  - `MCP_INTEGRATION.md` (Model Context Protocol v2024-11-05, JSON-RPC 2.0, dynamic ToolRegistry bridging).
  - `A2UI_INTEGRATION.md` (Google A2UI v0.8 specification, flat-tree resolution, JSONL streaming, Compose surfaces).
  - `OPENUI_INTEGRATION.md` (OpenUI Lang syntax, streaming parser, Jetpack Compose components).
  - `SECURITY.md` (Threat model, human-in-the-loop approvals, audit logging, zero-eval policy).
  - Architecture Decision Records (`docs/adr/ADR-001` through `ADR-005`).
- [x] **Portfolio Showcase README**: Transformed `README.md` into an industry-grade portfolio centerpiece.
- [x] **Full Test Suite & Build Verification**: 512 total tests passing at 100%.
