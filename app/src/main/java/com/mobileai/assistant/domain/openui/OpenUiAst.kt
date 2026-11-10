package com.mobileai.assistant.domain.openui

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Supported attribute value types in OpenUI Lang.
 */
@Serializable
sealed interface OpenUiValue {
    data class StringValue(val value: String) : OpenUiValue
    data class NumberValue(val value: Double) : OpenUiValue
    data class BooleanValue(val value: Boolean) : OpenUiValue
    data class BindingValue(val path: String) : OpenUiValue // e.g. {{state.count}}
}

/**
 * Concrete AST node in the OpenUI Lang component tree.
 */
@Serializable
data class OpenUiNode(
    val id: String = UUID.randomUUID().toString(),
    val tag: String,
    val attributes: Map<String, OpenUiValue> = emptyMap(),
    val children: List<OpenUiNode> = emptyList(),
    val textContent: String? = null
) {
    fun getStringAttr(key: String, default: String = ""): String {
        return when (val v = attributes[key]) {
            is OpenUiValue.StringValue -> v.value
            is OpenUiValue.NumberValue -> v.value.toString()
            is OpenUiValue.BooleanValue -> v.value.toString()
            else -> default
        }
    }

    fun getNumberAttr(key: String, default: Double = 0.0): Double {
        return when (val v = attributes[key]) {
            is OpenUiValue.NumberValue -> v.value
            is OpenUiValue.StringValue -> v.value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    fun getBooleanAttr(key: String, default: Boolean = false): Boolean {
        return when (val v = attributes[key]) {
            is OpenUiValue.BooleanValue -> v.value
            is OpenUiValue.StringValue -> v.value.toBooleanStrictOrNull() ?: default
            else -> default
        }
    }
}

