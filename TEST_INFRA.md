# E2E Test Infra: Mobile AI (Generative UI & Persistent Dots)

## Test Philosophy
- Opaque-box, requirement-driven. Derived from ORIGINAL_REQUEST.md and user-facing specifications without coupling to internal private methods.
- Methodology: Category-Partition + Boundary Value Analysis (BVA) + Pairwise Combinatorial Testing + Real-World Workload Testing.

## Feature Inventory
| # | Feature | Source (Requirement) | Tier 1 (Min 5) | Tier 2 (Min 5) | Tier 3 (Pairwise) |
|---|---------|----------------------|:--------------:|:--------------:|:-----------------:|
| F1 | GenUI Protocol & Data Models | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F2 | Progressive Streaming JSON Repair | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F3 | GenUI Schema Validation & Fallback | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F4 | Native Compose GenUI Renderers | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F5 | Interactive Forms & Controls | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F6 | GenUI Interaction Event Bus | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F7 | Zero-Code Execution Enforcement | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F8 | Dot Profile Architecture | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F9 | Built-in Starter Dot Personas | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F10 | Dot-Scoped Conversation Isolation | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F11 | Persistent Dot Memory Subsystem | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F12 | Workspace & Document Artifacts | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F13 | Multi-Step Task State Machine | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F14 | Resumable Task Checkpointing | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F15 | Tool Classification & Registry | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F16 | Human-in-the-Loop Sensitive Tool Approval | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F17 | Immutable Tool Audit Logging | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F18 | Dual Model Inference Tool Calling | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| F19 | UI Navigation & Dot Management | ORIGINAL_REQUEST §R2, R3 | 5 | 5 | ✓ |
| F20 | Local-First & Zero Regression | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| F21 | End-to-End Task & GenUI Workflows | ORIGINAL_REQUEST Acceptance Criteria | 5 | 5 | ✓ |

## Test Architecture
- Test Runner: Gradle command `.\gradlew.bat testDebugUnitTest`
- Location: `app/src/test/java/com/mobileai/assistant/e2e/` and unit test suites under `app/src/test/java/com/mobileai/assistant/`
- Test Case Tiers:
  - **Tier 1 (Feature Coverage)**: Happy path tests for every feature in isolation.
  - **Tier 2 (Boundary & Corner Cases)**: Malformed JSON, network breaks, truncated SSE streams, extreme values, empty inputs, unclosed fences, rapid interruptions.
  - **Tier 3 (Cross-Feature Combinations)**: Generative UI + Dot context; Multi-step task + Sensitive Tool approval; Dot memory + Streaming GenUI response.
  - **Tier 4 (Real-World Application Scenarios)**: Complex end-to-end user workflows (e.g., Code Architect creates a structured architecture document artifact, submits approval to save, generates GenUI diagram and charts, and resumes after app restart).

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Interactive Financial Report | F1, F3, F4, F6 (Card, Metric, Bar Chart, Table with sorting) | Medium |
| 2 | Diagnostic Feedback Survey Form | F1, F5, F6 (TextInput, Select, Slider, Switch, validation, submission feedback) | High |
| 3 | Code Architect Multi-Step Project Scaffolding | F8, F9, F12, F13, F14, F16, F17 (Dot persona, task steps, code artifact, sensitive tool approval, audit log) | High |
| 4 | Offline Task Interruption and Resumption | F10, F13, F14, F18, F20 (Llama.cpp local offline inference, process termination mid-task, state recovery, resume) | High |
| 5 | Streaming Malformed Payload Degradation | F2, F3, F7 (Streaming JSON truncation, corrupted JSON syntax, graceful fallback to safe text/code, zero eval) | High |
| 6 | Research Analyst Fact Synthesis & Memory | F9, F11, F12, F18 (OpenAI endpoint, memory accumulation, prompt injection, document artifact creation) | Medium |

## Coverage Thresholds
- Tier 1: >= 5 per feature (>= 105 tests across 21 features)
- Tier 2: >= 5 per feature (>= 105 tests across 21 features)
- Tier 3: >= 21 pairwise integration tests
- Tier 4: >= 6 realistic application scenarios

