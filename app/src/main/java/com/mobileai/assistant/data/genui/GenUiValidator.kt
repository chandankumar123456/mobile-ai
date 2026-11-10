package com.mobileai.assistant.data.genui

import com.mobileai.assistant.domain.genui.*
import kotlinx.serialization.json.*
import java.util.UUID

private val JsonElement?.primitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

private val JsonElement?.stringOrNull: String?
    get() = (this as? JsonPrimitive)?.contentOrNull

private val JsonElement?.intOrNull: Int?
    get() = (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

private val JsonElement?.doubleOrNull: Double?
    get() = (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

private val JsonElement?.floatOrNull: Float?
    get() = (this as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()

private val JsonElement?.booleanOrNull: Boolean?
    get() = (this as? JsonPrimitive)?.contentOrNull?.lowercase()?.toBooleanStrictOrNull()

sealed interface GenUiValidationResult {
    data class Success(val component: GenUiComponent) : GenUiValidationResult
    data class Fallback(val component: GenUiComponent, val reason: String) : GenUiValidationResult
    data class Malformed(val rawPayload: String, val error: String) : GenUiValidationResult
}

object GenUiValidator {

    const val MAX_PAYLOAD_CHARS = 128 * 1024 // 128 KB
    const val MAX_NESTING_DEPTH = 8
    const val MAX_CHILDREN = 50
    const val MAX_TABLE_ROWS = 500
    const val MAX_TABLE_COLUMNS = 20
    const val MAX_CHART_SERIES = 8
    const val MAX_CHART_POINTS = 250
    const val MAX_DIAGRAM_NODES = 50
    const val MAX_DIAGRAM_EDGES = 100
    const val MAX_FORM_FIELDS = 30

    val ALLOWED_URL_SCHEMES = setOf("https", "http")

    private val json = GenUiSerialization.json

    /**
     * Executes the 4-Tier Validation & Coercion Pipeline.
     */
    fun validate(rawPayload: String, isStreaming: Boolean = false): GenUiValidationResult {
        // Anti-DoS Size Check
        if (rawPayload.length > MAX_PAYLOAD_CHARS) {
            return GenUiValidationResult.Malformed(
                rawPayload = rawPayload.take(1024) + "\n...[truncated]",
                error = "Payload exceeds maximum allowable size of ${MAX_PAYLOAD_CHARS / 1024} KB"
            )
        }

        // Tier 1: Syntax Repair & Structural Validation
        val repairedJson = StreamingJsonRepair.repair(rawPayload)
        val rootElement = try {
            json.parseToJsonElement(repairedJson)
        } catch (e: Exception) {
            return GenUiValidationResult.Malformed(
                rawPayload = rawPayload,
                error = "JSON Syntax Error: ${e.message}"
            )
        }

        if (rootElement !is JsonObject) {
            return GenUiValidationResult.Malformed(
                rawPayload = rawPayload,
                error = "GenUI payload must be a JSON object, found ${rootElement::class.simpleName}"
            )
        }

        // Non-primitive 'type' property check (Tier 1 structural validation)
        if (rootElement.containsKey("type") && rootElement["type"] !is JsonPrimitive) {
            return GenUiValidationResult.Malformed(
                rawPayload = rawPayload,
                error = "Property 'type' must be a primitive string, found ${rootElement["type"]?.let { it::class.simpleName } ?: "null"}"
            )
        }

        // Tier 2 & 3: Component Registry & Value Coercion
        return try {
            val sanitized = sanitizeAndCoerceObject(rootElement, depth = 0)
            val component = json.decodeFromJsonElement<GenUiComponent>(sanitized)
            GenUiValidationResult.Success(component)
        } catch (e: Exception) {
            val rawType = rootElement["type"].stringOrNull ?: "unknown"
            GenUiValidationResult.Fallback(
                component = GenUiFallback(
                    id = UUID.randomUUID().toString(),
                    unsupportedType = rawType,
                    reason = e.message ?: "Schema validation failed",
                    rawJson = repairedJson
                ),
                reason = e.message ?: "Schema validation failed"
            )
        }
    }

    /**
     * Tier 2 & 3: Recursively validates, sanitizes, bounds-checks, and coerces JsonObjects.
     */
    private fun sanitizeAndCoerceObject(obj: JsonObject, depth: Int): JsonObject {
        if (depth > MAX_NESTING_DEPTH) {
            return buildJsonObject {
                put("type", "fallback")
                put("id", UUID.randomUUID().toString())
                put("unsupportedType", "nested_container")
                put("reason", "Maximum layout nesting depth of $MAX_NESTING_DEPTH exceeded")
                put("rawJson", obj.toString())
            }
        }

        val type = resolveType(obj)
        val id = obj["id"].stringOrNull?.ifBlank { null }
            ?: UUID.randomUUID().toString()

        return buildJsonObject {
            put("type", type)
            put("id", id)

            when (type) {
                "card" -> coerceCard(obj, this, depth)
                "column" -> coerceColumn(obj, this, depth)
                "row" -> coerceRow(obj, this, depth)
                "metric" -> coerceMetric(obj, this)
                "chart" -> coerceChart(obj, this)
                "table" -> coerceTable(obj, this)
                "form" -> coerceForm(obj, this)
                "diagram" -> coerceDiagram(obj, this)
                "button" -> coerceButton(obj, this)
                "fallback" -> {
                    put("unsupportedType", obj["unsupportedType"].stringOrNull ?: "unknown")
                    put("reason", obj["reason"].stringOrNull ?: "Fallback")
                    put("rawJson", obj["rawJson"].stringOrNull ?: obj.toString())
                }
                else -> {
                    put("type", "fallback")
                    put("unsupportedType", type)
                    put("reason", "Unsupported component type: '$type'")
                    put("rawJson", obj.toString())
                }
            }
        }
    }

    private fun resolveType(obj: JsonObject): String {
        val explicit = obj["type"].stringOrNull?.lowercase()?.trim()
        if (!explicit.isNullOrBlank()) return explicit

        return when {
            obj.containsKey("chartType") || obj.containsKey("series") -> "chart"
            obj.containsKey("columns") && obj.containsKey("rows") -> "table"
            obj.containsKey("fields") -> "form"
            obj.containsKey("nodes") || obj.containsKey("edges") -> "diagram"
            obj.containsKey("label") && obj.containsKey("value") -> "metric"
            obj.containsKey("children") -> "card"
            obj.containsKey("actionId") -> "button"
            else -> "card"
        }
    }

    private fun coerceCard(src: JsonObject, dst: JsonObjectBuilder, depth: Int) {
        src["title"].stringOrNull?.let { dst.put("title", it) }
        src["subtitle"].stringOrNull?.let { dst.put("subtitle", it) }
        val style = src["style"].stringOrNull?.lowercase() ?: "elevated"
        dst.put("style", if (style in listOf("elevated", "outlined", "tonal")) style else "elevated")
        dst.put("children", coerceChildren(src["children"], depth + 1))
    }

    private fun coerceColumn(src: JsonObject, dst: JsonObjectBuilder, depth: Int) {
        val spacing = src["spacing"].intOrNull ?: 8
        dst.put("spacing", spacing.coerceIn(0, 64))
        dst.put("children", coerceChildren(src["children"], depth + 1))
    }

    private fun coerceRow(src: JsonObject, dst: JsonObjectBuilder, depth: Int) {
        val spacing = src["spacing"].intOrNull ?: 8
        dst.put("spacing", spacing.coerceIn(0, 64))
        dst.put("children", coerceChildren(src["children"], depth + 1))
    }

    private fun coerceChildren(childrenElement: JsonElement?, depth: Int): JsonArray {
        val array = childrenElement as? JsonArray ?: return JsonArray(emptyList())
        val coercedList = mutableListOf<JsonElement>()

        for (item in array.take(MAX_CHILDREN)) {
            if (item is JsonObject) {
                try {
                    coercedList.add(sanitizeAndCoerceObject(item, depth))
                } catch (_: Exception) {
                    coercedList.add(buildJsonObject {
                        put("type", "fallback")
                        put("id", UUID.randomUUID().toString())
                        put("unsupportedType", "malformed_child")
                        put("reason", "Malformed child element")
                        put("rawJson", item.toString())
                    })
                }
            }
        }
        return JsonArray(coercedList)
    }

    private fun coerceMetric(src: JsonObject, dst: JsonObjectBuilder) {
        val label = src["label"].stringOrNull ?: "Metric"
        val value = src["value"].stringOrNull ?: "-"
        dst.put("label", label)
        dst.put("value", value)

        val trendVal = src["trend"]?.doubleOrNull
            ?: src["trend"].stringOrNull?.replace("%", "")?.toDoubleOrNull()
        if (trendVal != null) {
            dst.put("trend", trendVal)
        }
        src["trendLabel"].stringOrNull?.let { dst.put("trendLabel", it) }

        val dir = src["trendDirection"].stringOrNull?.lowercase()
        if (dir in listOf("up", "down", "neutral")) {
            dst.put("trendDirection", dir!!)
        } else if (trendVal != null) {
            dst.put("trendDirection", if (trendVal > 0) "up" else if (trendVal < 0) "down" else "neutral")
        }
    }

    private fun coerceChart(src: JsonObject, dst: JsonObjectBuilder) {
        val chartType = src["chartType"].stringOrNull?.lowercase() ?: "line"
        dst.put("chartType", if (chartType in listOf("line", "bar", "pie", "area", "donut")) chartType else "line")
        src["title"].stringOrNull?.let { dst.put("title", it) }
        src["xAxisLabel"].stringOrNull?.let { dst.put("xAxisLabel", it) }
        src["yAxisLabel"].stringOrNull?.let { dst.put("yAxisLabel", it) }

        val seriesArray = (src["series"] as? JsonArray)?.take(MAX_CHART_SERIES) ?: JsonArray(emptyList())
        val coercedSeries = seriesArray.mapNotNull { s ->
            val sObj = s as? JsonObject ?: return@mapNotNull null
            buildJsonObject {
                put("name", sObj["name"].stringOrNull ?: "Series")
                sObj["color"].stringOrNull?.let { put("color", it) }
                val dataArray = (sObj["data"] as? JsonArray)?.take(MAX_CHART_POINTS) ?: JsonArray(emptyList())
                put("data", JsonArray(dataArray.mapNotNull { p ->
                    val pObj = p as? JsonObject ?: return@mapNotNull null
                    val x = pObj["x"].stringOrNull ?: ""
                    val y = pObj["y"].doubleOrNull ?: 0.0
                    buildJsonObject {
                        put("x", x)
                        put("y", y)
                        pObj["label"].stringOrNull?.let { put("label", it) }
                    }
                }))
            }
        }
        dst.put("series", JsonArray(coercedSeries))
    }

    private fun coerceTable(src: JsonObject, dst: JsonObjectBuilder) {
        src["title"].stringOrNull?.let { dst.put("title", it) }
        dst.put("sortable", src["sortable"].booleanOrNull ?: true)
        src["pageSize"].intOrNull?.let { dst.put("pageSize", it.coerceIn(1, 100)) }

        val colsArray = (src["columns"] as? JsonArray)?.take(MAX_TABLE_COLUMNS) ?: JsonArray(emptyList())
        val coercedCols = colsArray.mapNotNull { c ->
            val cObj = c as? JsonObject ?: return@mapNotNull null
            val key = cObj["key"].stringOrNull ?: return@mapNotNull null
            val title = cObj["title"].stringOrNull ?: key
            val align = cObj["alignment"].stringOrNull?.lowercase() ?: "left"
            val weight = cObj["widthWeight"].floatOrNull ?: 1.0f
            buildJsonObject {
                put("key", key)
                put("title", title)
                put("alignment", if (align in listOf("left", "center", "right")) align else "left")
                put("widthWeight", weight.coerceIn(0.2f, 5.0f))
            }
        }
        dst.put("columns", JsonArray(coercedCols))

        val rowsArray = (src["rows"] as? JsonArray)?.take(MAX_TABLE_ROWS) ?: JsonArray(emptyList())
        val coercedRows = rowsArray.mapNotNull { r ->
            val rObj = r as? JsonObject ?: return@mapNotNull null
            buildJsonObject {
                for ((k, v) in rObj) {
                    val strVal = if (v is JsonPrimitive) v.content else v.toString()
                    put(k, strVal)
                }
            }
        }
        dst.put("rows", JsonArray(coercedRows))
    }

    private fun coerceForm(src: JsonObject, dst: JsonObjectBuilder) {
        src["title"].stringOrNull?.let { dst.put("title", it) }
        dst.put("submitLabel", src["submitLabel"].stringOrNull ?: "Submit")
        dst.put("submitActionId", src["submitActionId"].stringOrNull ?: "submit")
        dst.put("isSubmitted", src["isSubmitted"].booleanOrNull ?: false)

        val fieldsArray = (src["fields"] as? JsonArray)?.take(MAX_FORM_FIELDS) ?: JsonArray(emptyList())
        val coercedFields = fieldsArray.mapNotNull { f ->
            val fObj = f as? JsonObject ?: return@mapNotNull null
            val fType = fObj["type"].stringOrNull?.lowercase() ?: "input_text"
            val fieldId = fObj["fieldId"].stringOrNull ?: UUID.randomUUID().toString()
            val label = fObj["label"].stringOrNull ?: fieldId
            val required = fObj["required"].booleanOrNull ?: false
            val helperText = fObj["helperText"].stringOrNull

            buildJsonObject {
                put("fieldId", fieldId)
                put("label", label)
                put("required", required)
                helperText?.let { put("helperText", it) }

                when (fType) {
                    "input_text" -> {
                        put("type", "input_text")
                        fObj["placeholder"].stringOrNull?.let { put("placeholder", it) }
                        put("defaultValue", fObj["defaultValue"].stringOrNull ?: "")
                        val inputType = fObj["inputType"].stringOrNull?.lowercase() ?: "text"
                        put("inputType", if (inputType in listOf("text", "number", "email", "multiline")) inputType else "text")
                    }
                    "input_select" -> {
                        put("type", "input_select")
                        fObj["defaultSelectedKey"].stringOrNull?.let { put("defaultSelectedKey", it) }
                        val opts = (fObj["options"] as? JsonArray)?.mapNotNull { opt ->
                            val oObj = opt as? JsonObject ?: return@mapNotNull null
                            val k = oObj["key"].stringOrNull ?: return@mapNotNull null
                            val l = oObj["label"].stringOrNull ?: k
                            buildJsonObject {
                                put("key", k)
                                put("label", l)
                            }
                        } ?: emptyList()
                        put("options", JsonArray(opts))
                    }
                    "input_slider" -> {
                        put("type", "input_slider")
                        val min = fObj["min"].floatOrNull ?: 0f
                        val max = fObj["max"].floatOrNull ?: 100f
                        val step = fObj["step"].floatOrNull ?: 1f
                        val defVal = fObj["defaultValue"].floatOrNull ?: min
                        put("min", min)
                        put("max", max)
                        put("step", step)
                        put("defaultValue", defVal.coerceIn(min, max))
                    }
                    "input_switch" -> {
                        put("type", "input_switch")
                        put("defaultValue", fObj["defaultValue"].booleanOrNull ?: false)
                    }
                    else -> {
                        put("type", "unsupported_field")
                        put("rawType", fType)
                    }
                }
            }
        }
        dst.put("fields", JsonArray(coercedFields))
    }

    private fun coerceDiagram(src: JsonObject, dst: JsonObjectBuilder) {
        src["title"].stringOrNull?.let { dst.put("title", it) }
        val dir = src["layoutDirection"].stringOrNull?.lowercase() ?: "top_to_bottom"
        dst.put("layoutDirection", if (dir in listOf("top_to_bottom", "left_to_right")) dir else "top_to_bottom")

        val nodesArray = (src["nodes"] as? JsonArray)?.take(MAX_DIAGRAM_NODES) ?: JsonArray(emptyList())
        val coercedNodes = nodesArray.mapNotNull { n ->
            val nObj = n as? JsonObject ?: return@mapNotNull null
            val id = nObj["id"].stringOrNull ?: return@mapNotNull null
            val label = nObj["label"].stringOrNull ?: id
            val shape = nObj["shape"].stringOrNull?.lowercase() ?: "rounded_rect"
            val status = nObj["status"].stringOrNull?.lowercase()
            val desc = nObj["description"].stringOrNull

            buildJsonObject {
                put("id", id)
                put("label", label)
                put("shape", if (shape in listOf("rounded_rect", "diamond", "circle", "pill")) shape else "rounded_rect")
                if (status in listOf("success", "in_progress", "warning", "error", "neutral")) {
                    put("status", status!!)
                }
                desc?.let { put("description", it) }
            }
        }
        dst.put("nodes", JsonArray(coercedNodes))

        val edgesArray = (src["edges"] as? JsonArray)?.take(MAX_DIAGRAM_EDGES) ?: JsonArray(emptyList())
        val coercedEdges = edgesArray.mapNotNull { e ->
            val eObj = e as? JsonObject ?: return@mapNotNull null
            val from = eObj["from"].stringOrNull ?: return@mapNotNull null
            val to = eObj["to"].stringOrNull ?: return@mapNotNull null
            val label = eObj["label"].stringOrNull
            val dashed = eObj["dashed"].booleanOrNull ?: false

            buildJsonObject {
                put("from", from)
                put("to", to)
                label?.let { put("label", it) }
                put("dashed", dashed)
            }
        }
        dst.put("edges", JsonArray(coercedEdges))
    }

    private fun coerceButton(src: JsonObject, dst: JsonObjectBuilder) {
        dst.put("label", src["label"].stringOrNull ?: "Action")
        dst.put("actionId", src["actionId"].stringOrNull ?: "action")
        val variant = src["variant"].stringOrNull?.lowercase() ?: "primary"
        dst.put("variant", if (variant in listOf("primary", "secondary", "tonal", "destructive")) variant else "primary")

        val payloadObj = src["payload"] as? JsonObject
        if (payloadObj != null) {
            val coercedPayload = buildJsonObject {
                for ((k, v) in payloadObj) {
                    val sVal = if (v is JsonPrimitive) v.content else v.toString()
                    put(k, sVal)
                }
            }
            dst.put("payload", coercedPayload)
        } else {
            dst.put("payload", JsonObject(emptyMap()))
        }
    }
}


