package com.typingpet.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import java.io.File
import java.util.UUID
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

class MainActivity : Activity() {

    private var currentTab = 0 // 0:基本 1:プリセット 2:イラスト

    private lateinit var tabBar: LinearLayout
    private lateinit var basicBox: LinearLayout
    private lateinit var presetBox: LinearLayout
    private lateinit var imageBox: LinearLayout
    private lateinit var showBox: LinearLayout

    // ---- 配色(シンプルなアプリ風。角丸だけ効かせる) ----
    private val ink = Color.parseColor("#1D1B19")        // 本文
    private val sub = Color.parseColor("#77716B")        // 補足
    private val bg = Color.parseColor("#F5F4F2")         // 画面の背景
    private val accent = Color.parseColor("#D65A22")     // メインの色(塗り)
    private val accentText = Color.parseColor("#A6461A") // オレンジの文字(白・薄い背景でも読めるよう濃いめ)
    private val accentSoft = Color.parseColor("#FCEEE7") // メインの色の薄い版
    private val cardBg = Color.WHITE
    private val line = Color.parseColor("#ECE9E5")       // 枠線
    private val tonal = Color.parseColor("#F2F0ED")      // 普通のボタン
    private val danger = Color.parseColor("#C94040")     // 削除
    private val dangerSoft = Color.parseColor("#FBEDEC")

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        // ステータスバー・ナビバーのアイコンを黒に(明るい背景に合わせる)
        var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = flags

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

    /**
     * 画像を選ぶ画面を開く。Googleフォト・ギャラリー・ファイルなど、
     * 画像を選べるアプリの中から好きなものを選べる。
     */
    private fun imagePickerIntent(multiple: Boolean): Intent {
        val get = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        return Intent.createChooser(get, getString(R.string.pick_image_title))
    }

    /** 選んだ画像をアプリ内(images フォルダ)にコピーして、その場所を返す。失敗したら null */
    private fun importImage(uri: Uri): String? {
        return try {
            val dir = File(filesDir, "images").apply { mkdirs() }
            val file = File(dir, UUID.randomUUID().toString())
            val input = contentResolver.openInputStream(uri) ?: return null
            input.use { inp -> file.outputStream().use { out -> inp.copyTo(out) } }
            if (file.length() == 0L) {
                file.delete()
                null
            } else {
                Uri.fromFile(file).toString()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** どのキャラにも使われていないコピー画像を消す(容量を食わないように) */
    private fun cleanupImages() {
        try {
            val dir = File(filesDir, "images")
            if (!dir.exists()) return
            val chars = Prefs.getCharacters(this)
            if (chars.isEmpty()) return // 読み込みに失敗したときは何も消さない
            val used = HashSet<String>()
            chars.forEach { c ->
                used.addAll(c.idle)
                c.steps.forEach { used.addAll(it) }
                c.triggers.forEach { used.addAll(it.images) }
            }
            dir.listFiles()?.forEach { f -> if (Uri.fromFile(f).toString() !in used) f.delete() }
        } catch (e: Exception) { /* 掃除できなくても動作には影響しない */ }
    }

    private fun buildUi() {
        cleanupImages()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(32))
            setBackgroundColor(bg)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, dp(18))
        }
        titleRow.addView(TextView(this).apply {
            text = getString(R.string.title_settings)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(Button(this).apply {
            tag = "small"
            text = "🌐 " + when (Prefs.getLanguage(this@MainActivity)) {
                "ja" -> "日本語"; "en" -> "English"; "ko" -> "한국어"; else -> "Language"
            }
            setOnClickListener { showLanguageDialog(cancelable = true) }
        })
        root.addView(titleRow)

        showBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(showBox)
        root.addView(spacer(16))

        val perm = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        permBox = perm
        root.addView(perm)
        root.addView(spacer(16))

        tabBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(tabBar)
        root.addView(spacer(12))

        basicBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        presetBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        imageBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(basicBox)
        root.addView(presetBox)
        root.addView(imageBox)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root)
        }
        setContentView(scroll)

        renderAll()
        restyle(root)
    }

    // ---------- 権限(許可済みかどうかを1行ずつ表示) ----------

    private var permBox: LinearLayout? = null

