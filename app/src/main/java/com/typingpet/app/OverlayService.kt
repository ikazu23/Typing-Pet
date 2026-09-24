package com.typingpet.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var petView: PetView
    private lateinit var params: WindowManager.LayoutParams
    private var added = false

    companion object {
        var instance: OverlayService? = null
        fun reactIfRunning() {
            instance?.petView?.post { instance?.petView?.react() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        petView = PetView(this)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            260, 260, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 200

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        petView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(petView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    petView.react(); true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(petView, params)
            added = true
        } catch (e: Exception) {
            // オーバーレイ権限が未許可の場合はここに来る
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (added) windowManager.removeView(petView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
