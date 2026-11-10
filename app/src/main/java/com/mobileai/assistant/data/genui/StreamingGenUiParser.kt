package com.mobileai.assistant.data.genui

import com.mobileai.assistant.domain.genui.GenUiComponent

sealed interface GenUiParseResult {
    data class Text(val content: String) : GenUiParseResult
    data class PartialUi(val component: GenUiComponent, val rawPayload: String) : GenUiParseResult
    data class CompleteUi(val component: GenUiComponent, val rawPayload: String) : GenUiParseResult
    data class Malformed(val rawPayload: String, val error: String) : GenUiParseResult
}

class StreamingGenUiParser(
    private val validator: GenUiValidator = GenUiValidator
) {
    private val buffer = StringBuilder()

    fun feedChunk(chunk: String): List<GenUiParseResult> {
        if (chunk.isEmpty()) return emptyList()
        val results = mutableListOf<GenUiParseResult>()
        buffer.append(chunk)

        val segments = GenUiStreamSplitter.split(buffer.toString(), isStreaming = true)
        for (segment in segments) {
            when (segment) {
                is GenUiSegment.Text -> {
                    if (segment.content.isNotEmpty()) {
                        results.add(GenUiParseResult.Text(segment.content))
                    }
                }
                is GenUiSegment.Ui -> {
                    when (val validated = validator.validate(segment.rawPayload, isStreaming = !segment.isComplete)) {
                        is GenUiValidationResult.Success -> {
                            if (segment.isComplete) {
                                results.add(GenUiParseResult.CompleteUi(validated.component, segment.rawPayload))
                            } else {
                                results.add(GenUiParseResult.PartialUi(validated.component, segment.rawPayload))
                            }
                        }
                        is GenUiValidationResult.Fallback -> {
                            results.add(GenUiParseResult.PartialUi(validated.component, segment.rawPayload))
                        }
                        is GenUiValidationResult.Malformed -> {
                            if (segment.isComplete) {
                                results.add(GenUiParseResult.Malformed(segment.rawPayload, validated.error))
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    fun finish(): List<GenUiParseResult> {
        val finalSegments = GenUiStreamSplitter.split(buffer.toString(), isStreaming = false)
        val results = mutableListOf<GenUiParseResult>()
        for (segment in finalSegments) {
            when (segment) {
                is GenUiSegment.Text -> results.add(GenUiParseResult.Text(segment.content))
                is GenUiSegment.Ui -> {
                    when (val validated = validator.validate(segment.rawPayload, isStreaming = false)) {
                        is GenUiValidationResult.Success -> results.add(GenUiParseResult.CompleteUi(validated.component, segment.rawPayload))
                        is GenUiValidationResult.Fallback -> results.add(GenUiParseResult.CompleteUi(validated.component, segment.rawPayload))
                        is GenUiValidationResult.Malformed -> results.add(GenUiParseResult.Malformed(segment.rawPayload, validated.error))
                    }
                }
            }
        }
        buffer.clear()
        return results
    }
}

