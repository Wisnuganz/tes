package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.example.model.AiPanelTab
import com.example.model.StealthMode
import com.example.model.TaskAssistantUiState
import com.example.model.TaskToolType
import com.example.session.AiAssistantSession
import kotlinx.coroutines.flow.StateFlow

class TaskAssistantViewModel : ViewModel() {

    val uiState: StateFlow<TaskAssistantUiState> = AiAssistantSession.uiState

    fun saveAndTestApiKey(context: Context, rawKey: String) =
        AiAssistantSession.saveAndTestApiKey(context, rawKey)

    fun clearApiKey(context: Context) = AiAssistantSession.clearApiKey(context)

    fun setOverlayPermission(granted: Boolean) = AiAssistantSession.setOverlayPermission(granted)

    fun toggleAiPanel(open: Boolean? = null) = AiAssistantSession.toggleAiPanel(open)

    fun captureLiveScreenAndSolve(inChatTab: Boolean, fromSystemOverlay: Boolean = false) =
        AiAssistantSession.captureLiveScreenAndSolve(inChatTab, fromSystemOverlay)

    fun selectAiTab(tab: AiPanelTab) = AiAssistantSession.selectAiTab(tab)

    fun toggleQuickAnswerMode() = AiAssistantSession.toggleQuickAnswerMode()

    fun setStealthMode(mode: StealthMode) = AiAssistantSession.setStealthMode(mode)

    fun cycleStealthMode() = AiAssistantSession.cycleStealthMode()

    fun toggleAntiCapture() = AiAssistantSession.toggleAntiCapture()

    fun updateScratchpadText(text: String) = AiAssistantSession.updateScratchpadText(text)

    fun openToolInput(toolType: TaskToolType, initialText: String = "", initialBitmap: Bitmap? = null) =
        AiAssistantSession.openToolInput(toolType, initialText, initialBitmap)

    fun updatePendingToolText(text: String) = AiAssistantSession.updatePendingToolText(text)

    fun updatePendingToolBitmap(bitmap: Bitmap?) = AiAssistantSession.updatePendingToolBitmap(bitmap)

    fun closeToolInput() = AiAssistantSession.closeToolInput()

    fun clearToolError() = AiAssistantSession.clearToolError()

    fun showToast(message: String) = AiAssistantSession.showToast(message)

    fun runActiveTool() = AiAssistantSession.runActiveTool()

    fun sendChatMessage(rawPrompt: String, attachedBitmap: Bitmap? = null) =
        AiAssistantSession.sendChatMessage(rawPrompt, attachedBitmap)

    fun retryLastUserPrompt() = AiAssistantSession.retryLastUserPrompt()

    fun stopGeneration() = AiAssistantSession.stopGeneration()

    fun startNewChat() = AiAssistantSession.startNewChat()

    fun deleteSolvedTask(id: String) = AiAssistantSession.deleteSolvedTask(id)
}
