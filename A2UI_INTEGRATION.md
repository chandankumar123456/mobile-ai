# Google A2UI (v0.8) Protocol Integration

The platform provides a first-class implementation of the official Google A2UI (Agent-to-User Interface) protocol specification (v0.8).

---

## 1. Protocol Architecture & Lifecycle

A2UI decouples component declarations from component hierarchy through a flat component catalog and root ID resolution.

### 1.1 Protocol Messages
Supported JSONL protocol operations:
1. **`surfaceUpdate`:** Declares or updates component definitions for a surface:
   ```json
   {
     "op": "surfaceUpdate",
     "surfaceId": "summary_surface",
     "components": [
       { "id": "root", "type": "Column", "children": ["header", "metric"] },
       { "id": "header", "type": "Text", "properties": { "text": "System Health", "style": "headline" } },
       { "id": "metric", "type": "Metric", "properties": { "label": "CPU Usage", "value": "42%" } }
     ]
   }
   ```
2. **`dataModelUpdate`:** Incremental updates to dynamic data variables referenced via `{{key}}`:
   ```json
   {
     "op": "dataModelUpdate",
     "surfaceId": "summary_surface",
     "data": { "cpu_pct": "42%" }
   }
   ```
3. **`action`:** Dispatches user interactions (button clicks, form submits) back to the agent coordinator.
4. **`deleteSurface`:** Closes and frees surface state and associated Jetpack Compose tree.

---

## 2. Flat Tree Resolution & Safety

`A2UiManager` resolves the component hierarchy starting from the designated `root` component:
- **Cycle Prevention:** A visited set tracks resolved node IDs; circular component references fail gracefully with an explicit error rather than causing a stack overflow.
- **Tolerant Streaming:** Partial JSON lines during active LLM streaming are buffered until valid JSON termination is detected.
- **Data Binding:** Properties with `{{variable}}` syntax are reactively replaced with bound data model values.

---

## 3. Native Jetpack Compose Rendering

Rendered natively in `A2UiSurfaceView.kt`:
- Supported components: `Column`, `Row`, `Card`, `Text`, `Button`, `TextField`, `Metric`, `ProgressBar`, `Divider`.
- Native Material 3 theming with full dark mode and accessibility support.

