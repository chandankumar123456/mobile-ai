# Original User Request

## Initial Request — 2026-11-08T12:34:37Z

Enhance the existing Android AI chat application for production use by incorporating a structured, validated Generative UI system and persistent specialized AI assistants (Dots), drawing architectural reference from Open Intelligent UI and OpenDots while preserving native Kotlin/Compose architecture, local-first operation, and existing model infrastructure.

Working directory: E:\Projects\Mobile AI
Integrity mode: development

References:
- Open Intelligent UI: https://github.com/CopilotKit/OpenIntelligentUI
- OpenDots: https://github.com/CopilotKit/OpenDots
- OpenUI reference: https://github.com/thesysdev/open-intelligent-ui

## Requirements

### R1. Generative UI System
Implement an intelligent interface where models dynamically present structured responses rendered natively in Jetpack Compose (such as ordinary text, interactive charts, tables, forms, cards, and diagrams, with an extensible architecture to support additional component types). The Generative UI protocol must parse and validate structured UI definitions, stream components progressively as generation proceeds, support user interactions that feed context back into conversations, and never execute unrestricted model-generated code inside the application.

### R2. Persistent Specialized AI Assistants (Dots)
Introduce persistent, specialized AI assistants with configurable profiles (name, role, instructions, memory, tool permissions) and individual conversation histories. Support multi-step task execution with progress tracking, resumable tasks, workspace/document creation, and mandatory user approval before executing sensitive tool actions, backed by robust local persistence.

### R3. Architecture and Model Integration
Preserve existing Kotlin/Android architecture and current model infrastructure, supporting both local offline models (llama.cpp) and remote OpenAI-compatible endpoints. Keep local-first operation without requiring CopilotKit hosted services, utilize MCP-compatible tools where appropriate, and avoid introducing external backends, duplicate functionality, or non-functional placeholder controls.

## Verification Resources
- Existing Android project test suites and Gradle build configuration (`./gradlew testDebugUnitTest`).
- Architectural reference repositories (Open Intelligent UI, OpenDots, OpenUI reference).

## Acceptance Criteria

### Generative UI Rendering and Interaction
- [ ] Structured Generative UI messages validate against defined schemas; invalid payloads degrade gracefully to safe text rendering.
- [ ] UI components stream progressively during model generation, displaying partial state before completion.
- [ ] Interactive UI components (e.g., form inputs, buttons, selection controls) correctly send interaction event data back to the active conversation context.
- [ ] No arbitrary or unrestricted code execution is evaluated at runtime.

### AI Agents (Dots) and Execution
- [ ] Agent profiles, instructions, permissions, memory, and conversation histories persist across application restarts.
- [ ] Multi-step tasks report progress incrementally and can be resumed after interruption.
- [ ] Tool executions marked as sensitive require explicit user approval before execution.

### Architectural Compatibility and Verification
- [ ] Both local offline inference (llama.cpp) and remote OpenAI-compatible endpoints continue to function without regressions.
- [ ] Application compiles successfully with `./gradlew assembleDebug` and all automated unit/instrumentation tests pass via `./gradlew testDebugUnitTest`.

