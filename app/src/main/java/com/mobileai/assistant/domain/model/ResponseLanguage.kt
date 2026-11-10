package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ResponseLanguage(
    val displayName: String,
    val nativeName: String,
    val bcp47: String,
    val scriptName: String
) {
    AUTO("Auto", "Auto", "auto", ""),
    ENGLISH("English", "English", "en", "Latin"),
    TELUGU("Telugu", "తెలుగు", "te", "Telugu"),
    HINDI("Hindi", "हिन्दी", "hi", "Devanagari"),
    TAMIL("Tamil", "தமிழ்", "ta", "Tamil"),
    KANNADA("Kannada", "ಕನ್ನಡ", "kn", "Kannada"),
    MALAYALAM("Malayalam", "മലയാളം", "ml", "Malayalam"),
    BENGALI("Bengali", "বাংলা", "bn", "Bengali"),
    MARATHI("Marathi", "मराठी", "mr", "Devanagari"),
    URDU("Urdu", "اردو", "ur", "Arabic"),
    ARABIC("Arabic", "العربية", "ar", "Arabic"),
    SPANISH("Spanish", "Español", "es", "Latin"),
    FRENCH("French", "Français", "fr", "Latin"),
    GERMAN("German", "Deutsch", "de", "Latin"),
    CHINESE("Chinese", "中文", "zh", "Han"),
    JAPANESE("Japanese", "日本語", "ja", "Japanese"),
    KOREAN("Korean", "한국어", "ko", "Hangul");

    val directive: String
        get() = when (this) {
            AUTO -> ""
            else -> "Respond only in $displayName using the $scriptName script ($nativeName). Do not switch languages."
        }

    fun resolve(userText: String): ResponseLanguage =
        if (this == AUTO) detectFromText(userText) else this

    companion object {
        private const val THRESHOLD = 3

        private val URDU_SPECIFIC = setOf(
            'ٹ', 'ں', 'ھ', 'ے', 'ۓ', 'ڈ', 'ڑ', 'ۂ', 'ی', 'ک', 'ہ'
        )

        fun detectFromText(text: String): ResponseLanguage {
            if (text.isBlank()) return AUTO

            val telugu = countRange(text, '\u0C00', '\u0C7F')
            val devanagari = countRange(text, '\u0900', '\u097F')
            val tamil = countRange(text, '\u0B80', '\u0BFF')
            val kannada = countRange(text, '\u0C80', '\u0CFF')
            val malayalam = countRange(text, '\u0D00', '\u0D7F')
            val bengali = countRange(text, '\u0980', '\u09FF')
            val arabic = countRange(text, '\u0600', '\u06FF')
            val kana = countRange(text, '\u3040', '\u30FF')
            val hangul = countRange(text, '\u1100', '\u11FF') + countRange(text, '\uAC00', '\uD7AF')
            val han = countRange(text, '\u4E00', '\u9FFF') + countRange(text, '\u3400', '\u4DBF')

            val candidates = mutableListOf<Pair<ResponseLanguage, Int>>()
            if (telugu >= THRESHOLD) candidates += TELUGU to telugu
            if (devanagari >= THRESHOLD) candidates += HINDI to devanagari
            if (tamil >= THRESHOLD) candidates += TAMIL to tamil
            if (kannada >= THRESHOLD) candidates += KANNADA to kannada
            if (malayalam >= THRESHOLD) candidates += MALAYALAM to malayalam
            if (bengali >= THRESHOLD) candidates += BENGALI to bengali
            if (arabic >= THRESHOLD) {
                val language = if (text.any { it in URDU_SPECIFIC }) URDU else ARABIC
                candidates += language to arabic
            }

            val nonCjk = candidates.maxByOrNull { it.second }
            val cjkCount = maxOf(kana, hangul, han)
            if (nonCjk != null && nonCjk.second >= cjkCount) return nonCjk.first

            return when {
                kana >= THRESHOLD -> JAPANESE
                hangul >= THRESHOLD -> KOREAN
                han >= THRESHOLD -> CHINESE
                else -> nonCjk?.first ?: AUTO
            }
        }

        private fun countRange(text: String, start: Char, end: Char): Int =
            text.count { it in start..end }
    }
}
