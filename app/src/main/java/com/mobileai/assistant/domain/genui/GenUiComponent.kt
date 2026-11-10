package com.mobileai.assistant.domain.genui

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Root polymorphic sealed interface for all Generative UI components.
 * Serialized and deserialized using the "type" property discriminator.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface GenUiComponent {
    val id: String
}

// ============================================================================
// Layout & Container Components
// ============================================================================

/**
 * Elevated, outlined, or tonal card container wrapping child components.
 */
@Serializable
@SerialName("card")
data class GenUiCard(
    override val id: String = "",
    val title: String? = null,
    val subtitle: String? = null,
    val style: CardStyle = CardStyle.ELEVATED,
    val children: List<GenUiComponent> = emptyList()
) : GenUiComponent

@Serializable
enum class CardStyle {
    @SerialName("elevated") ELEVATED,
    @SerialName("outlined") OUTLINED,
    @SerialName("tonal") TONAL
}

/**
 * Vertical linear layout container with configurable element spacing.
 */
@Serializable
@SerialName("column")
data class GenUiColumn(
    override val id: String = "",
    val spacing: Int = 8,
    val children: List<GenUiComponent> = emptyList()
) : GenUiComponent

/**
 * Horizontal linear layout container with configurable element spacing.
 */
@Serializable
@SerialName("row")
data class GenUiRow(
    override val id: String = "",
    val spacing: Int = 8,
    val children: List<GenUiComponent> = emptyList()
) : GenUiComponent

// ============================================================================
// Data Presentation Components
// ============================================================================

/**
 * Key performance indicator / metric callout card with trend indicators.
 */
@Serializable
@SerialName("metric")
data class GenUiMetric(
    override val id: String = "",
    val label: String,
    val value: String,
    val trend: Double? = null,
    val trendLabel: String? = null,
    val trendDirection: TrendDirection? = null
) : GenUiComponent

@Serializable
enum class TrendDirection {
    @SerialName("up") UP,
    @SerialName("down") DOWN,
    @SerialName("neutral") NEUTRAL
}

/**
 * Native Canvas chart supporting Line, Bar, Pie, Donut, and Area visualizations.
 */
@Serializable
@SerialName("chart")
data class GenUiChart(
    override val id: String = "",
    val chartType: ChartType = ChartType.LINE,
    val title: String? = null,
    val xAxisLabel: String? = null,
    val yAxisLabel: String? = null,
    val series: List<ChartSeries> = emptyList()
) : GenUiComponent

@Serializable
enum class ChartType {
    @SerialName("line") LINE,
    @SerialName("bar") BAR,
    @SerialName("pie") PIE,
    @SerialName("donut") DONUT,
    @SerialName("area") AREA
}

@Serializable
data class ChartSeries(
    val name: String,
    val color: String? = null,
    val data: List<ChartPoint> = emptyList()
)

@Serializable
data class ChartPoint(
    val x: String,
    val y: Double,
    val label: String? = null
)

/**
 * Tabular data grid with sortable columns, horizontal scroll, and pagination.
 */
@Serializable
@SerialName("table")
data class GenUiTable(
    override val id: String = "",
    val title: String? = null,
    val columns: List<TableColumn> = emptyList(),
    val rows: List<Map<String, String>> = emptyList(),
    val sortable: Boolean = true,
    val pageSize: Int? = null
) : GenUiComponent

@Serializable
data class TableColumn(
    val key: String,
    val title: String,
    val alignment: ColumnAlignment = ColumnAlignment.LEFT,
    val widthWeight: Float = 1.0f
)

@Serializable
enum class ColumnAlignment {
    @SerialName("left") LEFT,
    @SerialName("center") CENTER,
    @SerialName("right") RIGHT
}

// ============================================================================
// Workflow & Diagram Components
// ============================================================================

/**
 * Directed node-edge graph for workflows, flowcharts, and architecture diagrams.
 */
@Serializable
@SerialName("diagram")
data class GenUiDiagram(
    override val id: String = "",
    val title: String? = null,
    val layoutDirection: DiagramDirection = DiagramDirection.TOP_TO_BOTTOM,
    val nodes: List<DiagramNode> = emptyList(),
    val edges: List<DiagramEdge> = emptyList()
) : GenUiComponent

@Serializable
enum class DiagramDirection {
    @SerialName("top_to_bottom") TOP_TO_BOTTOM,
    @SerialName("left_to_right") LEFT_TO_RIGHT
}

