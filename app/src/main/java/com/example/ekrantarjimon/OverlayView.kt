package com.example.ekrantarjimon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Bir tarjima qilingan matn bo'lagi va uning ekrandagi joyi. */
data class TranslatedBlock(val rect: Rect, val text: String)

/**
 * Butun ekranni qoplaydigan shaffof qatlam.
 * Har bir xitoycha yozuv ustiga uning o'zbekcha tarjimasini chizadi.
 * Istalgan joyga bosilsa yopiladi.
 */
class OverlayView(
    context: Context,
    private val blocks: List<TranslatedBlock>,
    private val onDismiss: () -> Unit
) : View(context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2000000") // deyarli qora fon
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        textSize = 34f
        textAlign = Paint.Align.CENTER
    }

    init {
        setBackgroundColor(Color.parseColor("#33000000")) // yengil qorong'ilashtirish
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (block in blocks) {
            val r = block.rect
            if (r.width() <= 0 || r.height() <= 0) continue

            // Matn o'lchamini bo'lak balandligiga moslashtiramiz
            var size = (r.height() * 0.62f).coerceIn(24f, 90f)
            textPaint.textSize = size
            // Kenglikka sig'masa kichraytiramiz
            var guard = 0
            while (textPaint.measureText(block.text) > r.width() * 1.15f && size > 18f && guard < 20) {
                size -= 3f
                textPaint.textSize = size
                guard++
            }

            val pad = 8f
            val rectF = RectF(
                r.left - pad,
                r.top - pad,
                r.right + pad,
                (r.top + size * 1.35f) + pad
            )
            canvas.drawRoundRect(rectF, 12f, 12f, bgPaint)
            canvas.drawText(block.text, r.left.toFloat(), r.top + size, textPaint)
        }

        // Pastda ko'rsatma
        canvas.drawText("Yopish uchun bosing", width / 2f, height - 60f, hintPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            onDismiss()
            return true
        }
        return super.onTouchEvent(event)
    }
}
