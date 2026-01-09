package com.example.arbuildingdemo.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arbuildingdemo.databinding.ActivityArBinding
import com.example.arbuildingdemo.models.ActiveTracker
import com.example.arbuildingdemo.services.ARCoreService
import com.example.arbuildingdemo.services.BuildingTrackingService
import com.example.arbuildingdemo.services.ObjectDetectionService
import com.google.ar.core.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ARActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "ARActivity"
    }

    private lateinit var binding: ActivityArBinding

    // 서비스들
    private lateinit var arCoreService: ARCoreService
    private lateinit var trackingService: BuildingTrackingService
    private lateinit var detectionService: ObjectDetectionService

    // 상태
    private var isProcessing = false
    private var showDebug = true
    private var frameCount = 0

    // 화면 크기
    private var screenWidth = 0f
    private var screenHeight = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeServices()
        setupUI()
        setupGLSurfaceView()
    }

    private fun initializeServices() {
        arCoreService = ARCoreService(this)
        trackingService = BuildingTrackingService()
        detectionService = ObjectDetectionService()

        // 트래커 업데이트 콜백
        trackingService.onTrackersUpdated = { trackers ->
            runOnUiThread {
                updateLabels(trackers)
                updateStatus(trackers.size)
            }
        }

        // 객체 감지 초기화
        detectionService.initialize()

        // ARCore 초기화
        if (!arCoreService.initialize()) {
            Toast.makeText(this, "ARCore 초기화 실패", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    private fun setupUI() {
        // 설정 버튼 (디버그 토글)
        binding.btnSettings.setOnClickListener {
            showDebug = !showDebug
            binding.debugPanel.visibility = if (showDebug) View.VISIBLE else View.GONE
        }

        // 리셋 버튼
        binding.btnReset.setOnClickListener {
            trackingService.reset()
            binding.labelContainer.removeAllViews()
        }

        // 확인 버튼
        binding.btnConfirm.setOnClickListener {
            // 현재 감지된 건물들 확정 로직
            Toast.makeText(this, "${trackingService.activeTrackerCount}개 건물 확정됨", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupGLSurfaceView() {
        binding.glSurfaceView.apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(this@ARActivity)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    override fun onResume() {
        super.onResume()
        arCoreService.resume()
        binding.glSurfaceView.onResume()

        // 화면 크기 업데이트
        binding.root.post {
            screenWidth = binding.root.width.toFloat()
            screenHeight = binding.root.height.toFloat()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.glSurfaceView.onPause()
        arCoreService.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arCoreService.close()
        detectionService.close()
    }

    // ============================================
    // GLSurfaceView.Renderer 구현
    // ============================================

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // ARCore 프레임 업데이트
        val frame = arCoreService.update() ?: return

        // 카메라 배경 렌더링 (실제 구현시 ARCore 배경 렌더링 필요)
        // drawCameraBackground(frame)

        // 객체 감지 (일정 프레임마다)
        frameCount++
        if (frameCount % 3 == 0 && !isProcessing) {
            processFrame(frame)
        }
    }

    private fun processFrame(frame: Frame) {
        if (screenWidth == 0f || screenHeight == 0f) return

        isProcessing = true

        lifecycleScope.launch {
            try {
                // 카메라 이미지 획득
                val image = arCoreService.getCameraImage()
                if (image != null) {
                    val rotation = arCoreService.getCameraRotation()

                    // 객체 감지
                    val detections = detectionService.detect(
                        image = image,
                        rotationDegrees = rotation,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        measureDistance = { box ->
                            arCoreService.measureDistanceFromBox(box, screenWidth, screenHeight)
                        }
                    )

                    image.close()

                    // 추적 서비스에 전달
                    if (detections.isNotEmpty()) {
                        trackingService.processDetections(
                            detections = detections,
                            screenWidth = screenWidth,
                            screenHeight = screenHeight
                        )
                    }

                    // 디버그 정보 업데이트
                    if (showDebug) {
                        withContext(Dispatchers.Main) {
                            binding.debugText.text = trackingService.getDebugInfo()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame processing error", e)
            } finally {
                isProcessing = false
            }
        }
    }

    private fun updateLabels(trackers: List<ActiveTracker>) {
        // 기존 라벨 제거
        binding.labelContainer.removeAllViews()

        // 새 라벨 추가
        for (tracker in trackers) {
            val labelView = BuildingLabelView(this).apply {
                setBuilding(tracker.building)
                setConfidence(tracker.confidence)
                setRealDistance(tracker.realDistance)
            }

            // 위치 설정
            labelView.x = tracker.centerX - 75f
            labelView.y = tracker.smoothBox.top - 120f

            binding.labelContainer.addView(labelView)
        }
    }

    private fun updateStatus(count: Int) {
        binding.statusText.text = if (count > 0) {
            "${count}개 건물 감지됨"
        } else {
            "건물을 비춰주세요"
        }

        binding.statusDot.setBackgroundResource(
            if (count > 0) android.R.drawable.presence_online
            else android.R.drawable.presence_away
        )
    }
}
