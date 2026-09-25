package com.typingpet.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

class MainActivity : Activity() {

    private var currentTab = 0 // 0:基本 1:プリセット 2:イラスト

    private lateinit var tabBar: LinearLayout
    private lateinit var basicBox: LinearLayout
    private lateinit var presetBox: LinearLayout
    private lateinit var imageBox: LinearLayout

    private val ink = Color.parseColor("#3A2E2C")
    private val sub = Color.parseColor("#8A7A72")
    private val bg = Color.parseColor("#FDF6EC")
    private val accent = Color.parseColor("#FF9D6C")
    private val cardBg = Color.WHITE

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初回起動時は言語を選ぶまで設定画面を組み立てない(選択後にrecreateされる)
        if (!Prefs.hasChosenLanguage(this)) {
            showLanguageDialog(cancelable = false)
            return
        }

        buildUi()
    }

    private fun showLanguageDialog(cancelable: Boolean) {
        val labels = arrayOf("日本語", "English", "한국어")
        val codes = arrayOf("ja", "en", "ko")
        AlertDialog.Builder(this)
            .setTitle("言語 / Language / 언어")
            .setItems(labels) { _, which ->
                Prefs.setLanguage(this, codes[which])
                recreate()
            }
            .setCancelable(cancelable)
            .show()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(bg)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.title_settings)
            textSize = 20f
            setTextColor(ink)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(sectionCard {
            addView(smallLabel(getString(R.string.section_permission)))
            addView(spacer(8))
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_overlay)
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            })
            addView(spacer(8))
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_accessibility)
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            })
            addView(spacer(8))
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_show_pet)
                setOnClickListener {
                    if (Settings.canDrawOverlays(this@MainActivity)) {
                        startService(Intent(this@MainActivity, OverlayService::class.java))
                    }
                }
            })
            addView(spacer(8))
            addView(Button(this@MainActivity).apply {
                text = "言語 / Language / 언어"
                setOnClickListener { showLanguageDialog(cancelable = true) }
            })
        })

        root.addView(spacer(16))

        tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabBar)
        root.addView(spacer(12))

        basicBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        presetBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        imageBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(basicBox)
        root.addView(presetBox)
        root.addView(imageBox)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        renderAll()
    }

    // ---------- 全体描画 ----------

    private fun renderAll() {
        renderTabs()
        renderBasic()
        renderPreset()
        renderImages()
        basicBox.visibility = if (currentTab == 0) View.VISIBLE else View.GONE
        presetBox.visibility = if (currentTab == 1) View.VISIBLE else View.GONE
        imageBox.visibility = if (currentTab == 2) View.VISIBLE else View.GONE
    }

    private fun renderTabs() {
        tabBar.removeAllViews()
        listOf(
            getString(R.string.tab_basic),
            getString(R.string.tab_preset),
            getString(R.string.tab_image)
        ).forEachIndexed { i, label ->
            tabBar.addView(Button(this).apply {
                text = label
                val selected = currentTab == i
                setBackgroundColor(if (selected) ink else cardBg)
                setTextColor(if (selected) Color.WHITE else ink)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener { currentTab = i; renderAll() }
            })
        }
    }

    // ---------- 基本設定タブ ----------

    private fun renderBasic() {
        basicBox.removeAllViews()

        basicBox.addView(sectionCard {
            addView(smallLabel(getString(R.string.label_size)))
            addView(spacer(8))
            addView(segRow(
                listOf(
                    getString(R.string.size_small) to "small",
                    getString(R.string.size_medium) to "medium",
                    getString(R.string.size_large) to "large",
                    getString(R.string.size_xlarge) to "xlarge"
                ),
                Prefs.getSizeKey(this@MainActivity)
            ) { key ->
                Prefs.setSizeKey(this@MainActivity, key)
                OverlayService.refreshIfRunning()
                renderBasic()
            })
        })

        basicBox.addView(spacer(16))

        basicBox.addView(sectionCard {
            addView(smallLabel(getString(R.string.label_shake)))
            addView(descLabel(getString(R.string.desc_shake)))
            addView(spacer(8))
            addView(segRow(
                listOf(
                    getString(R.string.shake_none) to "0",
                    getString(R.string.shake_1) to "1",
                    getString(R.string.shake_2) to "2",
                    getString(R.string.shake_3) to "3"
                ),
                Prefs.getShakeLevel(this@MainActivity).toString()
            ) { key ->
                Prefs.setShakeLevel(this@MainActivity, key.toInt())
                OverlayService.refreshIfRunning()
                renderBasic()
            })
        })

        basicBox.addView(spacer(16))

        basicBox.addView(sectionCard {
            addView(smallLabel(getString(R.string.label_display)))
            addView(spacer(4))

            addView(switchRow(
                getString(R.string.switch_always_on_top_title),
                getString(R.string.switch_always_on_top_desc),
                Prefs.getAlwaysOnTop(this@MainActivity)
            ) { checked ->
                Prefs.setAlwaysOnTop(this@MainActivity, checked)
            })

            addView(spacer(12))

            addView(switchRow(
                getString(R.string.switch_lock_title),
                getString(R.string.switch_lock_desc),
                Prefs.getPositionLocked(this@MainActivity)
            ) { checked ->
                Prefs.setPositionLocked(this@MainActivity, checked)
                OverlayService.refreshIfRunning()
            })

            addView(spacer(12))

            addView(switchRow(
                getString(R.string.switch_shadow_title),
                getString(R.string.switch_shadow_desc),
                Prefs.getShowShadow(this@MainActivity)
            ) { checked ->
                Prefs.setShowShadow(this@MainActivity, checked)
                OverlayService.refreshIfRunning()
            })

            addView(spacer(12))

            addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_reset_pos)
                setOnClickListener {
                    Prefs.resetPos(this@MainActivity)
                    OverlayService.refreshIfRunning()
                }
            })
        })

        basicBox.addView(spacer(16))

        basicBox.addView(sectionCard {
            addView(smallLabel(getString(R.string.label_icon_section)))
            addView(descLabel(getString(R.string.desc_icon_section)))
            addView(spacer(12))
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_add_icon_shortcut)
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    startActivityForResult(intent, 320)
                }
            })
        })
    }

    // ---------- プリセットタブ ----------

    private fun renderPreset() {
        presetBox.removeAllViews()

        presetBox.addView(charactersCard())
        presetBox.addView(spacer(16))

        presetBox.addView(sectionCard {
            addView(smallLabel(getString(R.string.label_builtin_color)))
            addView(descLabel(getString(R.string.desc_builtin_color)))
            addView(spacer(12))

            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            val current = Prefs.getPreset(this@MainActivity)

            for (i in 0 until 4) {
                val cell = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val preview = PetView(this@MainActivity).apply {
                    preset = i
                    layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
                    setOnClickListener {
                        Prefs.setPreset(this@MainActivity, i)
                        OverlayService.refreshIfRunning()
                        renderPreset()
                    }
                }
                cell.addView(preview)
                cell.addView(TextView(this@MainActivity).apply {
                    text = if (i == current) getString(R.string.label_selected) else getString(R.string.label_select)
                    textSize = 12f
                    setTextColor(if (i == current) accent else sub)
                    gravity = Gravity.CENTER
                })
                row.addView(cell)
            }
            addView(row)
        })
    }

    // ---------- キャラ(イラスト一式を保存して切り替え) ----------

    /** キャラを切り替え・追加・削除したらオーバーレイに反映して全タブ描き直す */
    private fun charactersChanged() {
        OverlayService.refreshIfRunning()
        renderAll()
    }

    private fun charactersCard(): LinearLayout = sectionCard {
        addView(smallLabel(getString(R.string.label_chars)))
        addView(descLabel(getString(R.string.desc_chars)))
        addView(spacer(12))

        Prefs.ensureCharacters(this@MainActivity, getString(R.string.char_default_name, 1))
        val names = Prefs.getCharacterNames(this@MainActivity)
        val active = Prefs.getActiveCharacter(this@MainActivity)

        names.forEachIndexed { i, name ->
            val isActive = i == active
            addView(TextView(this@MainActivity).apply {
                text = if (isActive) "$name  ${getString(R.string.label_in_use)}" else name
                textSize = 15f
                setTextColor(if (isActive) accent else ink)
            })

            val buttons = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            if (!isActive) {
                buttons.addView(Button(this@MainActivity).apply {
                    text = getString(R.string.btn_use)
                    setOnClickListener {
                        Prefs.switchCharacter(this@MainActivity, i)
                        charactersChanged()
                    }
                })
            }
            buttons.addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_rename)
                setOnClickListener {
                    showTextDialog(getString(R.string.dialog_char_name_title), name, "") { newName ->
                        Prefs.renameCharacter(this@MainActivity, i, newName)
                        renderPreset()
                    }
                }
            })
            buttons.addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_remove)
                setOnClickListener {
                    if (names.size <= 1) {
                        Toast.makeText(this@MainActivity, getString(R.string.toast_last_char), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(getString(R.string.confirm_delete_char, name))
                        .setPositiveButton(getString(R.string.btn_remove)) { _, _ ->
                            Prefs.deleteCharacter(this@MainActivity, i)
                            charactersChanged()
                        }
                        .setNegativeButton(getString(R.string.btn_cancel), null)
                        .show()
                }
            })
            addView(buttons)
            addView(spacer(10))
        }

        addView(Button(this@MainActivity).apply {
            text = getString(R.string.btn_add_char)
            setOnClickListener {
                showTextDialog(
                    getString(R.string.dialog_char_name_title),
                    getString(R.string.char_default_name, names.size + 1), ""
                ) { newName ->
                    Prefs.addCharacter(this@MainActivity, newName, copyCurrent = false)
                    charactersChanged()
                }
            }
        })
        addView(Button(this@MainActivity).apply {
            text = getString(R.string.btn_dup_char)
            setOnClickListener {
                val base = names.getOrNull(active) ?: ""
                showTextDialog(
                    getString(R.string.dialog_char_name_title),
                    getString(R.string.copy_suffix, base), ""
                ) { newName ->
                    Prefs.addCharacter(this@MainActivity, newName, copyCurrent = true)
                    charactersChanged()
                }
            }
        })
    }

    /** 1行テキストを入力するダイアログ(空欄ならOKしても何もしない) */
    private fun showTextDialog(title: String, initial: String, hintText: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            hint = hintText
            setSingleLine(true)
            setSelection(text.length)
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(getString(R.string.btn_ok)) { _, _ ->
                val v = input.text.toString().trim()
                if (v.isNotEmpty()) onOk(v)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    // ---------- イラストタブ ----------

    private val REQ_PICK = 400
    private val CAT_STEP = "step"
    private val CAT_TRIGGER = "trigger"
    private var pendingCategory = ""
    private var pendingIndex = -1
    private val thumbCache = HashMap<String, Bitmap?>()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pendingCategory", pendingCategory)
        outState.putInt("pendingIndex", pendingIndex)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        pendingCategory = savedInstanceState.getString("pendingCategory", "") ?: ""
        pendingIndex = savedInstanceState.getInt("pendingIndex", -1)
    }

    /** 画像設定を変えたらオーバーレイに反映して描き直す */
    private fun imagesChanged() {
        OverlayService.refreshIfRunning()
        renderImages()
    }

    private fun renderImages() {
        imageBox.removeAllViews()
        imageBox.addView(idleCard())
        imageBox.addView(spacer(16))
        imageBox.addView(stepsCard())
        imageBox.addView(spacer(16))
        imageBox.addView(triggersCard())
    }

    /** 待機イラスト(ランダム表示) */
    private fun idleCard(): LinearLayout = sectionCard {
        addView(smallLabel(getString(R.string.label_idle)))
        addView(descLabel(getString(R.string.desc_idle)))
        addView(spacer(10))

        addView(thumbStrip(Prefs.getImages(this@MainActivity, Prefs.CAT_IDLE)) { i ->
            val list = Prefs.getImages(this@MainActivity, Prefs.CAT_IDLE)
            if (i in list.indices) list.removeAt(i)
            Prefs.setImages(this@MainActivity, Prefs.CAT_IDLE, list)
            imagesChanged()
        })

        addView(spacer(8))
        addView(Button(this@MainActivity).apply {
            text = getString(R.string.btn_add_image)
            setOnClickListener { pickImages(Prefs.CAT_IDLE, -1) }
        })
    }

    /** タイピング中イラスト(番号順。各番号に別パターンを登録でき、その番号ではランダム表示) */
    private fun stepsCard(): LinearLayout = sectionCard {
        addView(smallLabel(getString(R.string.label_steps)))
        addView(descLabel(getString(R.string.desc_steps)))
        addView(spacer(12))

        val steps = Prefs.getTypingSteps(this@MainActivity)
        if (steps.isEmpty()) {
            addView(descLabel(getString(R.string.empty_hint)))
            addView(spacer(8))
        }

        steps.forEachIndexed { si, variants ->
            val header = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(TextView(this@MainActivity).apply {
                text = getString(R.string.step_label, si + 1)
                textSize = 14f
                setTextColor(ink)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            header.addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_delete_step)
                setOnClickListener {
                    val s = Prefs.getTypingSteps(this@MainActivity)
                    if (si in s.indices) s.removeAt(si)
                    Prefs.setTypingSteps(this@MainActivity, s)
                    imagesChanged()
                }
            })
            addView(header)
            addView(spacer(6))

            addView(thumbStrip(variants) { i ->
                val s = Prefs.getTypingSteps(this@MainActivity)
                if (si in s.indices && i in s[si].indices) s[si].removeAt(i)
                Prefs.setTypingSteps(this@MainActivity, s)
                imagesChanged()
            })

            addView(spacer(6))
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_add_variant)
                setOnClickListener { pickImages(CAT_STEP, si) }
            })
            addView(spacer(16))
        }

        addView(Button(this@MainActivity).apply {
            text = getString(R.string.btn_add_step)
            setOnClickListener { pickImages(CAT_STEP, steps.size) } // 選んだ画像で新しい番号を作る
        })
    }

    /** 文字で切り替え(反応する文字は複数OK、画像はランダム表示) */
    private fun triggersCard(): LinearLayout = sectionCard {
        addView(smallLabel(getString(R.string.label_triggers)))
        addView(descLabel(getString(R.string.desc_triggers)))
        addView(spacer(12))

        val triggers = Prefs.getTriggers(this@MainActivity)
        if (triggers.isEmpty()) {
            addView(descLabel(getString(R.string.empty_hint)))
            addView(spacer(8))
        }

        triggers.forEachIndexed { ti, t ->
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.trigger_keys_label, formatKeys(t.keys))
                textSize = 14f
                setTextColor(ink)
            })

            val buttons = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_edit_keys)
                setOnClickListener {
                    showKeysDialog(t.keys) { keys ->
                        val all = Prefs.getTriggers(this@MainActivity)
                        if (ti in all.indices) all[ti].keys = keys
                        Prefs.setTriggers(this@MainActivity, all)
                        imagesChanged()
                    }
                }
            })
            buttons.addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_remove)
                setOnClickListener {
                    val all = Prefs.getTriggers(this@MainActivity)
                    if (ti in all.indices) all.removeAt(ti)
                    Prefs.setTriggers(this@MainActivity, all)
                    imagesChanged()
                }
            })
            addView(buttons)
            addView(spacer(6))

            addView(thumbStrip(t.images) { i ->
                val all = Prefs.getTriggers(this@MainActivity)
                if (ti in all.indices && i in all[ti].images.indices) all[ti].images.removeAt(i)
                Prefs.setTriggers(this@MainActivity, all)
                imagesChanged()
            })

            addView(spacer(6))
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_add_image)
                setOnClickListener { pickImages(CAT_TRIGGER, ti) }
            })
            addView(spacer(16))
        }

        addView(Button(this@MainActivity).apply {
            text = getString(R.string.btn_add_trigger)
            setOnClickListener {
                showKeysDialog(emptyList()) { keys ->
                    val all = Prefs.getTriggers(this@MainActivity)
                    all.add(Prefs.Trigger(keys, mutableListOf()))
                    Prefs.setTriggers(this@MainActivity, all)
                    renderImages()
                    pickImages(CAT_TRIGGER, all.size - 1) // そのまま画像選択へ
                }
            }
        })
    }

    private fun formatKeys(keys: List<String>): String =
        keys.joinToString(" ") { getString(R.string.key_format, it) }

    /** 反応する文字を入力するダイアログ(スペース区切りで複数) */
    private fun showKeysDialog(current: List<String>, onOk: (MutableList<String>) -> Unit) {
        val input = EditText(this).apply {
            setText(current.joinToString(" "))
            hint = getString(R.string.dialog_keys_hint)
            setSingleLine(true)
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_keys_title))
            .setView(box)
            .setPositiveButton(getString(R.string.btn_ok)) { _, _ ->
                val keys = input.text.toString()
                    .split(Regex("[\\s\\u3000]+"))
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .toMutableList()
                if (keys.isEmpty()) {
                    Toast.makeText(this, getString(R.string.toast_keys_empty), Toast.LENGTH_SHORT).show()
                } else {
                    onOk(keys)
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    /** サムネイルを横スクロールで並べる */
    private fun thumbStrip(uris: List<String>, onRemove: (Int) -> Unit): View {
        if (uris.isEmpty()) return descLabel(getString(R.string.empty_hint))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        uris.forEachIndexed { i, uri ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, dp(8), 0)
            }
            cell.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#EEE0D3"))
                thumbFor(uri)?.let { setImageBitmap(it) }
            })
            cell.addView(Button(this).apply {
                text = getString(R.string.btn_remove)
                textSize = 11f
                setOnClickListener { onRemove(i) }
            })
            row.addView(cell)
        }
        return HorizontalScrollView(this).apply { addView(row) }
    }

    private fun thumbFor(uri: String): Bitmap? {
        if (thumbCache.containsKey(uri)) return thumbCache[uri]
        val bmp = ImageLoader.load(this, uri, dp(64))
        thumbCache[uri] = bmp
        return bmp
    }

    private fun pickImages(category: String, index: Int) {
        pendingCategory = category
        pendingIndex = index
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQ_PICK)
    }

    // ---------- ホーム画面アイコン(ショートカット) ----------

    private fun createIconShortcut(uri: Uri) {
        val original = try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) { null }

        if (original == null) return

        val squared = cropToSquare(original)
        val icon = IconCompat.createWithBitmap(squared)

        val shortcut = ShortcutInfoCompat.Builder(this, "typing_pet_icon_${System.currentTimeMillis()}")
            .setShortLabel(getString(R.string.app_name))
            .setIcon(icon)
            .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        } else {
            Toast.makeText(this, getString(R.string.toast_icon_not_supported), Toast.LENGTH_LONG).show()
        }
    }

    private fun cropToSquare(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        return Bitmap.createBitmap(src, x, y, size, size)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        if (requestCode == 320) {
            data.data?.let { createIconShortcut(it) }
            return
        }
        if (requestCode != REQ_PICK) return

        // 複数選択(clipData)と単体選択(data)の両方に対応
        val uris = mutableListOf<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) clip.getItemAt(i)?.uri?.let { uris.add(it) }
        } else {
            data.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return

        uris.forEach {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { /* 一部プロバイダでは付与できない場合がある */ }
        }
        val added = uris.map { it.toString() }

        when (pendingCategory) {
            CAT_STEP -> {
                val s = Prefs.getTypingSteps(this)
                if (pendingIndex in s.indices) s[pendingIndex].addAll(added) else s.add(added.toMutableList())
                Prefs.setTypingSteps(this, s)
            }
            CAT_TRIGGER -> {
                val all = Prefs.getTriggers(this)
                if (pendingIndex in all.indices) {
                    all[pendingIndex].images.addAll(added)
                    Prefs.setTriggers(this, all)
                }
            }
            Prefs.CAT_IDLE -> {
                val list = Prefs.getImages(this, Prefs.CAT_IDLE)
                list.addAll(added)
                Prefs.setImages(this, Prefs.CAT_IDLE, list)
            }
        }

        imagesChanged()
    }

    // ---------- 共通UI部品 ----------

    private fun sectionCard(build: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cardBg)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            build()
        }
    }

    private fun smallLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(ink)
    }

    private fun descLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(sub)
        setPadding(0, dp(4), 0, 0)
    }

    private fun spacer(h: Int) = View(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h))
    }

    /** ラベルと値のペアから選択式のボタン列を作る */
    private fun segRow(options: List<Pair<String, String>>, selectedValue: String, onSelect: (String) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEach { (label, value) ->
            val selected = value == selectedValue
            row.addView(Button(this).apply {
                text = label
                setBackgroundColor(if (selected) accent else Color.parseColor("#F3EAE0"))
                setTextColor(if (selected) Color.WHITE else ink)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                }
                setOnClickListener { onSelect(value) }
            })
        }
        return row
    }

    private fun switchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply { text = title; setTextColor(ink); textSize = 15f })
        textCol.addView(TextView(this).apply { text = desc; setTextColor(sub); textSize = 12f })
        row.addView(textCol)
        row.addView(Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        })
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
