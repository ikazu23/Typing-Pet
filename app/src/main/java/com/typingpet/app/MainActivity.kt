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

    // ---------- イラストタブ ----------

    private val imagePreviews = arrayOfNulls<ImageView>(4)

    private fun renderImages() {
        imageBox.removeAllViews()

        imageBox.addView(sectionCard {
            addView(smallLabel(getString(R.string.label_add_illust)))
            addView(descLabel(getString(R.string.desc_add_illust)))
            addView(spacer(12))

            for (i in 0 until 4) {
                addView(imageSlotRow(i))
                addView(spacer(10))
            }
        })

        imageBox.addView(spacer(16))

        imageBox.addView(sectionCard {
            addView(smallLabel(getString(R.string.label_special)))
            addView(descLabel(getString(R.string.desc_special)))
            addView(spacer(12))

            addView(specialImageRow(
                getString(R.string.label_exclaim),
                Prefs.getExclaimUri(this@MainActivity),
                requestCode = 310,
                onRemove = {
                    Prefs.setExclaimUri(this@MainActivity, null)
                    OverlayService.refreshIfRunning()
                    renderImages()
                }
            ))

            addView(spacer(10))

            addView(specialImageRow(
                getString(R.string.label_question),
                Prefs.getQuestionUri(this@MainActivity),
                requestCode = 311,
                onRemove = {
                    Prefs.setQuestionUri(this@MainActivity, null)
                    OverlayService.refreshIfRunning()
                    renderImages()
                }
            ))
        })
    }

    private fun imageSlotRow(index: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val thumb = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#EEE0D3"))
        }
        imagePreviews[index] = thumb
        val uri = Prefs.getCustomFrameSlots(this)[index]
        if (uri != null) {
            try { thumb.setImageURI(Uri.parse(uri)) } catch (e: Exception) {}
        }
        row.addView(thumb)

        val label = TextView(this).apply {
            text = getString(R.string.frame_label, index + 1)
            setTextColor(ink)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        row.addView(Button(this).apply {
            text = getString(R.string.btn_add)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, 300 + index)
            }
        })

        row.addView(Button(this).apply {
            text = getString(R.string.btn_remove)
            setOnClickListener {
                Prefs.setCustomFrameSlot(this@MainActivity, index, null)
                OverlayService.refreshIfRunning()
                renderImages()
            }
        })

        return row
    }

    /** ！／？専用スロットの1行を作る(枠1〜4とは別で、単一画像のみ) */
    private fun specialImageRow(
        label: String,
        currentUri: String?,
        requestCode: Int,
        onRemove: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val thumb = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#EEE0D3"))
        }
        if (currentUri != null) {
            try { thumb.setImageURI(Uri.parse(currentUri)) } catch (e: Exception) {}
        }
        row.addView(thumb)

        row.addView(TextView(this).apply {
            text = label
            setTextColor(ink)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(Button(this).apply {
            text = getString(R.string.btn_add)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, requestCode)
            }
        })

        row.addView(Button(this).apply {
            text = getString(R.string.btn_remove)
            setOnClickListener { onRemove() }
        })

        return row
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
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        if (requestCode == 320) {
            createIconShortcut(uri)
            return
        }

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { /* 一部プロバイダでは付与できない場合がある */ }

        when {
            requestCode in 300..303 -> {
                val index = requestCode - 300
                Prefs.setCustomFrameSlot(this, index, uri.toString())
                OverlayService.refreshIfRunning()
                renderImages()
            }
            requestCode == 310 -> {
                Prefs.setExclaimUri(this, uri.toString())
                OverlayService.refreshIfRunning()
                renderImages()
            }
            requestCode == 311 -> {
                Prefs.setQuestionUri(this, uri.toString())
                OverlayService.refreshIfRunning()
                renderImages()
            }
        }
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
