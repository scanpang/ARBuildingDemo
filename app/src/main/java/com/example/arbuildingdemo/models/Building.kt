package com.example.arbuildingdemo.models

import android.graphics.Color
import android.graphics.RectF

/**
 * 건물 정보 데이터 클래스
 */
data class Building(
    val id: String,
    val name: String,
    val distance: Int, // 미터 단위 (더미)
    val colorName: String,
    val description: String? = null,
    val address: String? = null
) {
    val color: Int
        get() = when (colorName) {
            "orange" -> Color.parseColor("#FF6B35")
            "blue" -> Color.parseColor("#3B82F6")
            "purple" -> Color.parseColor("#8B5CF6")
            "green" -> Color.parseColor("#22C55E")
            else -> Color.parseColor("#3B82F6")
        }

    companion object {
        val dummyBuildings = listOf(
            Building(
                id = "gs_tower",
                name = "GS타워",
                distance = 125,
                colorName = "orange",
                description = "지상 40층, 지하 6층의 초고층 빌딩",
                address = "서울 강남구 논현로 508"
            ),
            Building(
                id = "teheran_building",
                name = "테헤란로 빌딩",
                distance = 85,
                colorName = "blue",
                description = "테헤란로의 랜드마크 오피스 빌딩",
                address = "서울 강남구 테헤란로 152"
            ),
            Building(
                id = "yeoksam_tower",
                name = "역삼 타워",
                distance = 110,
                colorName = "purple",
                description = "역삼역 인근 프리미엄 오피스",
                address = "서울 강남구 역삼동 123"
            ),
            Building(
                id = "gangnam_finance",
                name = "강남파이낸스센터",
                distance = 200,
                colorName = "green",
                description = "강남 금융의 중심지",
                address = "서울 강남구 테헤란로 152"
            ),
            Building(
                id = "samsung_town",
                name = "삼성타운",
                distance = 180,
                colorName = "blue",
                description = "삼성그룹 본사 빌딩",
                address = "서울 서초구 서초대로74길 11"
            )
        )
    }
}

/**
 * 객체 시그니처 - 장기 기억용 복합 특징
 */
