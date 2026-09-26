package com.typingpet.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    // 足元の影を表示するか
    fun getShowShadow(context: Context): Boolean = sp(context).getBoolean("showShadow", true)
    fun setShowShadow(context: Context, value: Boolean) = sp(context).edit().putBoolean("showShadow", value).apply()

    // 表示位置
    fun getPosX(context: Context): Int = sp(context).getInt("posX", 40)
    fun getPosY(context: Context): Int = sp(context).getInt("posY", 200)
    fun setPos(context: Context, x: Int, y: Int) = sp(context).edit().putInt("posX", x).putInt("posY", y).apply()
    /** 全キャラの表示位置を初期位置に戻す */
    fun resetPos(context: Context) {
        setPos(context, 40, 200)
        ensureCharacters(context)
        val arr = readChars(context)
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.put("x", defaultX(i))?.put("y", defaultY(i))
        writeChars(context, arr)
    }

    // 内蔵プリセット(0〜3の色違い)
    fun getPreset(context: Context): Int = sp(context).getInt("preset", 0)
    fun setPreset(context: Context, preset: Int) = sp(context).edit().putInt("preset", preset).apply()

    // 表示言語: "system" / "ja" / "en" / "ko"。未選択の間は初回ポップアップを出す
    fun hasChosenLanguage(context: Context): Boolean = sp(context).contains("language")
    fun getLanguage(context: Context): String = sp(context).getString("language", LANG_SYSTEM) ?: LANG_SYSTEM
    fun setLanguage(context: Context, code: String) = sp(context).edit().putString("language", code).apply()

    // ---------- イラスト ----------
    // 待機: 画像リスト(ランダム表示)
    // タイピング中: 番号順のリスト。各番号に別パターンを複数登録でき、その番号ではランダム表示
    // 文字で切り替え: 反応する文字(複数)＋画像リスト(ランダム表示)
    // どれも枚数制限なし。JSONで保存する。

    const val CAT_IDLE = "idle"

    /** 文字で切り替えの1件分 */
    class Trigger(var keys: MutableList<String>, val images: MutableList<String>)

    private const val KEY_IDLE = "idleImages"
    private const val KEY_STEPS = "typingSteps"
    private const val KEY_TRIGGERS = "triggers"
    // 旧バージョンのキー(引き継ぎ用)
    private const val KEY_SETS_V2 = "typingSets"
    private const val KEY_EXCLAIM_V2 = "exclaimImages"
    private const val KEY_QUESTION_V2 = "questionImages"
    private const val KEY_MIGRATED_V2 = "imagesV2"
    private const val KEY_MIGRATED_V3 = "imagesV3"
    private const val KEY_MIGRATED_V4 = "imagesV4"

    /** 最初から用意しておく文字の反応(画像はユーザーが追加。画像が0枚の間は照合しない) */
    private fun defaultTriggers(): MutableList<Trigger> = mutableListOf(
        Trigger(mutableListOf("!", "！"), mutableListOf()),
        Trigger(mutableListOf("?", "？"), mutableListOf())
    )

    private fun parseList(arr: JSONArray?): MutableList<String> {
        val list = mutableListOf<String>()
        if (arr == null) return list
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotBlank()) list.add(s)
        }
        return list
    }

    private fun readList(context: Context, key: String): MutableList<String> = try {
        parseList(JSONArray(sp(context).getString(key, "[]") ?: "[]"))
    } catch (e: Exception) { mutableListOf() }

    private fun readNested(context: Context, key: String): MutableList<MutableList<String>> = try {
        val outer = JSONArray(sp(context).getString(key, "[]") ?: "[]")
        val result = mutableListOf<MutableList<String>>()
        for (i in 0 until outer.length()) result.add(parseList(outer.optJSONArray(i)))
        result
    } catch (e: Exception) { mutableListOf() }

    private fun nestedToJson(list: List<List<String>>): String {
        val outer = JSONArray()
        list.forEach { outer.put(JSONArray(it)) }
        return outer.toString()
    }

    private fun triggersToJson(list: List<Trigger>): String {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().put("k", JSONArray(t.keys)).put("i", JSONArray(t.images)))
        }
        return arr.toString()
    }

    /** 旧バージョンの設定を新形式に引き継ぐ */
    private fun ensureMigrated(context: Context) {
        val p = sp(context)

        // v1(最大4枠＋！？各1枚) → v2(セット＋！？リスト)
        if (!p.getBoolean(KEY_MIGRATED_V2, false)) {
            val e = p.edit()
            val oldFrames = (p.getString("customFrames", "") ?: "").split("|||").filter { it.isNotBlank() }
            e.putString(KEY_SETS_V2, nestedToJson(if (oldFrames.isEmpty()) emptyList() else listOf(oldFrames)))
            p.getString("exclaimUri", null)?.let { e.putString(KEY_EXCLAIM_V2, JSONArray(listOf(it)).toString()) }
            p.getString("questionUri", null)?.let { e.putString(KEY_QUESTION_V2, JSONArray(listOf(it)).toString()) }
            e.putBoolean(KEY_MIGRATED_V2, true).apply()
        }

        // v2 → v3(番号順＋別パターン、！？は文字で切り替えへ)
        if (!p.getBoolean(KEY_MIGRATED_V3, false)) {
            val e = p.edit()
            // セットのn枚目を「n番目」の別パターンとしてまとめる
            val steps = mutableListOf<MutableList<String>>()
            readNested(context, KEY_SETS_V2).forEach { set ->
                set.forEachIndexed { i, uri ->
                    while (steps.size <= i) steps.add(mutableListOf())
                    steps[i].add(uri)
                }
            }
            e.putString(KEY_STEPS, nestedToJson(steps))

            val triggers = mutableListOf<Trigger>()
            val ex = readList(context, KEY_EXCLAIM_V2)
            if (ex.isNotEmpty()) triggers.add(Trigger(mutableListOf("!", "！"), ex))
            val q = readList(context, KEY_QUESTION_V2)
            if (q.isNotEmpty()) triggers.add(Trigger(mutableListOf("?", "？"), q))
            e.putString(KEY_TRIGGERS, triggersToJson(triggers))

            e.putBoolean(KEY_MIGRATED_V3, true).apply()
        }

        // v3 → v4(「！」「？」を最初から用意。既にあれば何もしない)
        if (!p.getBoolean(KEY_MIGRATED_V4, false)) {
            val current = try {
                val arr = JSONArray(p.getString(KEY_TRIGGERS, "[]") ?: "[]")
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
                    Trigger(parseList(it.optJSONArray("k")), parseList(it.optJSONArray("i")))
                }.toMutableList()
            } catch (ex: Exception) { mutableListOf() }

            val existing = current.flatMap { it.keys }.toSet()
            defaultTriggers().forEach { d ->
                if (d.keys.none { it in existing }) current.add(d)
            }
            p.edit().putString(KEY_TRIGGERS, triggersToJson(current))
                .putBoolean(KEY_MIGRATED_V4, true).apply()
            matchKeysCache = null
        }
    }

    /** 待機イラスト */
    fun getImages(context: Context, category: String): MutableList<String> {
        ensureMigrated(context)
        return readList(context, KEY_IDLE)
    }

    fun setImages(context: Context, category: String, list: List<String>) {
        sp(context).edit().putString(KEY_IDLE, JSONArray(list).toString()).apply()
    }

    /** タイピング中イラスト: [1番目の別パターン一覧, 2番目の…, …] */
    fun getTypingSteps(context: Context): MutableList<MutableList<String>> {
        ensureMigrated(context)
        return readNested(context, KEY_STEPS)
    }

    fun setTypingSteps(context: Context, steps: List<List<String>>) {
        sp(context).edit().putString(KEY_STEPS, nestedToJson(steps)).apply()
    }

    /** 文字で切り替え */
    fun getTriggers(context: Context): MutableList<Trigger> {
        ensureMigrated(context)
        return try {
            val arr = JSONArray(sp(context).getString(KEY_TRIGGERS, "[]") ?: "[]")
            val list = mutableListOf<Trigger>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(Trigger(parseList(o.optJSONArray("k")), parseList(o.optJSONArray("i"))))
            }
            list
        } catch (e: Exception) { mutableListOf() }
    }

    fun setTriggers(context: Context, list: List<Trigger>) {
        sp(context).edit().putString(KEY_TRIGGERS, triggersToJson(list)).apply()
        matchKeysCache = null
    }

    @Volatile private var matchKeysCache: List<List<String>>? = null

    /**
     * 照合用の文字リスト(getTriggersと同じ並び)。画像が1枚もない項目は空にして照合しない。
     * 打鍵のたびに呼ばれるのでキャッシュする。
     */
    fun getMatchKeys(context: Context): List<List<String>> {
        matchKeysCache?.let { return it }
        val keys = getTriggers(context).map { if (it.images.isEmpty()) emptyList() else it.keys.toList() }
        matchKeysCache = keys
        return keys
    }

    // ---------- キャラ(イラスト一式＋内蔵カラーを名前付きで保存) ----------
    // 「編集中」キャラの内容は上の「待機/タイピング中/文字で切り替え/内蔵カラー」にそのまま入っていて、
    // 編集するキャラを切り替える時にだけ保存・読み込みする。
    // 画面に表示するキャラは複数選べる(表示中キャラのidリスト)。位置はキャラごとに保存する。

    private const val KEY_CHARS = "characters"
    private const val KEY_ACTIVE = "activeChar"
    private const val KEY_SHOWN = "shownChars"

    /** 画面に出すためのキャラ1体分のデータ */
    class CharData(
        val id: String,
        val name: String,
        val idle: List<String>,
        val steps: List<List<String>>,
        val triggers: List<Trigger>,
        val preset: Int,
        val x: Int,
        val y: Int
    )

    private fun readChars(context: Context): JSONArray = try {
        JSONArray(sp(context).getString(KEY_CHARS, "[]") ?: "[]")
    } catch (e: Exception) { JSONArray() }

    private fun writeChars(context: Context, arr: JSONArray) {
        sp(context).edit().putString(KEY_CHARS, arr.toString()).apply()
    }

    private fun newId(): String = java.util.UUID.randomUUID().toString()

    /** キャラごとの初期位置(少しずつずらす) */
    private fun defaultX(i: Int) = 40 + (i % 5) * 90
    private fun defaultY(i: Int) = 200 + (i % 5) * 90

    /** 編集中キャラの内容(イラスト・カラー)を、既存のキャラデータに書き込む */
    private fun fillFromCurrent(context: Context, o: JSONObject): JSONObject = o
        .put("idle", JSONArray(getImages(context, CAT_IDLE)))
        .put("steps", JSONArray(nestedToJson(getTypingSteps(context))))
        .put("tr", JSONArray(triggersToJson(getTriggers(context))))
        .put("preset", getPreset(context))

    /** キャラ1体分のデータを編集中の設定に読み込む */
    private fun applySnapshot(context: Context, o: JSONObject) {
        ensureMigrated(context)
        sp(context).edit()
            .putString(KEY_IDLE, (o.optJSONArray("idle") ?: JSONArray()).toString())
            .putString(KEY_STEPS, (o.optJSONArray("steps") ?: JSONArray()).toString())
            .putString(KEY_TRIGGERS, (o.optJSONArray("tr") ?: JSONArray()).toString())
            .putInt("preset", o.optInt("preset", 0))
            .apply()
        matchKeysCache = null
    }

    /**
     * キャラが1体もなければ今の設定を1体目として登録する。
     * あわせて、古いデータにid・位置がなければ付ける(以前の表示位置は編集中キャラに引き継ぐ)。
     */
    fun ensureCharacters(context: Context, defaultName: String = context.getString(R.string.char_default_name, 1)) {
        val arr = readChars(context)
        if (arr.length() == 0) {
            arr.put(fillFromCurrent(context, JSONObject().put("n", defaultName)))
            sp(context).edit().putInt(KEY_ACTIVE, 0).apply()
        }
        val active = getActiveCharacter(context)
        var changed = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.has("id")) { o.put("id", newId()); changed = true }
            if (!o.has("x") || !o.has("y")) {
                if (i == active) {
                    o.put("x", sp(context).getInt("posX", defaultX(i))).put("y", sp(context).getInt("posY", defaultY(i)))
                } else {
                    o.put("x", defaultX(i)).put("y", defaultY(i))
                }
                changed = true
            }
        }
        if (changed) writeChars(context, arr)
    }

    fun getCharacterNames(context: Context): List<String> {
        val arr = readChars(context)
        return (0 until arr.length()).map { arr.optJSONObject(it)?.optString("n", "") ?: "" }
    }

    fun getActiveCharacter(context: Context): Int = sp(context).getInt(KEY_ACTIVE, 0)

    /** 全キャラのデータ。編集中キャラは最新の編集内容を返す */
    fun getCharacters(context: Context): List<CharData> {
        ensureCharacters(context)
        val arr = readChars(context)
        val active = getActiveCharacter(context)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (i == active) {
                CharData(
                    o.optString("id"), o.optString("n"),
                    getImages(context, CAT_IDLE), getTypingSteps(context), getTriggers(context), getPreset(context),
                    o.optInt("x", defaultX(i)), o.optInt("y", defaultY(i))
                )
            } else {
                val steps = mutableListOf<List<String>>()
                val sa = o.optJSONArray("steps") ?: JSONArray()
                for (k in 0 until sa.length()) steps.add(parseList(sa.optJSONArray(k)))
                val trs = mutableListOf<Trigger>()
                val ta = o.optJSONArray("tr") ?: JSONArray()
                for (k in 0 until ta.length()) {
                    val t = ta.optJSONObject(k) ?: continue
                    trs.add(Trigger(parseList(t.optJSONArray("k")), parseList(t.optJSONArray("i"))))
                }
                CharData(
                    o.optString("id"), o.optString("n"),
                    parseList(o.optJSONArray("idle")), steps, trs, o.optInt("preset", 0),
                    o.optInt("x", defaultX(i)), o.optInt("y", defaultY(i))
                )
            }
        }
    }

    /** 表示中キャラのid。未設定なら編集中キャラだけを表示 */
    fun getShownIds(context: Context): Set<String> {
        ensureCharacters(context)
        val raw = sp(context).getString(KEY_SHOWN, null)
        if (raw == null) {
            val id = readChars(context).optJSONObject(getActiveCharacter(context))?.optString("id") ?: return emptySet()
            return setOf(id)
        }
        return try { parseList(JSONArray(raw)).toSet() } catch (e: Exception) { emptySet() }
    }

    fun setShown(context: Context, id: String, shown: Boolean) {
        val set = getShownIds(context).toMutableSet()
        if (shown) set.add(id) else set.remove(id)
        // キャラの並び順で保存
        val order = getCharacters(context).map { it.id }
        sp(context).edit().putString(KEY_SHOWN, JSONArray(order.filter { it in set }).toString()).apply()
    }

    /** キャラの表示位置を保存 */
    fun setCharPos(context: Context, id: String, x: Int, y: Int) {
        val arr = readChars(context)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == id) {
                o.put("x", x).put("y", y)
                writeChars(context, arr)
                return
            }
        }
    }

    /** 編集中キャラに今の設定を書き戻す(位置やidはそのまま残す) */
    private fun saveActive(context: Context) {
        val arr = readChars(context)
        val a = getActiveCharacter(context)
        val o = arr.optJSONObject(a) ?: return
        fillFromCurrent(context, o)
        writeChars(context, arr)
    }

    fun switchCharacter(context: Context, index: Int) {
        if (index == getActiveCharacter(context)) return
        ensureCharacters(context)
        saveActive(context)
        val arr = readChars(context)
        val o = arr.optJSONObject(index) ?: return
        applySnapshot(context, o)
        sp(context).edit().putInt(KEY_ACTIVE, index).apply()
    }

    /** キャラを追加して編集中にする。copyCurrent=trueなら今のキャラを複製、falseなら空のキャラ */
    fun addCharacter(context: Context, name: String, copyCurrent: Boolean) {
        ensureCharacters(context)
        saveActive(context)
        val arr = readChars(context)
        val i = arr.length()
        val base = JSONObject().put("n", name).put("id", newId()).put("x", defaultX(i)).put("y", defaultY(i))
        val o = if (copyCurrent) {
            fillFromCurrent(context, base)
        } else {
            base.put("idle", JSONArray())
                .put("steps", JSONArray())
                .put("tr", JSONArray(triggersToJson(defaultTriggers())))
                .put("preset", i % 4)
        }
        arr.put(o)
        writeChars(context, arr)
        applySnapshot(context, o)
        sp(context).edit().putInt(KEY_ACTIVE, i).apply()
    }

    fun renameCharacter(context: Context, index: Int, name: String) {
        val arr = readChars(context)
        arr.optJSONObject(index)?.put("n", name) ?: return
        writeChars(context, arr)
    }

    /** キャラを削除する。最後の1体は削除できない(falseを返す) */
    fun deleteCharacter(context: Context, index: Int): Boolean {
        ensureCharacters(context)
        val shown = getShownIds(context)
        val arr = readChars(context)
        if (arr.length() <= 1 || index !in 0 until arr.length()) return false
        val removedId = arr.optJSONObject(index)?.optString("id")
        val active = getActiveCharacter(context)
        arr.remove(index)
        writeChars(context, arr)

        val newActive = when {
            index == active -> {
                val n = minOf(index, arr.length() - 1)
                arr.optJSONObject(n)?.let { applySnapshot(context, it) }
                n
            }
            index < active -> active - 1
            else -> active
        }
        sp(context).edit()
            .putInt(KEY_ACTIVE, newActive)
            .putString(KEY_SHOWN, JSONArray(shown.filter { it != removedId }).toString())
            .apply()
        return true
    }
}
