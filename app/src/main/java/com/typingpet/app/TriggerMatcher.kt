package com.typingpet.app

/** 入力直後の数文字(window)の中から、登録文字が入力されたかを照合する */
object TriggerMatcher {
    /**
     * window: 入力位置の直前数文字＋今回入力された文字
     * newFrom: window の中で今回入力された部分の開始位置
     * keys: 文字で切り替えの各項目の文字リスト(画像がない項目は空リスト)
     * 戻り値: ヒットした項目の番号。なければ -1。
     * 複数ヒットしたら、いちばん後ろ(＝最後に打たれた)もの、同じ位置なら長い方を優先。
     */
    fun find(window: String, newFrom: Int, keys: List<List<String>>): Int {
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
}
