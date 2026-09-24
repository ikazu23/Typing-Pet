package com.typingpet.app

import android.content.Context
import org.json.JSONArray

/** すべての設定をSharedPreferencesで保存・読み込みする */
object Prefs {
    private const val NAME = "typing_pet_prefs"
    private fun sp(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    const val LANG_SYSTEM = "system"

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

    // 表示言語: "system" / "ja" / "en" / "ko"。未選択の間は初回ポップアップを出す
    fun hasChosenLanguage(context: Context): Boolean = sp(context).contains("language")
    fun getLanguage(context: Context): String = sp(context).getString("language", LANG_SYSTEM) ?: LANG_SYSTEM
    fun setLanguage(context: Context, code: String) = sp(context).edit().putString("language", code).apply()

    // ---------- イラスト: 待機 / タイピング中(セット) / ！ / ？ ----------
    // 枚数制限なし。JSON配列で保存する。

    const val CAT_IDLE = "idle"
    const val CAT_EXCLAIM = "exclaim"
    const val CAT_QUESTION = "question"

    private const val KEY_IDLE = "idleImages"
    private const val KEY_SETS = "typingSets"
    private const val KEY_EXCLAIM = "exclaimImages"
    private const val KEY_QUESTION = "questionImages"
    private const val KEY_MIGRATED = "imagesV2"

    private fun keyFor(category: String) = when (category) {
        CAT_EXCLAIM -> KEY_EXCLAIM
        CAT_QUESTION -> KEY_QUESTION
        else -> KEY_IDLE
    }

    /** 旧バージョン(最大4枠＋！？各1枚)の設定を新形式に引き継ぐ */
    private fun ensureMigrated(context: Context) {
        val p = sp(context)
        if (p.getBoolean(KEY_MIGRATED, false)) return
        val e = p.edit()

        val oldFrames = (p.getString("customFrames", "") ?: "").split("|||").filter { it.isNotBlank() }
        val sets = JSONArray()
        if (oldFrames.isNotEmpty()) sets.put(JSONArray(oldFrames))
        e.putString(KEY_SETS, sets.toString())

        p.getString("exclaimUri", null)?.let { e.putString(KEY_EXCLAIM, JSONArray(listOf(it)).toString()) }
        p.getString("questionUri", null)?.let { e.putString(KEY_QUESTION, JSONArray(listOf(it)).toString()) }

        e.putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun parseList(arr: JSONArray): MutableList<String> {
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotBlank()) list.add(s)
        }
        return list
    }

    /** 待機 / ！ / ？ の画像リスト */
    fun getImages(context: Context, category: String): MutableList<String> {
        ensureMigrated(context)
        return try {
            parseList(JSONArray(sp(context).getString(keyFor(category), "[]") ?: "[]"))
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun setImages(context: Context, category: String, list: List<String>) {
        sp(context).edit().putString(keyFor(category), JSONArray(list).toString()).apply()
    }

    /** タイピング中セットのリスト(各セットは順番付きの画像リスト。空のセットも保持する) */
    fun getTypingSets(context: Context): MutableList<MutableList<String>> {
        ensureMigrated(context)
        return try {
            val outer = JSONArray(sp(context).getString(KEY_SETS, "[]") ?: "[]")
            val result = mutableListOf<MutableList<String>>()
            for (i in 0 until outer.length()) {
                val inner = outer.optJSONArray(i) ?: JSONArray()
                result.add(parseList(inner))
            }
            result
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun setTypingSets(context: Context, sets: List<List<String>>) {
        val outer = JSONArray()
        sets.forEach { outer.put(JSONArray(it)) }
        sp(context).edit().putString(KEY_SETS, outer.toString()).apply()
    }
}
