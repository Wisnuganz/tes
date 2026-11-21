package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiApiKeyStore
import com.example.overlay.FloatingAiOverlayService
import com.example.session.AiAssistantSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Cerdas AI", appName)
    }

    @Test
    fun `floating overlay service starts and expands cleanly`() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(
            FloatingAiOverlayService::class.java,
            Intent(ApplicationProvider.getApplicationContext(), FloatingAiOverlayService::class.java).apply {
                action = FloatingAiOverlayService.ACTION_START
            }
        )
        controller.create().startCommand(0, 0).get()
        assertEquals(true, AiAssistantSession.uiState.value.isOverlayServiceRunning)
        AiAssistantSession.setSystemOverlayExpanded(true)
        AiAssistantSession.setSystemOverlayExpanded(false)
        controller.destroy()
        assertEquals(false, AiAssistantSession.uiState.value.isOverlayServiceRunning)
    }

    @Test
    fun `floating overlay service stops safely without overlay permission`() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller = Robolectric.buildService(
            FloatingAiOverlayService::class.java,
            Intent(ApplicationProvider.getApplicationContext(), FloatingAiOverlayService::class.java).apply {
                action = FloatingAiOverlayService.ACTION_START
            }
        )
        controller.create().startCommand(0, 0).get()
        assertEquals(false, AiAssistantSession.uiState.value.isOverlayServiceRunning)
        controller.destroy()
    }

    @Test
    fun `gemini api key store saves masks and clears cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        GeminiApiKeyStore.clearApiKey(context)

        assertEquals("", GeminiApiKeyStore.getApiKey(context))
        assertEquals("AIzaSy••••3456", GeminiApiKeyStore.maskKey("AIzaSyTestKeyExample123456"))

        GeminiApiKeyStore.saveApiKey(context, "  AIzaSyTestKeyExample123456  ", "gemini-3.5-flash-lite")
        AiAssistantSession.init(context)

        assertEquals("AIzaSyTestKeyExample123456", GeminiApiKeyStore.getApiKey(context))
        assertEquals("gemini-3.5-flash-lite", GeminiApiKeyStore.getVerifiedModel(context))
        assertTrue(AiAssistantSession.uiState.value.hasCustomApiKey)
        assertEquals("AIzaSy••••3456", AiAssistantSession.uiState.value.maskedApiKey)

        AiAssistantSession.clearApiKey(context)
        assertFalse(AiAssistantSession.uiState.value.hasCustomApiKey)
        assertEquals("", GeminiApiKeyStore.getApiKey(context))
    }
}
