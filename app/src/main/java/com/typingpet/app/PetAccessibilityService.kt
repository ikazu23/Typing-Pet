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

        // 文字の反応が1つも登録されていない / パスワード欄 → 中身は一切見ない
        val keys = Prefs.getMatchKeys(this)
        if (event.isPassword || keys.all { it.isEmpty() }) {
            OverlayService.reactIfRunning()
            return
        }

        val index = findTrigger(event, keys)
        if (index >= 0) OverlayService.reactTrigger(index) else OverlayService.reactIfRunning()
    }

    /**
     * 今回入力された位置の直前数文字(登録文字の最大長ぶん)だけを見て、
     * 登録した文字が入力されたかをその場で照合する(保存・送信はしない)。
     * 複数ヒットしたら、いちばん後ろ(＝最後に打たれた)ものを優先。
     */
    private fun findTrigger(event: AccessibilityEvent, keys: List<List<String>>): Int {
        val after = event.text?.firstOrNull()?.toString() ?: return -1
        val range = insertedRange(event, after) ?: return -1
        val start = range.first
        val end = range.second

        val maxLen = keys.flatten().maxOfOrNull { it.length } ?: return -1
        val winStart = maxOf(0, start - (maxLen - 1))
        val window = after.substring(winStart, end)
        val newFrom = start - winStart

        var bestIndex = -1
        var bestEnd = -1
        var bestLen = 0
        keys.forEachIndexed { ti, list ->
            list.forEach { key ->
                if (key.isEmpty()) return@forEach
                var from = 0
                while (true) {
                    val pos = window.indexOf(key, from)
                    if (pos < 0) break
                    val e = pos + key.length
                    // 今回入力された部分にかかっているものだけ有効
                    if (e > newFrom && (e > bestEnd || (e == bestEnd && key.length > bestLen))) {
                        bestIndex = ti; bestEnd = e; bestLen = key.length
                    }
                    from = pos + 1
                }
            }
        }
        return bestIndex
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