@Serializable
data class DiagramNode(
    val id: String,
    val label: String,
    val shape: NodeShape = NodeShape.ROUNDED_RECT,
    val status: NodeStatus? = null,
    val description: String? = null
)

@Serializable
enum class NodeShape {
    @SerialName("rounded_rect") ROUNDED_RECT,
    @SerialName("diamond") DIAMOND,
    @SerialName("circle") CIRCLE,
    @SerialName("pill") PILL
}

@Serializable
enum class NodeStatus {
    @SerialName("success") SUCCESS,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("warning") WARNING,
    @SerialName("error") ERROR,
    @SerialName("neutral") NEUTRAL
}

@Serializable
data class DiagramEdge(
    val from: String,
    val to: String,
    val label: String? = null,
    val dashed: Boolean = false
)

// ============================================================================
// Interactive Form Components
// ============================================================================

/**
 * Interactive input form containing fields and a submit action.
 */
@Serializable
@SerialName("form")
data class GenUiForm(
    override val id: String = "",
    val title: String? = null,
    val submitLabel: String = "Submit",
    val submitActionId: String = "submit",
    val fields: List<GenUiFormField> = emptyList(),
    val isSubmitted: Boolean = false
) : GenUiComponent

/**
 * Polymorphic base sealed interface for all form input fields.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface GenUiFormField {
    val fieldId: String
    val label: String
    val required: Boolean
    val helperText: String?
}

@Serializable
@SerialName("input_text")
data class GenUiTextInput(
    override val fieldId: String = "",
    override val label: String = "",
    override val required: Boolean = false,
    override val helperText: String? = null,
    val placeholder: String? = null,
    val defaultValue: String = "",
    val inputType: TextInputType = TextInputType.TEXT
) : GenUiFormField

@Serializable
enum class TextInputType {
    @SerialName("text") TEXT,
    @SerialName("number") NUMBER,
    @SerialName("email") EMAIL,
    @SerialName("multiline") MULTILINE
}

@Serializable
@SerialName("input_select")
data class GenUiSelectInput(
    override val fieldId: String = "",
    override val label: String = "",
    override val required: Boolean = false,
    override val helperText: String? = null,
    val options: List<SelectOption> = emptyList(),
    val defaultSelectedKey: String? = null
) : GenUiFormField

@Serializable
data class SelectOption(
    val key: String,
    val label: String
)

@Serializable
@SerialName("input_slider")
data class GenUiSliderInput(
    override val fieldId: String = "",
    override val label: String = "",
    override val required: Boolean = false,
    override val helperText: String? = null,
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,
    val defaultValue: Float = min
) : GenUiFormField

@Serializable
@SerialName("input_switch")
data class GenUiSwitchInput(
    override val fieldId: String = "",
    override val label: String = "",
    override val required: Boolean = false,
    override val helperText: String? = null,
    val defaultValue: Boolean = false
) : GenUiFormField

@Serializable
@SerialName("unsupported_field")
data class GenUiUnsupportedField(
    override val fieldId: String = "",
    override val label: String = "Unsupported Field",
    override val required: Boolean = false,
    override val helperText: String? = null,
    val rawType: String? = null
) : GenUiFormField

// ============================================================================
// Action & Fallback Components
// ============================================================================

/**
 * Standalone action button for triggers and workflows.
 */
@Serializable
@SerialName("button")
data class GenUiButton(
    override val id: String = "",
    val label: String = "",
    val actionId: String = "action",
    val variant: ButtonVariant = ButtonVariant.PRIMARY,
    val payload: Map<String, String> = emptyMap()
) : GenUiComponent

@Serializable
enum class ButtonVariant {
    @SerialName("primary") PRIMARY,
    @SerialName("secondary") SECONDARY,
    @SerialName("tonal") TONAL,
    @SerialName("destructive") DESTRUCTIVE
}

/**
 * Isolated fallback component rendered when encountering unsupported or malformed child nodes.
 */
@Serializable
@SerialName("fallback")
data class GenUiFallback(
    override val id: String = "",
    val unsupportedType: String = "unknown",
    val reason: String = "",
    val rawJson: String = ""
) : GenUiComponent

/**
 * Safe degradation component rendered when encountering unsupported or malformed root nodes.
 */
@Serializable
@SerialName("unsupported")
data class GenUiUnsupported(
    override val id: String = "",
    val rawType: String? = null,
    val rawContent: String? = null,
    val reason: String? = null
) : GenUiComponent

