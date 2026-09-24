package com.typingpet.app

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
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

    /** 読み込み済み画像のキャッシュ(設定変更のたびに全部読み直さないように) */
    private val bitmapCache = HashMap<String, Bitmap>()

    companion object {
        var instance: OverlayService? = null

        fun reactIfRunning() {
            instance?.petView?.post { instance?.petView?.react() }
        }

        /** 登録した文字が入力された時に呼ぶ(index は Prefs.getTriggers の番号) */
        fun reactTrigger(index: Int) {
            instance?.petView?.post { instance?.petView?.react(index) }
        }

        /** 設定画面での変更を、動作中のオーバーレイに即反映する */
        fun refreshIfRunning() {
            instance?.applyPrefs()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        petView = PetView(this)
        petView.preset = Prefs.getPreset(this)
        petView.shakeLevel = Prefs.getShakeLevel(this)
        loadImages()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val sizePx = dpToPx(Prefs.getSizeDp(this))

        params = WindowManager.LayoutParams(sizePx, sizePx, type, baseFlags(), PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = Prefs.getPosX(this)
        params.y = Prefs.getPosY(this)

        setupTouch()

        try {
            windowManager.addView(petView, params)
            added = true
        } catch (e: Exception) {
            // オーバーレイ権限が未許可の場合はここに来る
        }
    }

    private fun baseFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        flags = if (Prefs.getPositionLocked(this)) {
            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        return flags
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun loadImages() {
        val req = dpToPx(260) // 最大サイズ(特大)に合わせて縮小読み込み

        val idleUris = Prefs.getImages(this, Prefs.CAT_IDLE)
        val stepUris = Prefs.getTypingSteps(this)
        val triggers = Prefs.getTriggers(this)

        // 使われなくなった画像はキャッシュから外す
        val inUse = HashSet<String>().apply {
            addAll(idleUris); stepUris.forEach { addAll(it) }; triggers.forEach { addAll(it.images) }
        }
        bitmapCache.keys.retainAll(inUse)

        fun get(uri: String): Bitmap? =
            bitmapCache[uri] ?: ImageLoader.load(this, uri, req)?.also { bitmapCache[uri] = it }

        petView.setImages(
            idle = idleUris.mapNotNull { get(it) },
            steps = stepUris.map { s -> s.mapNotNull { get(it) } },
            triggers = triggers.map { t -> t.images.mapNotNull { get(it) } }
        )
    }

    fun applyPrefs() {
        if (!added) return
        val sizePx = dpToPx(Prefs.getSizeDp(this))
        params.width = sizePx
        params.height = sizePx
        params.flags = baseFlags()
        params.x = Prefs.getPosX(this)
        params.y = Prefs.getPosY(this)
        windowManager.updateViewLayout(petView, params)

        petView.preset = Prefs.getPreset(this)
        petView.shakeLevel = Prefs.getShakeLevel(this)
        loadImages()
    }

    private fun setupTouch() {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        petView.setOnTouchListener { _, event ->
            if (Prefs.getPositionLocked(this)) return@setOnTouchListener false
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
                    Prefs.setPos(this, params.x, params.y)
                    petView.react()
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (added) windowManager.removeView(petView)
        bitmapCache.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
