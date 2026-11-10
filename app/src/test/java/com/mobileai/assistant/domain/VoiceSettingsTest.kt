package com.mobileai.assistant.domain

import com.mobileai.assistant.domain.model.VoiceSettings
import com.mobileai.assistant.domain.model.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSettingsTest {

    @Test
    fun testVoiceSettingsDefaults() {
        val settings = VoiceSettings.DEFAULT
        assertFalse(settings.autoSpeakResponses)
        assertEquals(1.0f, settings.speechRate, 0.01f)
        assertEquals(1.0f, settings.speechPitch, 0.01f)
        assertTrue(settings.backgroundSessionEnabled)
        assertTrue(settings.continuousConversation)
        assertEquals(null, settings.selectedVoiceName)
    }

    @Test
    fun testVoiceStateTransitions() {
        val idle = VoiceState.Idle
        assertFalse(idle.isListening)
        assertFalse(idle.isSpeaking)
        assertFalse(idle.isBusy)

        val listening = VoiceState.Listening(rmsDb = 5.5f)
        assertTrue(listening.isListening)
        assertFalse(listening.isSpeaking)
        assertTrue(listening.isBusy)
        assertEquals(5.5f, listening.rmsDb, 0.01f)

        val speaking = VoiceState.Speaking(utteranceId = "123", messageId = "msg_1")
        assertFalse(speaking.isListening)
        assertTrue(speaking.isSpeaking)
        assertTrue(speaking.isBusy)
    }
}

