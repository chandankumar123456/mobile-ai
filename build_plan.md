# Build Plan: Portfolio-Grade On-Device Agentic AI Platform

## Transformation Strategy & Milestones

This document details the transformation plan for elevating the native Android on-device LLM platform into an industry-grade Agentic AI Platform.

---

### Milestone 1: Engineering Foundation & Architecture Quality
- [ ] **Baseline Verification**: Verify test suite (488 tests baseline) and Gradle build.
- [ ] **CI/CD Pipeline**: GitHub Actions workflow (.github/workflows/ci.yml) for Gradle build, native compile, unit tests, lint, and artifact packaging.
- [ ] **Static Analysis & Tooling**: Detekt / KtLint configuration, Android Lint rules, and Proguard/R8 rules.
- [ ] **Dependency Injection**: Implement clean dependency injection architecture with modular provider modules (EngineModule, RepositoryModule, UseCaseModule, ServiceModule) supporting testing substitution and native lifecycle singleton control.
- [ ] **Decompose ChatViewModel**: Extract specialized coordinators:
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
- [ ] **Structured Error Architecture**: Create unified `AppError` hierarchy across domains (Inference, ModelLoad, Streaming, ToolExecution, MCP, AgentTask, GenUI, OpenUI, A2UI, Persistence).
- [ ] **Persistence Reliability**: Versioned schemas, atomic writes (temp file + atomic replace), data corruption recovery, schema migration, backup and export.

---

### Milestone 2: Unified Agentic AI Runtime & MCP Integration
- [ ] **Complete Dot Management**:
  - Independent system instructions, isolated conversation histories, persistent memories.
  - Active memory retrieval and context injection via `DotPromptBuilder`.
  - Zero cross-Dot memory or state leakage verified by tests.
  - Per-Dot tool permissions and model preferences.
- [ ] **Robust Agent Runtime**:
  - Observe -> Understand -> Plan -> Act -> Verify -> Recover state machine.
  - Model-driven multi-step planning, deterministic tool execution.
  - Execution checkpoints with process-death pause/resume/cancellation.
  - Verification of operation results and recovery from execution failures.
- [ ] **Model Context Protocol (MCP) Integration**:
  - Official MCP client architecture for Kotlin (streamable transport, SSE/HTTP).
  - Server configuration management, tool, resource, and prompt discovery.
  - Tool schema conversion to engine-compatible schemas.
  - Bridge into existing `ToolRegistry` with uniform approval and audit logging.
  - Remote and local model compatibility.
  - Per-Dot MCP server permissions.

---

### Milestone 3: Intelligent Native UI Ecosystem (GenUI, OpenUI Lang, Google A2UI)
- [ ] **Preserve Existing GenUI**:
  - Maintain backwards compatibility for existing JSON-based GenUI and streaming repair.
  - Enhance validation and fallback handling.
- [ ] **Implement Actual OpenUI Lang**:
  - Parser for OpenUI Lang syntax (component declarations, text, inputs, buttons, metrics, cards, layouts).
  - Incremental streaming parser and progressive Compose renderer.
  - Reactive state and controlled action execution.
- [ ] **Implement Official Google A2UI**:
  - Surface lifecycle, component catalog, and flat component definition resolution.
  - JSONL streaming message parser (`surfaceUpdate`, `dataModelUpdate`, `action`, `deleteSurface`).
  - Native Jetpack Compose renderers for standard A2UI components (Card, Column, Row, Text, Button, Image, TextField, Metric, Progress).
  - Incremental data model updates and bidirectional action dispatch.

---

### Milestone 4: Inference Benchmarking, Evaluation & Tracing
- [ ] **Benchmark Subsystem**:
  - Standardized evaluation datasets (`benchmark/prompts/`: short QA, medium reasoning, long generation, tool calling, GenUI, A2UI).
  - `BenchmarkRunner` measuring TTFT, prefill/decode tokens/sec, peak RAM, native memory, context utilization.
  - Hardware & environment capture (SoC, RAM, Android version, model quant, context size).
  - Statistical calculations (Mean, Median, P50, P95, P99, StdDev).
  - JSON and CSV report export.
- [ ] **Benchmark UI**: Dedicated `BenchmarkScreen` in Compose with comparative charts and runs history.
- [ ] **AI Evaluation Engine**:
  - Deterministic evaluation (schema validation, component check, tool signature check).
  - Semantic evaluation (relevance, reference match, model-as-judge with explicit disclaimer).
  - Agent reliability scoring (tool selection, schema validity, execution success, task completion, recovery).
  - GenUI / OpenUI / A2UI conformance scoring.
- [ ] **Evaluation Dashboard**: Dedicated `EvaluationScreen` in Compose.
- [ ] **Execution Tracing & Observability**:
  - `TraceEvent` infrastructure with correlation IDs, spans, and circular memory buffer.
  - `TraceObservabilityScreen` with timeline, event filters, export.
- [ ] **Remote Model Budget Management**:
  - Token tracking, pricing calculation, per-session/per-Dot budget caps, hard limits.

---

### Milestone 5: Professional Release Quality & Portfolio Presentation
- [ ] **Release Hardening**: Proguard/R8 rules verification for reflection, JNI, and Kotlinx serialization.
- [ ] **Comprehensive Technical Documentation**:
  - `ARCHITECTURE.md`
  - `BUILD_AND_RUN.md`
  - `TEST_INFRA.md`
  - `BENCHMARKING.md`
  - `EVALUATION.md`
  - `OBSERVABILITY.md`
  - `MCP_INTEGRATION.md`
  - `A2UI_INTEGRATION.md`
  - `OPENUI_INTEGRATION.md`
  - `SECURITY.md`
  - Architecture Decision Records (ADRs)
- [ ] **Portfolio-Grade README**:
  - Positioning as On-Device Agentic AI Platform.
  - Architecture and sequence diagrams.
  - Verifiable performance and benchmark figures.
  - Technical deep dives on JNI/NDK, streaming JSON repair, A2UI, agent recovery.
