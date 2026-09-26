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

        // 表示中のキャラに文字の反応も「消したとき」もない / パスワード欄 → 中身は一切見ない
        val maxLen = OverlayService.maxKeyLength
        val watchDelete = OverlayService.watchDelete
        if (event.isPassword || (maxLen <= 0 && !watchDelete)) {
            OverlayService.reactIfRunning()
            return
        }

        val after = event.text?.firstOrNull()?.toString()
        val change = after?.let { findChange(event, it) }
        if (after == null || change == null) {
            OverlayService.reactIfRunning()
            return
        }

        // 新しい文字は何も入らず、文字が消えただけ → 「消したとき」
        if (change.end == change.start) {
            if (change.removed > 0 && watchDelete) OverlayService.reactDelete() else OverlayService.reactIfRunning()
            return
        }

        if (maxLen <= 0) {
            OverlayService.reactIfRunning()
            return
        }

        // 入力位置の直前数文字(登録文字の最大長ぶん)だけを切り出して各キャラに渡す(保存・送信はしない)
        val winStart = maxOf(0, change.start - (maxLen - 1))
        OverlayService.reactText(after.substring(winStart, change.end), change.start - winStart)
    }

    /** 今回の変更: 入力された範囲 [start, end) と、消えた文字数 */
    private class Change(val start: Int, val end: Int, val removed: Int)

    /**
     * 今回の変更を求める。
     * 変換中や予測変換でもズレにくいよう、まずは変更前後の文字列の差分から求め、
     * 取れない場合だけ fromIndex/addedCount/removedCount を使う。
     * (「ねむい」→「眠い」のような変換は、新しい文字が入るので「消した」にはならない)
     */
    private fun findChange(event: AccessibilityEvent, after: String): Change? {
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
            val removed = before.length - prefix - suffix
            if (end > prefix || removed > 0) return Change(prefix, end, removed)
        }

        val from = event.fromIndex
        val count = event.addedCount
        val removed = event.removedCount
        if (from >= 0 && count > 0 && from + count <= after.length) return Change(from, from + count, removed)
        if (from >= 0 && count == 0 && removed > 0) return Change(from, from, removed)
        return null
    }

    override fun onInterrupt() {}
}
