package com.mobileai.assistant.domain.model

object PromptBuilder {

    fun buildEffectiveSystemPrompt(
        basePrompt: String,
        language: ResponseLanguage,
        userText: String = ""
    ): String {
        val directive = languageDirective(language, userText)
        if (directive.isBlank()) return basePrompt
        if (basePrompt.isBlank()) return directive
        return basePrompt.trimEnd() + "\n\n" + directive
    }

    fun buildSystemPrompt(
        basePrompt: String,
        language: ResponseLanguage,
        capabilities: ModelCapabilities? = null,
        userText: String = ""
    ): String = buildEffectiveSystemPrompt(basePrompt, language, userText)

    fun languageDirective(language: ResponseLanguage, userText: String = ""): String =
        language.resolve(userText).directive
}
