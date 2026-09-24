package com.typingpet.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class PetAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (OverlayService.instance == null && Prefs.getAlwaysOnTop(this)) {
            startService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        // 今回追加された文字だけをその場でチェックする(保存・送信はしない)。
        // 「！」「？」以外の文字が来ても中身は一切見ない。
        val addedText = extractAddedText(event)

        when {
            addedText.contains('!') || addedText.contains('！') -> OverlayService.reactSpecial('!')
            addedText.contains('?') || addedText.contains('？') -> OverlayService.reactSpecial('?')
            else -> OverlayService.reactIfRunning()
        }
    }

    private fun extractAddedText(event: AccessibilityEvent): String {
        return try {
            val full = event.text?.firstOrNull()?.toString() ?: return ""
            val from = event.fromIndex
            val count = event.addedCount
            if (from < 0 || count <= 0 || from + count > full.length) "" else full.substring(from, from + count)
        } catch (e: Exception) {
            ""
        }
    }

    override fun onInterrupt() {}
}
