package com.example.arbuildingdemo.services

import android.graphics.RectF
import android.util.Log
import com.example.arbuildingdemo.models.*
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * 건물 추적 서비스 - 단기 IoU 추적 + 장기 시그니처 기억
 */
class BuildingTrackingService {

    companion object {
        private const val TAG = "BuildingTracking"
        private const val MIN_IOU_THRESHOLD = 0.2f      // IoU 최소 임계값
        private const val MIN_SIMILARITY = 0.45f         // 장기 기억 매칭 최소 유사도
        private const val TRACKER_TIMEOUT_MS = 500L      // 트래커 타임아웃
    }

    // 장기 기억: 등록된 건물들
    private val buildingMemory = mutableListOf<BuildingMemory>()

    // 단기 추적: 현재 화면의 트래커들
    private val activeTrackers = mutableMapOf<Int, ActiveTracker>()

    // 카운터
    private var nextTrackerId = 1
    private var nextBuildingIndex = 0

    // 콜백
    var onTrackersUpdated: ((List<ActiveTracker>) -> Unit)? = null

    // Getters
    val registeredBuildingCount: Int get() = buildingMemory.size
    val activeTrackerCount: Int get() = activeTrackers.size
    fun getActiveTrackers(): List<ActiveTracker> = activeTrackers.values.toList()

    /**
     * 감지된 객체들 처리 (매 프레임 호출)
     */
    fun processDetections(
        detections: List<DetectedObject>,
        screenWidth: Float,
        screenHeight: Float
    ) {
        val now = System.currentTimeMillis()
        val matchedTrackerIds = mutableSetOf<Int>()
        val matchedDetectionIdxs = mutableSetOf<Int>()

        // ============================================
        // 1단계: IoU 기반 단기 추적 (연속 프레임)
        // ============================================
        for (tracker in activeTrackers.values) {
            var bestIoU = MIN_IOU_THRESHOLD
            var bestIdx = -1

            detections.forEachIndexed { idx, det ->
                if (idx !in matchedDetectionIdxs) {
                    val iou = calculateIoU(tracker.boundingBox, det.boundingBox)
                    if (iou > bestIoU) {
                        bestIoU = iou
                        bestIdx = idx
                    }
                }
            }

            if (bestIdx >= 0) {
                val det = detections[bestIdx]

                // 트래커 업데이트
                tracker.updatePosition(
                    newBox = det.boundingBox,
                    newConfidence = det.confidence,
                    newRealDistance = det.realDistance
                )

                // 장기 기억 업데이트
                val memory = buildingMemory.find { it.id == tracker.memoryId }
                memory?.let {
                    val aspectRatio = det.boundingBox.width() / det.boundingBox.height()
                    val size = (det.boundingBox.width() * det.boundingBox.height()) / (screenWidth * screenHeight)
                    val normX = det.boundingBox.centerX() / screenWidth
                    val normY = det.boundingBox.centerY() / screenHeight

                    it.signature.update(
                        aspectRatio = aspectRatio,
                        size = size,
                        x = normX,
                        y = normY,
                        realDistance = det.realDistance,
                        colorR = det.avgColorR,
                        colorG = det.avgColorG,
                        colorB = det.avgColorB
                    )
                    it.lastSeen = now
                }

                matchedTrackerIds.add(tracker.id)
                matchedDetectionIdxs.add(bestIdx)
            }
        }

        // ============================================
        // 2단계: 매칭 안 된 감지 → 장기 기억 조회
        // ============================================
        val usedMemoryIds = activeTrackers.values.map { it.memoryId }.toMutableSet()

        detections.forEachIndexed { idx, det ->
            if (idx in matchedDetectionIdxs) return@forEachIndexed

            val aspectRatio = det.boundingBox.width() / det.boundingBox.height()
            val size = (det.boundingBox.width() * det.boundingBox.height()) / (screenWidth * screenHeight)
            val normX = det.boundingBox.centerX() / screenWidth
            val normY = det.boundingBox.centerY() / screenHeight

            // 장기 기억에서 찾기
            var matchedMemory = findBestMatch(
                aspectRatio = aspectRatio,
                size = size,
                x = normX,
                y = normY,
                realDistance = det.realDistance,
                colorR = det.avgColorR,
                colorG = det.avgColorG,
                colorB = det.avgColorB,
                excludeIds = usedMemoryIds
            )

            if (matchedMemory == null) {
                // 새 건물 등록
                matchedMemory = registerNewBuilding(
                    aspectRatio = aspectRatio,
                    size = size,
                    x = normX,
                    y = normY,
                    realDistance = det.realDistance,
                    colorR = det.avgColorR,
                    colorG = det.avgColorG,
                    colorB = det.avgColorB
                )
            } else {
                // 기존 건물 업데이트
                matchedMemory.signature.update(
                    aspectRatio = aspectRatio,
                    size = size,
                    x = normX,
                    y = normY,
                    realDistance = det.realDistance,
                    colorR = det.avgColorR,
                    colorG = det.avgColorG,
                    colorB = det.avgColorB
                )
                matchedMemory.lastSeen = now
                matchedMemory.matchCount++
            }

            usedMemoryIds.add(matchedMemory.id)

            // 새 트래커 생성
            val tracker = ActiveTracker(
                id = nextTrackerId++,
                memoryId = matchedMemory.id,
                building = matchedMemory.building,
                boundingBox = det.boundingBox,
                smoothBox = RectF(det.boundingBox),
                confidence = det.confidence,
                realDistance = det.realDistance
            )

            activeTrackers[tracker.id] = tracker
            matchedTrackerIds.add(tracker.id)

            Log.d(TAG, "🏢 트래커 생성: #${tracker.id} → ${matchedMemory.building.name}")
        }

        // ============================================
        // 3단계: 안 보이는 트래커 정리
        // ============================================
        val trackersToRemove = mutableListOf<Int>()

        for (tracker in activeTrackers.values) {
            if (tracker.id !in matchedTrackerIds) {
                val elapsed = now - tracker.lastSeen
                if (elapsed > TRACKER_TIMEOUT_MS) {
                    trackersToRemove.add(tracker.id)
                    Log.d(TAG, "🔴 트래커 제거: #${tracker.id}")
                }
            }
        }

        trackersToRemove.forEach { activeTrackers.remove(it) }

        // 콜백 호출
        onTrackersUpdated?.invoke(getActiveTrackers())
    }

