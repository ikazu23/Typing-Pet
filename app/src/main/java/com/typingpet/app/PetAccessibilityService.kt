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

        val hasExclaim = addedText.contains('!') || addedText.contains('！')
        val hasQuestion = addedText.contains('?') || addedText.contains('？')

        when {
            hasExclaim && hasQuestion -> {
                // キーボードによっては複数文字がまとめて届くことがあるため、
                // その場合は後ろにある方(＝最後に打たれた方)を優先する
                val lastExclaim = addedText.lastIndexOfAny(charArrayOf('!', '！'))
                val lastQuestion = addedText.lastIndexOfAny(charArrayOf('?', '？'))
                if (lastQuestion > lastExclaim) OverlayService.reactSpecial('?') else OverlayService.reactSpecial('!')
            }
            hasExclaim -> OverlayService.reactSpecial('!')
            hasQuestion -> OverlayService.reactSpecial('?')
            else -> OverlayService.reactIfRunning()
        }
    }

    /**
     * 追加された文字を取り出す。
     * fromIndex/addedCountだけに頼ると、日本語・韓国語の変換中や
     * キーボードの予測変換/自動補正が挟まったときにズレることがあるため、
     * まずは beforeText(変更前の文字列) と現在の文字列の差分から
     * 追加分を求め、それが取れない場合だけ fromIndex/addedCount を使う。
     */
    private fun extractAddedText(event: AccessibilityEvent): String {
        val after = event.text?.firstOrNull()?.toString() ?: return ""
        val before = event.beforeText?.toString()

        if (before != null && after.length > before.length) {
            var prefix = 0
            val maxPrefix = minOf(before.length, after.length)
            while (prefix < maxPrefix && before[prefix] == after[prefix]) prefix++

            var suffix = 0
            val maxSuffix = minOf(before.length - prefix, after.length - prefix)
            while (suffix < maxSuffix &&
                before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
            ) suffix++

            val diff = after.substring(prefix, after.length - suffix)
            if (diff.isNotEmpty()) return diff
        }

        return try {
            val from = event.fromIndex
            val count = event.addedCount
            if (from < 0 || count <= 0 || from + count > after.length) "" else after.substring(from, from + count)
        } catch (e: Exception) {
            ""
        }
    }

    override fun onInterrupt() {}
}
