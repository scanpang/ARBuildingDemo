package com.example.arbuildingdemo.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.arbuildingdemo.models.Building

/**
 * 건물 라벨 커스텀 뷰
 */
class BuildingLabelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var building: Building? = null
    private var confidence: Float = 0f
    private var realDistance: Float? = null

    // 페인트 객체들
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val confidencePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 크기
    private val cardWidth = 150f
    private val cardHeight = 90f
    private val pinRadius = 16f
    private val cornerRadius = 12f

    init {
        // 텍스트 페인트 설정
        textPaint.apply {
            color = Color.WHITE
            textSize = 14f * resources.displayMetrics.density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        subTextPaint.apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = 13f * resources.displayMetrics.density
            textAlign = Paint.Align.CENTER
        }

        confidencePaint.apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = 11f * resources.displayMetrics.density
            textAlign = Paint.Align.CENTER
        }

        borderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * resources.displayMetrics.density
            color = Color.argb(80, 255, 255, 255)
        }
    }

    fun setBuilding(building: Building) {
        this.building = building
        updateColors()
        invalidate()
    }

    fun setConfidence(confidence: Float) {
        this.confidence = confidence
        invalidate()
    }

    fun setRealDistance(distance: Float?) {
        this.realDistance = distance
        invalidate()
    }

    private fun updateColors() {
        building?.let { b ->
            // 그라데이션 색상 설정
            val baseColor = b.color
            val darkerColor = darkenColor(baseColor, 0.8f)

            cardPaint.shader = LinearGradient(
                0f, 0f, cardWidth, cardHeight,
                baseColor, darkerColor,
                Shader.TileMode.CLAMP
            )

            pinPaint.shader = LinearGradient(
                0f, 0f, pinRadius * 2, pinRadius * 2,
                baseColor, darkerColor,
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.rgb(r, g, b)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (cardWidth + 20).toInt()
        val height = (cardHeight + 50).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val b = building ?: return

        val density = resources.displayMetrics.density
        val cardW = cardWidth * density / 2
        val cardH = cardHeight * density / 2
        val cornerR = cornerRadius * density / 2
        val pinR = pinRadius * density / 2

        val centerX = width / 2f
        val cardRect = RectF(
            centerX - cardW / 2,
            0f,
            centerX + cardW / 2,
            cardH
        )

        // 그림자
        cardPaint.setShadowLayer(8f * density / 2, 0f, 4f * density / 2, Color.argb(80, 0, 0, 0))

        // 카드 배경
        canvas.drawRoundRect(cardRect, cornerR, cornerR, cardPaint)
        canvas.drawRoundRect(cardRect, cornerR, cornerR, borderPaint)

        // 건물 이름
        canvas.drawText(
            b.name,
            centerX,
            cardRect.top + 25f * density / 2,
            textPaint
        )

        // 거리
        val distanceText = realDistance?.let { "${"%.1f".format(it)}m" } ?: "${b.distance}m"
        canvas.drawText(
            distanceText,
            centerX,
            cardRect.top + 48f * density / 2,
            subTextPaint
        )

        // 신뢰도 배경
        val confText = "${(confidence * 100).toInt()}%"
        val confBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(50, 0, 0, 0)
        }
        val confWidth = confidencePaint.measureText(confText) + 16f * density / 2
        val confRect = RectF(
            centerX - confWidth / 2,
            cardRect.top + 58f * density / 2,
            centerX + confWidth / 2,
            cardRect.top + 75f * density / 2
        )
        canvas.drawRoundRect(confRect, 10f, 10f, confBgPaint)
        canvas.drawText(
            confText,
            centerX,
            cardRect.top + 70f * density / 2,
            confidencePaint
        )

        // 핀 아이콘
        val pinY = cardRect.bottom + 8f * density / 2 + pinR
        pinPaint.setShadowLayer(4f * density / 2, 0f, 2f * density / 2, Color.argb(80, 0, 0, 0))
        canvas.drawCircle(centerX, pinY, pinR, pinPaint)

        // 핀 아이콘 내부 (위치 마커)
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, pinY - 2f * density / 2, pinR * 0.3f, iconPaint)
    }
}