    override fun onResume() {
        super.onResume()
        // 設定画面から戻ってきたら許可の状態を更新
        if (permBox != null) renderPerm()
    }

    private fun renderPerm() {
        val box = permBox ?: return
        box.removeAllViews()
        box.addView(sectionCard {
            addView(smallLabel(getString(R.string.section_permission)))
            addView(spacer(6))
            addView(permRow(getString(R.string.btn_overlay), Settings.canDrawOverlays(this@MainActivity)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
            addView(divider())
            addView(permRow(getString(R.string.btn_accessibility), isAccessibilityEnabled()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            addView(spacer(12))
            addView(Button(this@MainActivity).apply {
                tag = "primary"
                text = getString(R.string.btn_show_pet)
                setOnClickListener {
                    if (Settings.canDrawOverlays(this@MainActivity)) {
                        startService(Intent(this@MainActivity, OverlayService::class.java))
                    }
                }
            })
        })
        restyle(box)
    }

    /** 権限1つ分の行。許可済みなら緑の「✓ OK」、まだなら「設定する ›」 */
    private fun permRow(label: String, ok: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(12), dp(2), dp(12))
            isClickable = true
            background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#14000000")), null, rounded(Color.WHITE, 10))
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(ink)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        })
        row.addView(
            if (ok) pill(getString(R.string.perm_ok), Color.parseColor("#2E7D4F"), Color.parseColor("#E6F4EC"))
            else TextView(this).apply {
                text = getString(R.string.perm_set)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(accentText)
            }
        )
        return row
    }

    private fun isAccessibilityEnabled(): Boolean {
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any {
            it.startsWith("$packageName/", ignoreCase = true) && it.contains("PetAccessibilityService", ignoreCase = true)
        }
    }

    /** 細い区切り線 */
    private fun divider() = View(this).apply {
        setBackgroundColor(line)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    /** 1件分(キャラ・番号・文字の反応)をまとめる薄いグレーの箱 */
    private fun itemBox(build: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.parseColor("#F7F6F4"), 12)
        setPadding(dp(12), dp(10), dp(12), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        build()
    }

    // ---------- 表示するキャラ(アイコンをタップで表示/非表示) ----------

    private fun renderShowBar() {
        showBox.removeAllViews()
        val chars = Prefs.getCharacters(this)
        val shown = Prefs.getShownIds(this)

        showBox.addView(sectionCard {
            val head = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(TextView(this@MainActivity).apply {
                text = getString(R.string.label_show_chars)
                textSize = 15f
                setTextColor(ink)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            head.addView(TextView(this@MainActivity).apply {
                text = getString(R.string.show_count, chars.count { it.id in shown })
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(dp(10), dp(3), dp(10), dp(3))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (shown.isEmpty()) sub else accentText)
                }
            })
            addView(head)
            addView(descLabel(getString(R.string.desc_show_chars)))
            addView(spacer(12))

            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            chars.forEach { c -> row.addView(avatarChip(c, c.id in shown)) }
            addView(HorizontalScrollView(this@MainActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            })
        })
        restyle(showBox)
    }

    /** まるいアイコン＋名前のボタン。表示中はオレンジの輪とチェック、非表示は薄く */
    private fun avatarChip(c: Prefs.CharData, isShown: Boolean): View {
        val size = dp(64)
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true
        }

        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(size + dp(8), size + dp(8))
        }

        // 輪っか
        frame.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(size + dp(8), size + dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                if (isShown) setStroke(dp(3), accent) else setStroke(dp(2), Color.parseColor("#E3D6C9"))
            }
        })

        // 中身(登録画像の1枚目、なければ内蔵イラスト)
        val uri = c.idle.firstOrNull() ?: c.steps.flatten().firstOrNull()
        val face: View = if (uri != null) {
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                thumbFor(uri)?.let { setImageBitmap(it) }
            }
        } else {
            PetView(this).apply { preset = c.preset; showShadow = false }
        }
        face.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#F3EAE0"))
        }
        face.clipToOutline = true
        face.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        face.alpha = if (isShown) 1f else 0.4f
        frame.addView(face)

        // 表示中のチェック
        if (isShown) {
            frame.addView(TextView(this).apply {
                text = "✓"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accent)
                    setStroke(dp(2), Color.WHITE)
                }
                layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END)
            })
        }
        cell.addView(frame)

        cell.addView(TextView(this).apply {
            text = c.name
            textSize = 12f
            setTextColor(if (isShown) ink else sub)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(2), dp(4), dp(2), 0)
            layoutParams = LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        cell.setOnClickListener {
            Prefs.setShown(this, c.id, !isShown)
            if (!isShown && OverlayService.instance == null && Settings.canDrawOverlays(this)) {
                startService(Intent(this, OverlayService::class.java))
            } else {
                OverlayService.refreshIfRunning()
            }
            // ぽよっと押した感
            frame.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction {
                renderShowBar()
            }.start()
        }
        return cell
    }

    // ---------- 全体描画 ----------

    private fun renderAll() {
        renderShowBar()
        renderPerm()
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
        val labels = listOf(
            getString(R.string.tab_basic),
            getString(R.string.tab_preset),
            getString(R.string.tab_image)
        )
        tabBar.addView(segmented(labels, currentTab) { i -> currentTab = i; renderAll() })
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
            addView(divider())
            addView(switchRow(
                getString(R.string.switch_lock_title),
                getString(R.string.switch_lock_desc),
                Prefs.getPositionLocked(this@MainActivity)
            ) { checked ->
                Prefs.setPositionLocked(this@MainActivity, checked)
                OverlayService.refreshIfRunning()
            })
            addView(divider())
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
                    startActivityForResult(imagePickerIntent(multiple = false), 320)
                }
            })
        })
        restyle(basicBox)
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
                    setPadding(0, dp(8), 0, dp(8))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = if (i < 3) dp(6) else 0
                    }
                    background = rounded(if (i == current) accentSoft else Color.TRANSPARENT, 14,
                        if (i == current) accent else line)
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
                    setPadding(0, dp(4), 0, 0)
                    setTextColor(if (i == current) accentText else sub)
                    gravity = Gravity.CENTER
                })
                row.addView(cell)
            }
            addView(row)
        })
        restyle(presetBox)
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
            addView(itemBox {
                val nameRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                nameRow.addView(TextView(this@MainActivity).apply {
                    text = name
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(ink)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                if (isActive) {
                    nameRow.addView(pill(getString(R.string.label_in_use).trim('(', ')', '（', '）'), accentText, accentSoft).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = dp(8) }
                    })
                }
                addView(nameRow)
                addView(spacer(8))

                val chips = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                if (!isActive) {
                    chips.addView(Button(this@MainActivity).apply {
                        tag = "small"
                        text = getString(R.string.btn_use)
                        setOnClickListener {
                            Prefs.switchCharacter(this@MainActivity, i)
                            charactersChanged()
                        }
                    })
                }
                chips.addView(Button(this@MainActivity).apply {
                    tag = "small"
                    text = getString(R.string.btn_rename)
                    setOnClickListener {
                        showTextDialog(getString(R.string.dialog_char_name_title), name, "") { newName ->
                            Prefs.renameCharacter(this@MainActivity, i, newName)
                            renderPreset()
                            renderShowBar()
                        }
                    }
                })
                chips.addView(Button(this@MainActivity).apply {
                    tag = "small"
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
                addView(chips)
            })
        }
        addView(spacer(4))

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
        addView(spacer(8))
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
        renderShowBar()
    }

    private fun renderImages() {
        imageBox.removeAllViews()
        imageBox.addView(idleCard())
        imageBox.addView(spacer(16))
        imageBox.addView(stepsCard())
        imageBox.addView(spacer(16))
        imageBox.addView(triggersCard())
        restyle(imageBox)
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
            addView(itemBox {
                val header = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                header.addView(TextView(this@MainActivity).apply {
                    text = getString(R.string.step_label, si + 1)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(ink)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                header.addView(Button(this@MainActivity).apply {
                    tag = "small"
                    text = getString(R.string.btn_delete_step)
                    setOnClickListener {
                        val s = Prefs.getTypingSteps(this@MainActivity)
                        if (si in s.indices) s.removeAt(si)
                        Prefs.setTypingSteps(this@MainActivity, s)
                        imagesChanged()
                    }
                })
                addView(header)
                addView(spacer(8))

                addView(thumbStrip(variants) { i ->
                    val s = Prefs.getTypingSteps(this@MainActivity)
                    if (si in s.indices && i in s[si].indices) s[si].removeAt(i)
                    Prefs.setTypingSteps(this@MainActivity, s)
                    imagesChanged()
                })

                addView(spacer(8))
                addView(Button(this@MainActivity).apply {
                    text = getString(R.string.btn_add_variant)
                    setOnClickListener { pickImages(CAT_STEP, si) }
                })
            })
        }
        addView(spacer(4))

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
            addView(itemBox {
                val header = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                header.addView(TextView(this@MainActivity).apply {
                    text = formatKeys(t.keys)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(ink)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (Prefs.DELETE_KEY !in t.keys) header.addView(Button(this@MainActivity).apply {
                    tag = "small"
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
                header.addView(Button(this@MainActivity).apply {
                    tag = "small"
                    text = getString(R.string.btn_remove)
                    setOnClickListener {
                        val all = Prefs.getTriggers(this@MainActivity)
                        if (ti in all.indices) all.removeAt(ti)
                        Prefs.setTriggers(this@MainActivity, all)
                        imagesChanged()
                    }
                })
                addView(header)
                addView(spacer(8))

                addView(thumbStrip(t.images) { i ->
                    val all = Prefs.getTriggers(this@MainActivity)
                    if (ti in all.indices && i in all[ti].images.indices) all[ti].images.removeAt(i)
                    Prefs.setTriggers(this@MainActivity, all)
                    imagesChanged()
                })

                addView(spacer(8))
                addView(Button(this@MainActivity).apply {
                    text = getString(R.string.btn_add_image)
                    setOnClickListener { pickImages(CAT_TRIGGER, ti) }
                })
            })
        }
        addView(spacer(4))

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

        // 「⌫ 消したとき」は1つだけ作れる
        if (triggers.none { Prefs.DELETE_KEY in it.keys }) {
            addView(spacer(8))
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.btn_add_delete_trigger)
                setOnClickListener {
                    val all = Prefs.getTriggers(this@MainActivity)
                    all.add(Prefs.Trigger(mutableListOf(Prefs.DELETE_KEY), mutableListOf()))
                    Prefs.setTriggers(this@MainActivity, all)
                    renderImages()
                    pickImages(CAT_TRIGGER, all.size - 1) // そのまま画像選択へ
                }
            })
        }
    }

    private fun formatKeys(keys: List<String>): String =
        keys.joinToString(" ") {
            if (it == Prefs.DELETE_KEY) getString(R.string.key_delete) else getString(R.string.key_format, it)
        }

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
            val cell = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(78), dp(78)).apply { marginEnd = dp(6) }
            }
            cell.addView(ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(72), dp(72), Gravity.BOTTOM or Gravity.START)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(Color.parseColor("#ECE9E5"), 12)
                clipToOutline = true
                thumbFor(uri)?.let { setImageBitmap(it) }
            })
            // 右上の × で削除
            cell.addView(TextView(this).apply {
                text = "×"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#CC1D1B19"))
                    setStroke(dp(2), Color.WHITE)
                }
                layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END)
                isClickable = true
                setOnClickListener { onRemove(i) }
            })
            row.addView(cell)
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
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
        startActivityForResult(imagePickerIntent(multiple = true), REQ_PICK)
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

        // Googleフォトなどから選んだ画像は、あとで読めなくなることがあるのでアプリ内にコピーして使う
        val added = uris.mapNotNull { importImage(it) }
        if (added.size < uris.size) {
            Toast.makeText(this, getString(R.string.toast_import_failed), Toast.LENGTH_SHORT).show()
        }
        if (added.isEmpty()) return

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

    /** 角丸の背景(stroke を渡すと細い枠線つき) */
    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    /** 小さいラベル(「編集中」など) */
    private fun pill(text: String, fg: Int, bgColor: Int) = TextView(this).apply {
        this.text = text
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(fg)
        setPadding(dp(8), dp(2), dp(8), dp(2))
        background = rounded(bgColor, 10)
    }

    private fun sectionCard(build: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardBg, 16, line)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            build()
        }
    }

    private fun smallLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ink)
    }

    /** 説明文。長いものは2行で「…」にして、タップで全文を開閉 */
    private fun descLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(sub)
        setLineSpacing(0f, 1.25f)
        setPadding(0, dp(4), 0, 0)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setOnClickListener {
            val open = maxLines == 2
            maxLines = if (open) Int.MAX_VALUE else 2
            ellipsize = if (open) null else TextUtils.TruncateAt.END
        }
    }

    private fun spacer(h: Int) = View(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h))
    }

    /** iOS/Androidでよくある「選択肢を横に並べた切り替え」 */
    private fun segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = rounded(Color.parseColor("#EAE7E3"), 12)
        }
        labels.forEachIndexed { i, label ->
            val on = i == selected
            row.addView(TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(6), dp(9), dp(6), dp(9))
                setTextColor(if (on) ink else sub)
                if (on) {
                    typeface = Typeface.DEFAULT_BOLD
                    background = rounded(cardBg, 10)
                    elevation = dp(1).toFloat()
                } else {
                    background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#14000000")), null, rounded(Color.WHITE, 10))
                }
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { if (!on) onSelect(i) }
            })
        }
        return row
    }

    /** ラベルと値のペアから選択式の切り替えを作る */
    private fun segRow(options: List<Pair<String, String>>, selectedValue: String, onSelect: (String) -> Unit): LinearLayout =
        segmented(options.map { it.first }, options.indexOfFirst { it.second == selectedValue }) { i ->
            onSelect(options[i].second)
        }

    private fun switchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(12)
            }
        }
        textCol.addView(TextView(this).apply { text = title; setTextColor(ink); textSize = 15f })
        textCol.addView(descLabel(desc).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(textCol)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        row.addView(Switch(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList(states, intArrayOf(accent, Color.parseColor("#FAFAFA")))
            trackTintList = ColorStateList(states, intArrayOf(Color.parseColor("#EFA37E"), Color.parseColor("#B9B2AA")))
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        })
        return row
    }

    /**
     * 画面内のボタンをまとめて同じデザインにする。
     * tag="primary" … メインの塗りボタン / tag="small" … 小さい削除ボタン
     * 「削除」系 … 赤文字 / 「＋」で始まる … 追加ボタン(薄いオレンジ) / それ以外 … グレーの普通ボタン
     */
    private fun restyle(v: View) {
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) restyle(v.getChildAt(i))
            return
        }
        if (v !is Button || v is Switch) return

        val label = v.text.toString()
        val removeWords = setOf(getString(R.string.btn_remove), getString(R.string.btn_delete_step))
        val small = v.tag == "small"
        val isRemove = label in removeWords
        val isAdd = label.startsWith("＋") || label.startsWith("+")

        // 文字色 / 塗り / 枠線
        val fg: Int; val fill: Int; val stroke: Int?
        when {
            v.tag == "primary" -> { fg = Color.WHITE; fill = accent; stroke = null }             // 一番大事なボタン: オレンジ塗り
            isRemove -> { fg = danger; fill = Color.WHITE; stroke = Color.parseColor("#F0C9C6") } // 削除: 赤文字
            isAdd -> { fg = accentText; fill = Color.WHITE; stroke = Color.parseColor("#E9B69C") } // 追加: オレンジの枠
            else -> { fg = ink; fill = Color.WHITE; stroke = Color.parseColor("#D9D4CE") }        // 普通: グレーの枠
        }

        v.isAllCaps = false
        v.stateListAnimator = null
        v.elevation = 0f
        v.minHeight = 0; v.minimumHeight = 0
        v.minWidth = 0; v.minimumWidth = 0
        v.setTextColor(fg)
        v.textSize = if (small) 13f else 15f
        v.typeface = if (v.tag == "primary" || isAdd) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (small) v.setPadding(dp(12), dp(7), dp(12), dp(7)) else v.setPadding(dp(16), dp(13), dp(16), dp(13))
        v.background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#1F000000")),
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(if (small) 18 else 12).toFloat()
                if (stroke != null) setStroke(dp(1), stroke)
            },
            null
        )

        // 横並びのボタン同士にすき間
        val lp = v.layoutParams as? LinearLayout.LayoutParams ?: return
        val parent = v.parent as? LinearLayout ?: return
        if (parent.orientation == LinearLayout.HORIZONTAL) {
            lp.marginStart = dp(6)
            v.layoutParams = lp
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
