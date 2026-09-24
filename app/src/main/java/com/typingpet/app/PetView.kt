package com.typingpet.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 打鍵のたびに 4 種類の表情を切り替える丸っこいペットのイラスト。
 * 画像アセット不要、すべて Canvas 描画。
 */
class PetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var frame = 0
    private var scaleY = 1f
    private var count = 0

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF9D6C") }
    private val cheekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7CC9A9") }
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A2E2C")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val inkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A2E2C") }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14000000") }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#59FFFFFF") }

    /** 打鍵イベントごとに呼ぶ：表情を切り替えてバウンスさせる */
    fun react() {
        count++
        frame = count % 4
        invalidate()

        val anim = ValueAnimator.ofFloat(0.9f, 1f)
        anim.duration = 160
        anim.addUpdateListener {
            scaleY = it.animatedValue as Float
            invalidate()
        }
        anim.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.34f

        canvas.save()
        canvas.scale(1f, scaleY, cx, cy + r * 0.6f)

        // 影
        canvas.drawOval(RectF(cx - r * 0.8f, cy + r * 1.05f, cx + r * 0.8f, cy + r * 1.3f), shadowPaint)

        // 耳
        canvas.drawCircle(cx - r * 0.75f, cy - r * 0.85f, r * 0.28f, bodyPaint)
        canvas.drawCircle(cx + r * 0.75f, cy - r * 0.85f, r * 0.28f, bodyPaint)
        canvas.drawCircle(cx - r * 0.75f, cy - r * 0.85f, r * 0.14f, cheekPaint)
        canvas.drawCircle(cx + r * 0.75f, cy - r * 0.85f, r * 0.14f, cheekPaint)

        // 顔
        canvas.drawCircle(cx, cy, r, bodyPaint)

        // ほっぺ
        canvas.drawCircle(cx - r * 0.62f, cy + r * 0.18f, r * 0.14f, highlightPaint)
        canvas.drawCircle(cx + r * 0.62f, cy + r * 0.18f, r * 0.14f, highlightPaint)

        drawFace(canvas, cx, cy, r)

        canvas.restore()
    }

    private fun drawFace(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val eyeY = cy - r * 0.12f
        val eyeDx = r * 0.45f
        val eyeR = r * 0.12f
        val mouthY = cy + r * 0.42f

        when (frame) {
            0 -> { // ふつう
                canvas.drawCircle(cx - eyeDx, eyeY, eyeR, inkFill)
                canvas.drawCircle(cx + eyeDx, eyeY, eyeR, inkFill)
                val arc = RectF(cx - r * 0.28f, mouthY - r * 0.1f, cx + r * 0.28f, mouthY + r * 0.18f)
                canvas.drawArc(arc, 20f, 140f, false, inkPaint)
            }
            1 -> { // びっくり
                canvas.drawCircle(cx - eyeDx, eyeY, eyeR * 1.3f, inkFill)
                canvas.drawCircle(cx + eyeDx, eyeY, eyeR * 1.3f, inkFill)
                canvas.drawOval(RectF(cx - r * 0.16f, mouthY - r * 0.14f, cx + r * 0.16f, mouthY + r * 0.14f), inkFill)
            }
            2 -> { // にっこり
                val la = RectF(cx - eyeDx - eyeR, eyeY - eyeR, cx - eyeDx + eyeR, eyeY + eyeR)
                val ra = RectF(cx + eyeDx - eyeR, eyeY - eyeR, cx + eyeDx + eyeR, eyeY + eyeR)
                canvas.drawArc(la, 200f, 140f, false, inkPaint)
                canvas.drawArc(ra, 200f, 140f, false, inkPaint)
                val arc = RectF(cx - r * 0.3f, mouthY - r * 0.16f, cx + r * 0.3f, mouthY + r * 0.24f)
                canvas.drawArc(arc, 15f, 150f, false, inkPaint)
            }
            3 -> { // ウインク
                canvas.drawCircle(cx - eyeDx, eyeY, eyeR, inkFill)
                val ra = RectF(cx + eyeDx - eyeR, eyeY - eyeR * 0.4f, cx + eyeDx + eyeR, eyeY + eyeR * 0.4f)
                canvas.drawArc(ra, 200f, 140f, false, inkPaint)
                val arc = RectF(cx - r * 0.26f, mouthY - r * 0.08f, cx + r * 0.26f, mouthY + r * 0.2f)
                canvas.drawArc(arc, 20f, 140f, false, inkPaint)
            }
        }
    }
}
