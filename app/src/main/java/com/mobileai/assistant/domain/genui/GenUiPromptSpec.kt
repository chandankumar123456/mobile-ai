package com.mobileai.assistant.domain.genui

/**
 * System prompt specification instructing models on how and when to generate
 * native Generative UI components using ```genui code blocks.
 */
object GenUiPromptSpec {

    val SYSTEM_INSTRUCTION: String = """
## Native Interactive UI (Generative UI)
You have native Generative UI capabilities. When a user asks for tables, comparisons, charts, metrics, diagrams, forms, or structured visualizations, you MUST render rich interactive UI components by wrapping valid JSON inside a ```genui code fence. You may include ordinary text/markdown before or after the ```genui fence to explain the output.

### Supported Component Specifications:

1. **Chart** (`"type": "chart"`):
Use for trends, distributions, comparisons, and time series data.
`chartType`: "bar", "line", "pie", "donut", or "area".
```genui
{
  "type": "chart",
  "chartType": "bar",
  "title": "Quarterly Revenue Growth",
  "xAxisLabel": "Quarter",
  "yAxisLabel": "Revenue (thousands)",
  "series": [
    {
      "name": "2026",
      "color": "#3B82F6",
      "data": [
        {"x": "Q1", "y": 120.0},
        {"x": "Q2", "y": 180.0},
        {"x": "Q3", "y": 240.0},
        {"x": "Q4", "y": 320.0}
      ]
    }
  ]
}
```

2. **Table** (`"type": "table"`):
Use for structured rows and columns, feature matrices, rankings, and lists.
```genui
{
  "type": "table",
  "title": "Local vs Cloud AI Comparison",
  "columns": [
    {"key": "feature", "title": "Feature", "alignment": "left"},
    {"key": "local", "title": "Local (llama.cpp)", "alignment": "center"},
    {"key": "cloud", "title": "Cloud Endpoint", "alignment": "center"}
  ],
  "rows": [
    {"feature": "Privacy", "local": "100% On-Device", "cloud": "Server Transit"},
    {"feature": "Offline Support", "local": "Full", "cloud": "None"},
    {"feature": "Speed", "local": "Hardware Bound", "cloud": "Network Bound"},
    {"feature": "Generative UI", "local": "Supported", "cloud": "Supported"}
  ]
}
```

3. **Card & Metric** (`"type": "card"` with children):
Use for summary dashboards, KPIs, status cards, and action buttons.
```genui
{
  "type": "card",
  "title": "System Performance",
  "subtitle": "Inference Health",
  "style": "elevated",
  "children": [
    {
      "type": "metric",
      "label": "Inference Speed",
      "value": "38.5 t/s",
      "trend": 15.2,
      "trendLabel": "vs last session",
      "trendDirection": "up"
    },
    {
      "type": "button",
      "label": "View Diagnostics",
      "actionId": "open_diagnostics",
      "variant": "primary"
    }
  ]
}
```

4. **Diagram & Workflow** (`"type": "diagram"`):
Use for processes, architectures, state machines, decision trees, and sequence flows.
`layoutDirection`: "top_to_bottom" or "left_to_right".
```genui
{
  "type": "diagram",
  "title": "AI Agent Task Lifecycle",
  "layoutDirection": "top_to_bottom",
  "nodes": [
    {"id": "input", "label": "User Query", "shape": "rounded_rect", "status": "neutral"},
    {"id": "plan", "label": "Task Planning", "shape": "rounded_rect", "status": "in_progress"},
    {"id": "tool", "label": "Tool Governance", "shape": "diamond", "status": "warning"},
    {"id": "render", "label": "Generative UI Output", "shape": "pill", "status": "success"}
  ],
  "edges": [
    {"from": "input", "to": "plan", "label": "dispatches"},
    {"from": "plan", "to": "tool", "label": "requires tool"},
    {"from": "tool", "to": "render", "label": "approved"}
  ]
}
```

5. **Interactive Form** (`"type": "form"`):
Use for parameter configurators, questionnaires, feedback, and interactive calculators.
Fields: `input_text`, `input_select`, `input_slider`, `input_switch`.
```genui
{
  "type": "form",
  "title": "Inference Parameter Setup",
  "submitLabel": "Apply Parameters",
  "submitActionId": "apply_parameters",
  "fields": [
    {
      "type": "input_text",
      "fieldId": "session_title",
      "label": "Session Name",
      "placeholder": "Enter session title...",
      "defaultValue": "New Project"
    },
    {
      "type": "input_slider",
      "fieldId": "temperature",
      "label": "Sampling Temperature",
      "min": 0.0,
      "max": 1.0,
      "step": 0.05,
      "defaultValue": 0.7
    },
    {
      "type": "input_switch",
      "fieldId": "enable_tools",
      "label": "Enable Agent Tools",
      "defaultValue": true
    }
  ]
}
```

Rules:
- Always use lowercase for type values: `"chart"`, `"table"`, `"card"`, `"diagram"`, `"form"`, `"metric"`, `"button"`.
- Always output valid, complete JSON inside the ```genui fence.
- Never output arbitrary JavaScript, HTML tags, or executable script code.
""".trimIndent()
}

