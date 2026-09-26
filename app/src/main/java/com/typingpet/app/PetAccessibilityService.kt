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
        if (OverlayService.instance == null) return

        // 表示中のキャラに文字の反応が1つもない / パスワード欄 → 中身は一切見ない
        val maxLen = OverlayService.maxKeyLength
        if (event.isPassword || maxLen <= 0) {
            OverlayService.reactIfRunning()
            return
        }

        // 入力位置の直前数文字(登録文字の最大長ぶん)だけを切り出して各キャラに渡す(保存・送信はしない)
        val after = event.text?.firstOrNull()?.toString()
        val range = after?.let { insertedRange(event, it) }
        if (after == null || range == null) {
            OverlayService.reactIfRunning()
            return
        }
        val winStart = maxOf(0, range.first - (maxLen - 1))
        OverlayService.reactText(after.substring(winStart, range.second), range.first - winStart)
    }

    /**
     * 今回入力された範囲 [start, end) を求める。
     * 変換中や予測変換でもズレにくいよう、まずは変更前後の文字列の差分から求め、
     * 取れない場合だけ fromIndex/addedCount を使う。
     */
    private fun insertedRange(event: AccessibilityEvent, after: String): Pair<Int, Int>? {
        val before = event.beforeText?.toString()
        if (before != null) {
            var prefix = 0
            val maxPrefix = minOf(before.length, after.length)
            while (prefix < maxPrefix && before[prefix] == after[prefix]) prefix++

            var suffix = 0
            val maxSuffix = minOf(before.length - prefix, after.length - prefix)
            while (suffix < maxSuffix &&
                before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
            ) suffix++

            val end = after.length - suffix
            if (end > prefix) return Pair(prefix, end)
        }

        val from = event.fromIndex
        val count = event.addedCount
        if (from >= 0 && count > 0 && from + count <= after.length) return Pair(from, from + count)
        return null
    }

    override fun onInterrupt() {}
}
