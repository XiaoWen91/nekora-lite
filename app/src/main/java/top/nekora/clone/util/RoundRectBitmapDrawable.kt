package top.nekora.clone.util

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * 圆角图片 Drawable。
 *
 * 对应原版 zq0：把 Bitmap 画成带圆角的背景，圆角半径 = 16dp。
 * 原版签名：new zq0(bitmap, 16.0f * density, maskColor)
 *
 * 额外叠一层蒙版色，保证任何亮度的图片上文字都看得清。
 */
class RoundRectBitmapDrawable(
    private val bitmap: Bitmap,
    private val radiusPx: Float,
    private val maskColor: Int = 0,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(
            Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, true),
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP
        )
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = maskColor
    }
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        if (Color.alpha(maskColor) > 0) {
            canvas.drawRoundRect(rect, radiusPx, radiusPx, maskPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
