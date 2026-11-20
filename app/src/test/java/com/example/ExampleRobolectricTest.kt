package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.overlay.FloatingAiOverlayService
import com.example.session.AiAssistantSession
import org.junit.Assert.assertEquals
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
}
