package com.example.arbuildingdemo.services

import android.graphics.Bitmap
import android.graphics.RectF
import android.media.Image
import android.util.Log
import com.example.arbuildingdemo.models.DetectedObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * ML Kit 기반 객체 감지 서비스
 */
class ObjectDetectionService {

    companion object {
        private const val TAG = "ObjectDetection"
        private const val CONFIDENCE_THRESHOLD = 0.6f
    }

    private var objectDetector: ObjectDetector? = null
    private var isInitialized = false

    // 처리 통계
    var avgProcessingTimeMs: Long = 0
        private set
    private var frameCount = 0

    /**
     * 초기화
     */
    fun initialize() {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .enableMultipleObjects()
            .build()

        objectDetector = ObjectDetection.getClient(options)
        isInitialized = true
        Log.d(TAG, "Object detection initialized")
    }

    /**
     * 이미지에서 객체 감지
     */
    suspend fun detect(
        image: Image,
        rotationDegrees: Int,
        screenWidth: Float,
        screenHeight: Float,
        measureDistance: ((RectF) -> Float?)? = null
    ): List<DetectedObject> = withContext(Dispatchers.Default) {

        if (!isInitialized || objectDetector == null) {
            return@withContext emptyList()
        }

        val startTime = System.currentTimeMillis()

        try {
            val inputImage = InputImage.fromMediaImage(image, rotationDegrees)

            val detectedObjects = objectDetector!!.process(inputImage).await()

            // 이미지 크기
            val imageWidth = inputImage.width.toFloat()
            val imageHeight = inputImage.height.toFloat()

            // 스케일 팩터
            val scaleX = screenWidth / imageWidth
            val scaleY = screenHeight / imageHeight

            val results = detectedObjects.mapNotNull { obj ->
                // 신뢰도 확인
                val label = obj.labels.maxByOrNull { it.confidence }
                val confidence = label?.confidence ?: 0f

                if (confidence < CONFIDENCE_THRESHOLD) {
                    return@mapNotNull null
                }

                // 바운딩 박스 변환
                val box = obj.boundingBox
                val scaledBox = RectF(
                    box.left * scaleX,
                    box.top * scaleY,
                    box.right * scaleX,
                    box.bottom * scaleY
                )

                // 실제 거리 측정
                val realDistance = measureDistance?.invoke(scaledBox)

                DetectedObject(
                    boundingBox = scaledBox,
                    confidence = confidence,
                    label = label?.text ?: "Unknown",
                    realDistance = realDistance
                )
            }

            // 처리 시간 통계
            val elapsed = System.currentTimeMillis() - startTime
            avgProcessingTimeMs = (avgProcessingTimeMs * frameCount + elapsed) / (frameCount + 1)
            frameCount++

            results

        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
            emptyList()
        }
    }

    /**
     * Bitmap에서 객체 감지 (대체 메서드)
     */
    suspend fun detectFromBitmap(
        bitmap: Bitmap,
        screenWidth: Float,
        screenHeight: Float,
        measureDistance: ((RectF) -> Float?)? = null
    ): List<DetectedObject> = withContext(Dispatchers.Default) {

        if (!isInitialized || objectDetector == null) {
            return@withContext emptyList()
        }

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val detectedObjects = objectDetector!!.process(inputImage).await()

            val scaleX = screenWidth / bitmap.width
            val scaleY = screenHeight / bitmap.height

            detectedObjects.mapNotNull { obj ->
                val label = obj.labels.maxByOrNull { it.confidence }
                val confidence = label?.confidence ?: 0f

                if (confidence < CONFIDENCE_THRESHOLD) {
                    return@mapNotNull null
                }

                val box = obj.boundingBox
                val scaledBox = RectF(
                    box.left * scaleX,
                    box.top * scaleY,
                    box.right * scaleX,
                    box.bottom * scaleY
                )

                val realDistance = measureDistance?.invoke(scaledBox)

                DetectedObject(
                    boundingBox = scaledBox,
                    confidence = confidence,
                    label = label?.text ?: "Unknown",
                    realDistance = realDistance
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Bitmap detection failed", e)
            emptyList()
        }
    }

    /**
     * 바운딩 박스 영역의 평균 색상 추출
     */
    fun extractAverageColor(bitmap: Bitmap, box: RectF): Triple<Float, Float, Float>? {
        try {
            val left = box.left.toInt().coerceIn(0, bitmap.width - 1)
            val top = box.top.toInt().coerceIn(0, bitmap.height - 1)
            val right = box.right.toInt().coerceIn(left + 1, bitmap.width)
            val bottom = box.bottom.toInt().coerceIn(top + 1, bitmap.height)

            var totalR = 0L
            var totalG = 0L
            var totalB = 0L
            var count = 0

            // 샘플링 (성능을 위해 일부 픽셀만)
            val stepX = ((right - left) / 10).coerceAtLeast(1)
            val stepY = ((bottom - top) / 10).coerceAtLeast(1)

            for (y in top until bottom step stepY) {
                for (x in left until right step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    totalR += (pixel shr 16) and 0xFF
                    totalG += (pixel shr 8) and 0xFF
                    totalB += pixel and 0xFF
                    count++
                }
            }

            if (count == 0) return null

            return Triple(
                totalR.toFloat() / count,
                totalG.toFloat() / count,
                totalB.toFloat() / count
            )

        } catch (e: Exception) {
            Log.e(TAG, "Color extraction failed", e)
            return null
        }
    }

    /**
     * 리소스 해제
     */
    fun close() {
        objectDetector?.close()
        objectDetector = null
        isInitialized = false
    }
}
