package com.typingpet.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * 打鍵のたびに表情(または画像)を切り替える丸っこいペット。
 * customFrames が空なら内蔵の描画イラスト(4色プリセット x 4表情)、
 * 空でなければユーザーが追加した画像を順番に切り替えて表示する。
 * 「！」「？」が入力された時は、専用イラスト(exclaimFrame/questionFrame)が
 * 設定されていればそれを優先表示し、SPECIAL_REVERT_DELAY_MS だけ入力が
 * 止まると自動的に通常表示へ戻る。
 */
class PetView(context: Context) : View(context) {

    companion object {
        private const val SPECIAL_REVERT_DELAY_MS = 1500L
    }

    var preset = 0
        set(value) { field = value; invalidate() }

    var customFrames: List<Bitmap> = emptyList()
        set(value) { field = value; invalidate() }

    var exclaimFrame: Bitmap? = null
        set(value) { field = value; invalidate() }

    var questionFrame: Bitmap? = null
        set(value) { field = value; invalidate() }

    /** 揺れの強さ 0(なし・画像だけ切り替え)〜3(大きく弾む) */
    var shakeLevel = 3

    private var frame = 0
    private var activeSpecial: Bitmap? = null
    private var scaleY = 1f
    private var count = 0

    private val revertHandler = Handler(Looper.getMainLooper())
    private val revertRunnable = Runnable {
        activeSpecial = null
        invalidate()
    }

    private val presetColors = listOf(
        "#FF9D6C" to "#7CC9A9",
        "#6CA8FF" to "#FFD76C",
        "#FF8FC7" to "#B98FFF",
        "#8FD9A8" to "#FFB26C"
    )

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cheekPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A2E2C"); style = Paint.Style.STROKE
        strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
    }
    private val inkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A2E2C") }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14000000") }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#59FFFFFF") }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * 打鍵イベントごとに呼ぶ。
     * special に '!' か '?' を渡すと、対応する専用イラストがあればそれを表示し、
     * 一定時間後に自動で通常表示へ戻る。
     */
    fun react(special: Char? = null) {
        revertHandler.removeCallbacks(revertRunnable)

        activeSpecial = when (special) {
            '!' -> exclaimFrame
            '?' -> questionFrame
            else -> null
        }

        count++
        val frameCount = if (customFrames.isNotEmpty()) customFrames.size else 4
        frame = count % frameCount
        invalidate()

        if (activeSpecial != null) {
            revertHandler.postDelayed(revertRunnable, SPECIAL_REVERT_DELAY_MS)
        }

        if (shakeLevel <= 0) return
        val dip = when (shakeLevel) { 1 -> 0.96f; 2 -> 0.92f; else -> 0.86f }
        ValueAnimator.ofFloat(dip, 1f).apply {
            duration = 160
            addUpdateListener { scaleY = it.animatedValue as Float; invalidate() }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        canvas.save()
        canvas.scale(1f, scaleY, cx, cy + height * 0.16f)

        val special = activeSpecial
        when {
            special != null -> drawBitmapFrame(canvas, cx, cy, special)
            customFrames.isNotEmpty() -> drawBitmapFrame(canvas, cx, cy, customFrames[frame % customFrames.size])
            else -> drawBuiltIn(canvas, cx, cy)
        }

        canvas.restore()
    }

    private fun drawBitmapFrame(canvas: Canvas, cx: Float, cy: Float, bmp: Bitmap) {
        val size = minOf(width, height).toFloat()
        val scale = size / maxOf(bmp.width, bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        canvas.drawOval(RectF(cx - w * 0.4f, cy + h * 0.42f, cx + w * 0.4f, cy + h * 0.5f), shadowPaint)
        canvas.drawBitmap(bmp, null, RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), bitmapPaint)
    }

    private fun drawBuiltIn(canvas: Canvas, cx: Float, cy: Float) {
        val r = minOf(width, height) * 0.34f
        val colors = presetColors[preset.coerceIn(0, presetColors.size - 1)]
        bodyPaint.color = Color.parseColor(colors.first)
        cheekPaint.color = Color.parseColor(colors.second)

        canvas.drawOval(RectF(cx - r * 0.8f, cy + r * 1.05f, cx + r * 0.8f, cy + r * 1.3f), shadowPaint)

        canvas.drawCircle(cx - r * 0.75f, cy - r * 0.85f, r * 0.28f, bodyPaint)
        canvas.drawCircle(cx + r * 0.75f, cy - r * 0.85f, r * 0.28f, bodyPaint)
        canvas.drawCircle(cx - r * 0.75f, cy - r * 0.85f, r * 0.14f, cheekPaint)
        canvas.drawCircle(cx + r * 0.75f, cy - r * 0.85f, r * 0.14f, cheekPaint)

        canvas.drawCircle(cx, cy, r, bodyPaint)

        canvas.drawCircle(cx - r * 0.62f, cy + r * 0.18f, r * 0.14f, highlightPaint)
        canvas.drawCircle(cx + r * 0.62f, cy + r * 0.18f, r * 0.14f, highlightPaint)

        drawFace(canvas, cx, cy, r)
    }

    private fun drawFace(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val eyeY = cy - r * 0.12f
        val eyeDx = r * 0.45f
        val eyeR = r * 0.12f
        val mouthY = cy + r * 0.42f

        when (frame % 4) {
            0 -> {
                canvas.drawCircle(cx - eyeDx, eyeY, eyeR, inkFill)
                canvas.drawCircle(cx + eyeDx, eyeY, eyeR, inkFill)
                canvas.drawArc(RectF(cx - r * 0.28f, mouthY - r * 0.1f, cx + r * 0.28f, mouthY + r * 0.18f), 20f, 140f, false, inkPaint)
            }
            1 -> {
                canvas.drawCircle(cx - eyeDx, eyeY, eyeR * 1.3f, inkFill)
                canvas.drawCircle(cx + eyeDx, eyeY, eyeR * 1.3f, inkFill)
                canvas.drawOval(RectF(cx - r * 0.16f, mouthY - r * 0.14f, cx + r * 0.16f, mouthY + r * 0.14f), inkFill)
            }
            2 -> {
                canvas.drawArc(RectF(cx - eyeDx - eyeR, eyeY - eyeR, cx - eyeDx + eyeR, eyeY + eyeR), 200f, 140f, false, inkPaint)
                canvas.drawArc(RectF(cx + eyeDx - eyeR, eyeY - eyeR, cx + eyeDx + eyeR, eyeY + eyeR), 200f, 140f, false, inkPaint)
                canvas.drawArc(RectF(cx - r * 0.3f, mouthY - r * 0.16f, cx + r * 0.3f, mouthY + r * 0.24f), 15f, 150f, false, inkPaint)
            }
            3 -> {
                canvas.drawCircle(cx - eyeDx, eyeY, eyeR, inkFill)
                canvas.drawArc(RectF(cx + eyeDx - eyeR, eyeY - eyeR * 0.4f, cx + eyeDx + eyeR, eyeY + eyeR * 0.4f), 200f, 140f, false, inkPaint)
                canvas.drawArc(RectF(cx - r * 0.26f, mouthY - r * 0.08f, cx + r * 0.26f, mouthY + r * 0.2f), 20f, 140f, false, inkPaint)
            }
        }
    }
}
