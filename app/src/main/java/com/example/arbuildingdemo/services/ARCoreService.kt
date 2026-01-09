package com.example.arbuildingdemo.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.Image
import android.util.Log
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ARCore 서비스 - 깊이 측정 및 AR 기능
 */
class ARCoreService(private val context: Context) {

    companion object {
        private const val TAG = "ARCoreService"
    }

    private var session: Session? = null
    private var isDepthSupported = false

    // 현재 프레임 데이터
    private var currentFrame: Frame? = null
    private var depthImage: Image? = null

    /**
     * ARCore 세션 초기화
     */
    fun initialize(): Boolean {
        return try {
            // ARCore 설치 확인
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            if (availability.isTransient) {
                // 재확인 필요
                return false
            }

            if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                Log.e(TAG, "ARCore not supported or not installed")
                return false
            }

            // 세션 생성
            session = Session(context)

            // 깊이 API 지원 확인
            isDepthSupported = session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            Log.d(TAG, "Depth API supported: $isDepthSupported")

            // 설정
            val config = Config(session).apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                focusMode = Config.FocusMode.AUTO
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

                // 깊이 모드 설정
                if (isDepthSupported) {
                    depthMode = Config.DepthMode.AUTOMATIC
                }
            }

            session!!.configure(config)
            Log.d(TAG, "ARCore initialized successfully")
            true
        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e(TAG, "ARCore not installed", e)
            false
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e(TAG, "Device not compatible with ARCore", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ARCore", e)
            false
        }
    }

    /**
     * 세션 시작
     */
    fun resume() {
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available", e)
        }
    }

    /**
     * 세션 일시정지
     */
    fun pause() {
        session?.pause()
    }

    /**
     * 프레임 업데이트
     */
    fun update(): Frame? {
        return try {
            currentFrame = session?.update()

            // 깊이 이미지 획득
            if (isDepthSupported && currentFrame != null) {
                try {
                    depthImage?.close()
                    depthImage = currentFrame!!.acquireDepthImage16Bits()
                } catch (e: NotYetAvailableException) {
                    // 깊이 이미지 아직 준비 안됨
                }
            }

            currentFrame
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available", e)
            null
        }
    }

    /**
     * 화면 좌표에서 실제 거리 측정 (미터)
     */
    fun measureDistance(screenX: Float, screenY: Float, screenWidth: Float, screenHeight: Float): Float? {
        val frame = currentFrame ?: return null

        // 방법 1: Hit Test (평면 기반)
        val hitResults = frame.hitTest(screenX, screenY)
        for (hit in hitResults) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                return hit.distance
            }
            if (trackable is Point) {
                return hit.distance
            }
        }

        // 방법 2: Depth API (깊이 맵 기반)
        if (isDepthSupported && depthImage != null) {
            return getDepthAtPoint(screenX, screenY, screenWidth, screenHeight)
        }

        return null
    }

    /**
     * 바운딩 박스 중심에서 거리 측정
     */
    fun measureDistanceFromBox(box: RectF, screenWidth: Float, screenHeight: Float): Float? {
        return measureDistance(box.centerX(), box.centerY(), screenWidth, screenHeight)
    }

    /**
     * 깊이 맵에서 특정 좌표의 깊이값 가져오기
     */
    private fun getDepthAtPoint(screenX: Float, screenY: Float, screenWidth: Float, screenHeight: Float): Float? {
        val image = depthImage ?: return null

        // 화면 좌표 → 깊이 이미지 좌표 변환
        val depthX = (screenX / screenWidth * image.width).toInt().coerceIn(0, image.width - 1)
        val depthY = (screenY / screenHeight * image.height).toInt().coerceIn(0, image.height - 1)

        // 16비트 깊이값 읽기
        val plane = image.planes[0]
        val buffer = plane.buffer.order(ByteOrder.LITTLE_ENDIAN)
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val index = depthY * rowStride + depthX * pixelStride
        if (index + 1 >= buffer.capacity()) return null

        val depthMm = buffer.getShort(index).toInt() and 0xFFFF

        // 밀리미터 → 미터 변환
        return if (depthMm > 0) depthMm / 1000f else null
    }

    /**
     * 깊이 신뢰도 맵 가져오기
     */
    fun getDepthConfidence(screenX: Float, screenY: Float, screenWidth: Float, screenHeight: Float): Float? {
        val frame = currentFrame ?: return null

        if (!isDepthSupported) return null

        return try {
            val confidenceImage = frame.acquireRawDepthConfidenceImage()
            val confidence = getConfidenceAtPoint(confidenceImage, screenX, screenY, screenWidth, screenHeight)
            confidenceImage.close()
            confidence
        } catch (e: NotYetAvailableException) {
            null
        }
    }

    private fun getConfidenceAtPoint(image: Image, screenX: Float, screenY: Float, screenWidth: Float, screenHeight: Float): Float? {
        val x = (screenX / screenWidth * image.width).toInt().coerceIn(0, image.width - 1)
        val y = (screenY / screenHeight * image.height).toInt().coerceIn(0, image.height - 1)

        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val index = y * rowStride + x * pixelStride
        if (index >= buffer.capacity()) return null

        val confidenceValue = buffer.get(index).toInt() and 0xFF
        return confidenceValue / 255f
    }

    /**
     * 카메라 이미지 가져오기 (ML Kit용)
     */
    fun getCameraImage(): Image? {
        return try {
            currentFrame?.acquireCameraImage()
        } catch (e: NotYetAvailableException) {
            null
        }
    }

    /**
     * 카메라 회전값 가져오기
     */
    fun getCameraRotation(): Int {
        return currentFrame?.let {
            val camera = it.camera
            // 카메라 디스플레이 회전값
            when (context.display?.rotation) {
                android.view.Surface.ROTATION_0 -> 0
                android.view.Surface.ROTATION_90 -> 90
                android.view.Surface.ROTATION_180 -> 180
                android.view.Surface.ROTATION_270 -> 270
                else -> 0
            }
        } ?: 0
    }

    /**
     * 깊이 지원 여부
     */
    fun isDepthModeSupported(): Boolean = isDepthSupported

    /**
     * 세션 종료
     */
    fun close() {
        depthImage?.close()
        session?.close()
        session = null
    }
}
