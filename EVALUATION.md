# AI Evaluation Subsystem & Quality Verification

Evaluating on-device language models requires measuring not just generation speed, but output quality, schema compliance, tool-calling precision, and security boundaries.

---

## 1. Evaluation Dimensions & Rubrics

The `EvaluationEngine` computes multidimensional scores normalized between `0.0` and `1.0`:

### 1.1 Schema Conformance & Syntax Validity ($S_{\text{syntax}}$)
- Evaluates whether the generated output parses as valid JSON, OpenUI Lang, or Google A2UI JSONL without unrecoverable errors.
- Penalties are applied for truncated brackets, syntax errors, or invalid types.

### 1.2 Tool Selection & Parameter Accuracy ($S_{\text{tool}}$)
- Measures whether the model selected the exact required tool for a given task prompt.
- Validates parameter presence, type adherence, and hallucinated argument absence.

### 1.3 Plan Generation & Cognitive Alignment ($S_{\text{plan}}$)
- Evaluates whether multi-step agent plans follow the Observe-Understand-Plan-Act sequence.
- Checks whether dependencies between steps are logically sound (e.g. `read_artifact` occurs before `format_content`).

### 1.4 Response Relevance & Accuracy ($S_{\text{relevance}}$)
- Evaluates lexical and semantic overlap against reference criteria, keyword assertions, and factual constraints.

---

## 2. Zero-Leakage Persona Verification

A critical security requirement is total isolation between distinct "Dots" (agent personas). The evaluation framework includes automated verification tests ensuring:
1. **Memory Isolation:** Memories recorded under Dot $A$ are never retrieved or injected into the prompt of Dot $B$.
2. **Tool Permission Isolation:** Tools permitted exclusively for Dot $A$ (such as private MCP servers or file writes) are strictly excluded from Dot $B$'s runtime registry.
3. **Audit Log Segregation:** Execution audit logs accurately tag the initiating Dot identity.