    /**
     * 장기 기억에서 최적 매칭 찾기
     */
    private fun findBestMatch(
        aspectRatio: Float,
        size: Float,
        x: Float,
        y: Float,
        realDistance: Float?,
        colorR: Float?,
        colorG: Float?,
        colorB: Float?,
        excludeIds: Set<String>
    ): BuildingMemory? {
        var bestMatch: BuildingMemory? = null
        var bestScore = MIN_SIMILARITY

        for (memory in buildingMemory) {
            if (memory.id in excludeIds) continue

            val score = memory.signature.calculateSimilarity(
                aspectRatio = aspectRatio,
                size = size,
                x = x,
                y = y,
                realDistance = realDistance,
                colorR = colorR,
                colorG = colorG,
                colorB = colorB
            )

            if (score > bestScore) {
                bestScore = score
                bestMatch = memory
            }
        }

        bestMatch?.let {
            Log.d(TAG, "✅ 매칭: ${it.building.name} (${(bestScore * 100).toInt()}%)")
        }

        return bestMatch
    }

    /**
     * 새 건물 등록
     */
    private fun registerNewBuilding(
        aspectRatio: Float,
        size: Float,
        x: Float,
        y: Float,
        realDistance: Float?,
        colorR: Float?,
        colorG: Float?,
        colorB: Float?
    ): BuildingMemory {
        val building = Building.dummyBuildings[nextBuildingIndex % Building.dummyBuildings.size]
        nextBuildingIndex++

        val signature = ObjectSignature(
            minAspectRatio = aspectRatio,
            maxAspectRatio = aspectRatio,
            avgAspectRatio = aspectRatio,
            minSize = size,
            maxSize = size,
            lastX = x,
            lastY = y,
            minRealDistance = realDistance,
            maxRealDistance = realDistance,
            avgColorR = colorR ?: 0f,
            avgColorG = colorG ?: 0f,
            avgColorB = colorB ?: 0f,
            hasColorInfo = colorR != null
        )

        val memory = BuildingMemory(
            id = UUID.randomUUID().toString(),
            building = building,
            signature = signature
        )

        buildingMemory.add(memory)

        Log.d(TAG, """
            🆕 건물 등록: ${building.name}
               위치: (${"%.2f".format(x)}, ${"%.2f".format(y)})
               비율: ${"%.2f".format(aspectRatio)}
               크기: ${"%.1f".format(size * 100)}%
               ${realDistance?.let { "거리: ${"%.2f".format(it)}m" } ?: ""}
        """.trimIndent())

        return memory
    }

    /**
     * IoU (Intersection over Union) 계산
     */
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val xA = max(box1.left, box2.left)
        val yA = max(box1.top, box2.top)
        val xB = min(box1.right, box2.right)
        val yB = min(box1.bottom, box2.bottom)

        if (xB <= xA || yB <= yA) return 0f

        val intersection = (xB - xA) * (yB - yA)
        val area1 = box1.width() * box1.height()
        val area2 = box2.width() * box2.height()
        val union = area1 + area2 - intersection

        return intersection / union
    }

    /**
     * 디버그 정보 문자열
     */
    fun getDebugInfo(): String {
        val sb = StringBuilder()
        sb.appendLine("📦 등록 건물: ${buildingMemory.size}")

        buildingMemory.forEachIndexed { i, m ->
            val sig = m.signature
            sb.appendLine("$i: ${m.building.name}")
            sb.appendLine("   위치(${"%.2f".format(sig.lastX)},${"%.2f".format(sig.lastY)})")
            sb.appendLine("   비율[${"%.2f".format(sig.minAspectRatio)}~${"%.2f".format(sig.maxAspectRatio)}]")
            sb.appendLine("   크기[${"%.1f".format(sig.minSize * 100)}~${"%.1f".format(sig.maxSize * 100)}%]")
            sig.minRealDistance?.let {
                sb.appendLine("   거리[${"%.1f".format(it)}~${"%.1f".format(sig.maxRealDistance)}m]")
            }
        }

        sb.appendLine("\n🎯 활성 트래커: ${activeTrackers.size}")

        return sb.toString()
    }

    /**
     * 모든 기억 초기화
     */
    fun reset() {
        buildingMemory.clear()
        activeTrackers.clear()
        nextTrackerId = 1
        nextBuildingIndex = 0
        Log.d(TAG, "🔄 추적 시스템 초기화")
        onTrackersUpdated?.invoke(emptyList())
    }
}
