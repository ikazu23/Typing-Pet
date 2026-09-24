package com.typingpet.app

import android.content.Context

/** すべての設定をSharedPreferencesで保存・読み込みする */
object Prefs {
    private const val NAME = "typing_pet_prefs"
    private fun sp(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // サイズ: small / medium / large / xlarge
    fun getSizeKey(context: Context): String = sp(context).getString("size", "medium") ?: "medium"
    fun setSizeKey(context: Context, key: String) = sp(context).edit().putString("size", key).apply()
    fun getSizeDp(context: Context): Int = when (getSizeKey(context)) {
        "small" -> 100
        "large" -> 200
        "xlarge" -> 260
        else -> 150
    }

    // 揺れの強さ 0(なし)〜3(大きい)
    fun getShakeLevel(context: Context): Int = sp(context).getInt("shake", 3)
    fun setShakeLevel(context: Context, level: Int) = sp(context).edit().putInt("shake", level).apply()

    // 常に最前面に固定(=他アプリを開いたときも自動でペットを出す)
    fun getAlwaysOnTop(context: Context): Boolean = sp(context).getBoolean("alwaysOnTop", true)
    fun setAlwaysOnTop(context: Context, value: Boolean) = sp(context).edit().putBoolean("alwaysOnTop", value).apply()

    // 位置ロック(タップが下のアプリに素通り、ドラッグ不可)
    fun getPositionLocked(context: Context): Boolean = sp(context).getBoolean("positionLocked", false)
    fun setPositionLocked(context: Context, value: Boolean) = sp(context).edit().putBoolean("positionLocked", value).apply()

    // 表示位置
    fun getPosX(context: Context): Int = sp(context).getInt("posX", 40)
    fun getPosY(context: Context): Int = sp(context).getInt("posY", 200)
    fun setPos(context: Context, x: Int, y: Int) = sp(context).edit().putInt("posX", x).putInt("posY", y).apply()
    fun resetPos(context: Context) = setPos(context, 40, 200)

    // 内蔵プリセット(0〜3の色違い)
    fun getPreset(context: Context): Int = sp(context).getInt("preset", 0)
    fun setPreset(context: Context, preset: Int) = sp(context).edit().putInt("preset", preset).apply()

    // カスタム画像 最大4枠(空きはnull)。 "|||" 区切りの1文字列で保存し、枠の順番を保つ
    fun getCustomFrameSlots(context: Context): Array<String?> {
        val raw = sp(context).getString("customFrames", "") ?: ""
        val parts = raw.split("|||")
        val arr = arrayOfNulls<String>(4)
        for (i in 0 until 4) arr[i] = parts.getOrNull(i)?.takeIf { it.isNotBlank() }
        return arr
    }

    fun setCustomFrameSlot(context: Context, index: Int, uri: String?) {
        val current = getCustomFrameSlots(context)
        current[index] = uri
        val joined = current.joinToString("|||") { it ?: "" }
        sp(context).edit().putString("customFrames", joined).apply()
    }

    fun getCustomFrames(context: Context): List<String> =
        getCustomFrameSlots(context).filterNotNull()
}
