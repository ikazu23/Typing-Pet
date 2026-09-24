package com.typingpet.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 120, 60, 60)
            setBackgroundColor(Color.parseColor("#FDF6EC"))
        }

        fun spacer(h: Int) = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
        }

        root.addView(TextView(this).apply {
            text = "🐾 タイピングペット"
            textSize = 22f
            setTextColor(Color.parseColor("#3A2E2C"))
            gravity = Gravity.CENTER
        })

        root.addView(spacer(16))

        root.addView(TextView(this).apply {
            text = "下の2つを両方ONにすると、Xなど他のアプリの上にペットが浮かび、文字を打つたびに表情が変わります。"
            textSize = 14f
            setTextColor(Color.parseColor("#8A7A72"))
            gravity = Gravity.CENTER
        })

        root.addView(spacer(40))

        root.addView(Button(this).apply {
            text = "① 他のアプリの上に表示を許可"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        })

        root.addView(spacer(20))

        root.addView(Button(this).apply {
            text = "② アクセシビリティ設定を開く"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        root.addView(spacer(20))

        root.addView(Button(this).apply {
            text = "③ ペットを表示する"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(Intent(this@MainActivity, OverlayService::class.java))
                }
            }
        })

        setContentView(root)
    }
}