data class ObjectSignature(
    // Shape 범위 (다양한 각도 허용)
    var minAspectRatio: Float,
    var maxAspectRatio: Float,
    var avgAspectRatio: Float,

    // 크기 범위 (거리 변화 허용) - 화면 대비 비율
    var minSize: Float,
    var maxSize: Float,

    // 마지막 위치 (정규화 좌표 0~1)
    var lastX: Float,
    var lastY: Float,

    // 실제 거리 범위 (ARCore Depth API) - 미터 단위
    var minRealDistance: Float? = null,
    var maxRealDistance: Float? = null,

    // 색상 평균 (RGB)
    var avgColorR: Float = 0f,
    var avgColorG: Float = 0f,
    var avgColorB: Float = 0f,
    var hasColorInfo: Boolean = false
) {
    /**
     * 새로운 관측값으로 시그니처 업데이트
     */
    fun update(
        aspectRatio: Float,
        size: Float,
        x: Float,
        y: Float,
        realDistance: Float? = null,
        colorR: Float? = null,
        colorG: Float? = null,
        colorB: Float? = null
    ) {
        // Shape 범위 확장
        if (aspectRatio < minAspectRatio) minAspectRatio = aspectRatio
        if (aspectRatio > maxAspectRatio) maxAspectRatio = aspectRatio
        avgAspectRatio = avgAspectRatio * 0.9f + aspectRatio * 0.1f

        // 크기 범위 확장
        if (size < minSize) minSize = size
        if (size > maxSize) maxSize = size

        // 위치 이동 평균
        lastX = lastX * 0.7f + x * 0.3f
        lastY = lastY * 0.7f + y * 0.3f

        // 실제 거리 범위 확장
        realDistance?.let { dist ->
            minRealDistance = minRealDistance?.let { minOf(it, dist) } ?: dist
            maxRealDistance = maxRealDistance?.let { maxOf(it, dist) } ?: dist
        }

        // 색상 정보 업데이트
        if (colorR != null && colorG != null && colorB != null) {
            if (hasColorInfo) {
                avgColorR = avgColorR * 0.8f + colorR * 0.2f
                avgColorG = avgColorG * 0.8f + colorG * 0.2f
                avgColorB = avgColorB * 0.8f + colorB * 0.2f
            } else {
                avgColorR = colorR
                avgColorG = colorG
                avgColorB = colorB
                hasColorInfo = true
            }
        }
    }

    /**
     * 다른 관측값과의 유사도 계산 (0~1)
     */
    fun calculateSimilarity(
        aspectRatio: Float,
        size: Float,
        x: Float,
        y: Float,
        realDistance: Float? = null,
        colorR: Float? = null,
        colorG: Float? = null,
        colorB: Float? = null
    ): Float {
        var totalScore = 0f
        var totalWeight = 0f

        // 1. Shape 범위 체크 (25%)
        val shapeWeight = 0.25f
        val shapeMargin = (maxAspectRatio - minAspectRatio) * 0.3f + 0.15f
        val inShapeRange = aspectRatio >= (minAspectRatio - shapeMargin) &&
                aspectRatio <= (maxAspectRatio + shapeMargin)
        totalScore += if (inShapeRange) shapeWeight else shapeWeight * 0.3f
        totalWeight += shapeWeight

        // 2. 크기 범위 체크 (20%)
        val sizeWeight = 0.20f
        val sizeMargin = (maxSize - minSize) * 0.3f + 0.05f
        val inSizeRange = size >= (minSize - sizeMargin) && size <= (maxSize + sizeMargin)
        totalScore += if (inSizeRange) sizeWeight else sizeWeight * 0.3f
        totalWeight += sizeWeight

        // 3. 위치 거리 (25%)
        val posWeight = 0.25f
        val dx = x - lastX
        val dy = y - lastY
        val posDist = dx * dx + dy * dy
        val posScore = (1f - posDist * 2f).coerceIn(0f, 1f)
        totalScore += posScore * posWeight
        totalWeight += posWeight

        // 4. 실제 거리 (20%) - ARCore 사용 시
        if (realDistance != null && minRealDistance != null && maxRealDistance != null) {
            val distWeight = 0.20f
            val distMargin = (maxRealDistance!! - minRealDistance!!) * 0.3f + 0.5f
            val inDistRange = realDistance >= (minRealDistance!! - distMargin) &&
                    realDistance <= (maxRealDistance!! + distMargin)
            totalScore += if (inDistRange) distWeight else distWeight * 0.3f
            totalWeight += distWeight
        }

        // 5. 색상 유사도 (10%)
        if (hasColorInfo && colorR != null && colorG != null && colorB != null) {
            val colorWeight = 0.10f
            val colorDiff = (
                kotlin.math.abs(avgColorR - colorR) +
                kotlin.math.abs(avgColorG - colorG) +
                kotlin.math.abs(avgColorB - colorB)
            ) / 3f / 255f
            val colorSim = (1f - colorDiff).coerceIn(0f, 1f)
            totalScore += colorSim * colorWeight
            totalWeight += colorWeight
        }

        return if (totalWeight > 0f) totalScore / totalWeight else 0f
    }
}

/**
 * 장기 기억에 저장된 건물 정보
 */
data class BuildingMemory(
    val id: String,
    val building: Building,
    val signature: ObjectSignature,
    var lastSeen: Long = System.currentTimeMillis(),
    var matchCount: Int = 1
)

/**
 * 활성 트래커 (단기 추적용)
 */
data class ActiveTracker(
    val id: Int,
    val memoryId: String,
    val building: Building,
    var boundingBox: RectF,
    var smoothBox: RectF,
    var confidence: Float,
    var realDistance: Float? = null,
    var lastSeen: Long = System.currentTimeMillis()
) {
    val centerX: Float get() = smoothBox.centerX()
    val centerY: Float get() = smoothBox.centerY()

    fun updatePosition(
        newBox: RectF,
        newConfidence: Float,
        newRealDistance: Float? = null
    ) {
        boundingBox = newBox
        confidence = newConfidence
        realDistance = newRealDistance
        lastSeen = System.currentTimeMillis()

        // 스무딩 (0.7 = 빠르게 따라감)
        smoothBox = RectF(
            smoothBox.left + (newBox.left - smoothBox.left) * 0.7f,
            smoothBox.top + (newBox.top - smoothBox.top) * 0.7f,
            smoothBox.right + (newBox.right - smoothBox.right) * 0.7f,
            smoothBox.bottom + (newBox.bottom - smoothBox.bottom) * 0.7f
        )
    }
}

/**
 * 감지된 객체 (ML Kit에서 전달)
 */
data class DetectedObject(
    val boundingBox: RectF,
    val confidence: Float,
    val label: String,
    var realDistance: Float? = null,
    var avgColorR: Float? = null,
    var avgColorG: Float? = null,
    var avgColorB: Float? = null
)
