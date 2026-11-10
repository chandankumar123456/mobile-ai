package com.mobileai.assistant.domain.genui

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Dedicated, resilient Json instance configured for Generative UI parsing.
 */
object GenUiSerialization {

    val serializersModule = SerializersModule {
        polymorphic(GenUiComponent::class) {
            subclass(GenUiCard::class)
            subclass(GenUiColumn::class)
            subclass(GenUiRow::class)
            subclass(GenUiMetric::class)
            subclass(GenUiChart::class)
            subclass(GenUiTable::class)
            subclass(GenUiDiagram::class)
            subclass(GenUiForm::class)
            subclass(GenUiButton::class)
            subclass(GenUiFallback::class)
            subclass(GenUiUnsupported::class)
            defaultDeserializer { GenUiUnsupported.serializer() }
        }
        polymorphic(GenUiFormField::class) {
            subclass(GenUiTextInput::class)
            subclass(GenUiSelectInput::class)
            subclass(GenUiSliderInput::class)
            subclass(GenUiSwitchInput::class)
            subclass(GenUiUnsupportedField::class)
            defaultDeserializer { GenUiUnsupportedField.serializer() }
        }
    }

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        classDiscriminator = "type"
        this.serializersModule = GenUiSerialization.serializersModule
    }
}

