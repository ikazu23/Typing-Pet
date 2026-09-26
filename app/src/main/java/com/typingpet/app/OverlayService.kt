package com.typingpet.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager

/** 表示中のキャラを1体ずつ別ウィンドウで画面に浮かせる(それぞれドラッグで移動できる) */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private class Pet(
        val id: String,
        val view: PetView,
        val params: WindowManager.LayoutParams,
        var keys: List<List<String>> = emptyList(),
        /** 「⌫ 消したとき」の項目の番号(なければ -1) */
        var deleteIdx: Int = -1
    )

    /** キャラid → 表示中のペット */
    private val pets = LinkedHashMap<String, Pet>()

    /** 読み込み済み画像のキャッシュ(設定変更のたびに全部読み直さないように) */
    private val bitmapCache = HashMap<String, Bitmap>()

    @Volatile private var maxKeyLen = 0
    @Volatile private var watchDel = false

    companion object {
        @Volatile var instance: OverlayService? = null

        /** 表示中キャラに登録された文字のうち最長の長さ(0なら文字の反応なし) */
        val maxKeyLength: Int get() = instance?.maxKeyLen ?: 0

        /** 表示中キャラの誰かが「⌫ 消したとき」を登録しているか */
        val watchDelete: Boolean get() = instance?.watchDel ?: false

        /** 文字が消されただけのときに呼ぶ */
        fun reactDelete() {
            instance?.let { s -> s.handler.post { s.pets.values.forEach { p -> p.view.react(p.deleteIdx) } } }
        }

        fun reactIfRunning() {
            instance?.reactAll(null, 0)
        }

        /** 入力直後の数文字を渡して、各キャラが自分の登録文字で反応する */
        fun reactText(window: String, newFrom: Int) {
            instance?.reactAll(window, newFrom)
        }

        /** 設定画面での変更を、動作中のオーバーレイに即反映する */
        fun refreshIfRunning() {
            instance?.let { s -> s.handler.post { s.sync() } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sync()
    }

    private fun reactAll(window: String?, newFrom: Int) {
        handler.post {
            pets.values.forEach { p ->
                val idx = if (window == null) -1 else TriggerMatcher.find(window, newFrom, p.keys)
                p.view.react(idx)
            }
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

    /** 表示中キャラの設定に合わせて、ペットを追加・更新・削除する */
    private fun sync() {
        val chars = Prefs.getCharacters(this)
        val shown = Prefs.getShownIds(this)
        val targets = chars.filter { it.id in shown }
        val targetIds = targets.map { it.id }.toSet()

        // 非表示になったキャラを消す
        pets.keys.filter { it !in targetIds }.forEach { id ->
            pets.remove(id)?.let { p -> try { windowManager.removeView(p.view) } catch (e: Exception) {} }
        }

        // 使われなくなった画像はキャッシュから外す
        val inUse = HashSet<String>()
        targets.forEach { c ->
            inUse.addAll(c.idle); c.steps.forEach { inUse.addAll(it) }; c.triggers.forEach { inUse.addAll(it.images) }
        }
        bitmapCache.keys.retainAll(inUse)

        val req = dpToPx(260) // 最大サイズ(特大)に合わせて縮小読み込み
        fun get(uri: String): Bitmap? =
            bitmapCache[uri] ?: ImageLoader.load(this, uri, req)?.also { bitmapCache[uri] = it }

        val sizePx = dpToPx(Prefs.getSizeDp(this))
        val flags = baseFlags()

        targets.forEach { c ->
            val pet = pets[c.id] ?: createPet(c.id) ?: return@forEach
            pet.params.width = sizePx
            pet.params.height = sizePx
            pet.params.flags = flags
            pet.params.x = c.x
            pet.params.y = c.y
            try { windowManager.updateViewLayout(pet.view, pet.params) } catch (e: Exception) {}

            pet.view.preset = c.preset
            pet.view.shakeLevel = Prefs.getShakeLevel(this)
            pet.view.showShadow = Prefs.getShowShadow(this)
            pet.view.setImages(
                idle = c.idle.mapNotNull { get(it) },
                steps = c.steps.map { s -> s.mapNotNull { get(it) } },
                triggers = c.triggers.map { t -> t.images.mapNotNull { get(it) } }
            )
            // 画像が1枚もない項目は照合しない
            val keysAll = c.triggers.map { if (it.images.isEmpty()) emptyList() else it.keys.toList() }
            pet.deleteIdx = keysAll.indexOfFirst { Prefs.DELETE_KEY in it }
            pet.keys = keysAll.map { l -> l.filter { it != Prefs.DELETE_KEY } }
        }

        maxKeyLen = pets.values.flatMap { it.keys.flatten() }.maxOfOrNull { it.length } ?: 0
        watchDel = pets.values.any { it.deleteIdx >= 0 }
    }

    private fun createPet(id: String): Pet? {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val sizePx = dpToPx(Prefs.getSizeDp(this))
        val params = WindowManager.LayoutParams(sizePx, sizePx, type, baseFlags(), PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.START

        val pet = Pet(id, PetView(this), params)
        setupTouch(pet)
        return try {
            windowManager.addView(pet.view, params)
            pets[id] = pet
            pet
        } catch (e: Exception) {
            null // オーバーレイ権限が未許可の場合はここに来る
        }
    }

    /** キャラごとにドラッグで移動。離した位置をそのキャラに保存する */
    private fun setupTouch(pet: Pet) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        pet.view.setOnTouchListener { _, event ->
            if (Prefs.getPositionLocked(this)) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = pet.params.x; startY = pet.params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    pet.params.x = startX + (event.rawX - touchX).toInt()
                    pet.params.y = startY + (event.rawY - touchY).toInt()
                    try { windowManager.updateViewLayout(pet.view, pet.params) } catch (e: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    Prefs.setCharPos(this, pet.id, pet.params.x, pet.params.y)
                    pet.view.react()
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        pets.values.forEach { p -> try { windowManager.removeView(p.view) } catch (e: Exception) {} }
        pets.clear()
        bitmapCache.clear()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        return START_STICKY // 止められても自動で戻ってくる
    }

    /**
     * 「前面で動いているサービス」にする。
     * 裏にいるアプリはスマホの省電力機能で一時停止されることがあり、
     * そうなるとキャラが触っても動かなくなる(アプリを開くと一気に動く)ので、それを防ぐ。
     * 通知の許可をしていなければ通知は表示されない。前面にできなかった場合もそのまま普通に動く。
     */
    private fun goForeground() {
        try {
            val channelId = "pet"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(channelId, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN)
                )
            }
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION") Notification.Builder(this)
            }
            val notification = builder
                .setSmallIcon(R.drawable.ic_notif_paw)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_text))
                .setContentIntent(open)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            // 前面にできない状況でも、ペットはそのまま表示する
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
