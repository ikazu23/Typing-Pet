package com.typingpet.app

import android.app.Activity
import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(bg)
        }

        root.addView(TextView(this).apply {
            text = "🐾 タイピングペット 設定"
            textSize = 20f
            setTextColor(ink)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(sectionCard {
            addView(smallLabel("権限とペットの表示"))
            addView(spacer(8))
            addView(Button(this).apply {
                text = "① 他のアプリの上に表示を許可"
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            })
            addView(spacer(8))
            addView(Button(this).apply {
                text = "② アクセシビリティ設定を開く"
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            })
            addView(spacer(8))
            addView(Button(this).apply {
                text = "③ ペットを表示する"
                setOnClickListener {
                    if (Settings.canDrawOverlays(this@MainActivity)) {
                        startService(Intent(this@MainActivity, OverlayService::class.java))
                    }
                }
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
        listOf("基本設定", "プリセット", "イラスト").forEachIndexed { i, label ->
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
            addView(smallLabel("サイズ"))
            addView(spacer(8))
            addView(segRow(
                listOf("小" to "small", "中" to "medium", "大" to "large", "特大" to "xlarge"),
                Prefs.getSizeKey(this@MainActivity)
            ) { key ->
                Prefs.setSizeKey(this@MainActivity, key)
                OverlayService.refreshIfRunning()
                renderBasic()
            })
        })

        basicBox.addView(spacer(16))

        basicBox.addView(sectionCard {
            addView(smallLabel("揺れの強さ"))
            addView(descLabel("タイピング時にペットが揺れる度合いです。「なし」は動かず画像だけ切り替わります。"))
            addView(spacer(8))
            addView(segRow(
                listOf("なし" to "0", "1段階" to "1", "2段階" to "2", "3段階" to "3"),
                Prefs.getShakeLevel(this@MainActivity).toString()
            ) { key ->
                Prefs.setShakeLevel(this@MainActivity, key.toInt())
                OverlayService.refreshIfRunning()
                renderBasic()
            })
        })

        basicBox.addView(spacer(16))

        basicBox.addView(sectionCard {
            addView(smallLabel("表示"))
            addView(spacer(4))

            addView(switchRow(
                "常に最前面に固定",
                "ONだと他のアプリを開いたときも自動でペットが現れます。OFFだと③ボタンを押した時だけ表示されます。",
                Prefs.getAlwaysOnTop(this@MainActivity)
            ) { checked ->
                Prefs.setAlwaysOnTop(this@MainActivity, checked)
            })

            addView(spacer(12))

            addView(switchRow(
                "位置ロック(タップを素通し)",
                "ONにするとタップが下のアプリに通り抜け、ドラッグで動かせなくなります。",
                Prefs.getPositionLocked(this@MainActivity)
            ) { checked ->
                Prefs.setPositionLocked(this@MainActivity, checked)
                OverlayService.refreshIfRunning()
            })

            addView(spacer(12))

            addView(Button(this@MainActivity).apply {
                text = "位置を初期化"
                setOnClickListener {
                    Prefs.resetPos(this@MainActivity)
                    OverlayService.refreshIfRunning()
                }
            })
        })
    }

    // ---------- プリセットタブ ----------

    private fun renderPreset() {
        presetBox.removeAllViews()

        presetBox.addView(sectionCard {
            addView(smallLabel("内蔵カラー"))
            addView(descLabel("イラストタブで画像を追加していない間は、ここで選んだ色が使われます。"))
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
                    text = if (i == current) "選択中" else "選ぶ"
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
            addView(smallLabel("自分のイラストを追加(最大4枚)"))
            addView(descLabel("1枚だけ追加した場合はその画像が常に表示され、複数追加すると打鍵ごとに順番に切り替わります。全部削除すると内蔵カラーに戻ります。"))
            addView(spacer(12))

            for (i in 0 until 4) {
                addView(imageSlotRow(i))
                addView(spacer(10))
            }
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
            text = "枠 ${index + 1}"
            setTextColor(ink)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        row.addView(Button(this).apply {
            text = "追加"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, 300 + index)
            }
        })

        row.addView(Button(this).apply {
            text = "削除"
            setOnClickListener {
                Prefs.setCustomFrameSlot(this@MainActivity, index, null)
                OverlayService.refreshIfRunning()
                renderImages()
            }
        })

        return row
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode in 300..303 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { /* 一部プロバイダでは付与できない場合がある */ }

            val index = requestCode - 300
            Prefs.setCustomFrameSlot(this, index, uri.toString())
            OverlayService.refreshIfRunning()
            renderImages()
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
