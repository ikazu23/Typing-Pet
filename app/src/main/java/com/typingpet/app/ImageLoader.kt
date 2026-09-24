package com.typingpet.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** 画像を必要なサイズまで縮小して読み込む(枚数が増えてもメモリを食いすぎないように) */
object ImageLoader {
    fun load(context: Context, uriStr: String, reqPx: Int): Bitmap? = try {
        val uri = Uri.parse(uriStr)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            while (bounds.outWidth / (sample * 2) >= reqPx && bounds.outHeight / (sample * 2) >= reqPx) {
                sample *= 2
            }
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }
}
