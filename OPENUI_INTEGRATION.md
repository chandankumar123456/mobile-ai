# OpenUI Lang Protocol Integration

The platform provides a native parser and Jetpack Compose renderer for OpenUI Lang, an open declarative markup format for generated user interfaces.

---

## 1. Syntax & Language Model

OpenUI Lang allows language models to describe dynamic UI structures using an intuitive markdown-like tag syntax:

```openui
Card {
  Column {
    Text(style="title") { "Battery Diagnostics" }
    Row {
      Badge(variant="success") { "Optimal" }
      Metric(label="Level", value="{{state.battery_pct}}")
    }
    ProgressBar(progress=0.88)
    Button(action="refresh_diagnostics") { "Run Scan" }
  }
}
```

---

## 2. Incremental Streaming Parser (`OpenUiLangParser`)

Unlike static XML/HTML parsers that fail on unclosed tags, `OpenUiLangParser` is engineered specifically for autoregressive LLM streaming:
- **Tolerant Tokenization:** Emits tokens incrementally as text deltas arrive from the inference engine.
- **Auto-Closing Unclosed Tags:** Closes dangling parent blocks gracefully so incomplete chunks render partial UI immediately without crashing.
- **Reactive State Bindings:** Extracts `{{state.field}}` references and connects them to local observable state.
- **Zero-Code Execution:** Action buttons dispatch strongly-typed `OpenUiAction` events; no untrusted script interpretation occurs on-device.

---

## 3. Jetpack Compose Component Catalog

Rendered in `OpenUiRenderer.kt`:
- **Containers:** `Card`, `Column`, `Row`.
- **Display Elements:** `Text`, `Badge`, `Metric`, `ProgressBar`, `Divider`.
- **Interactive Controls:** `Button`.

