package com.example.arbuildingdemo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.arbuildingdemo.databinding.ActivityMainBinding
import com.google.ar.core.ArCoreApk

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            checkARCoreAndProceed()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 스플래시 딜레이 후 권한 체크
        Handler(Looper.getMainLooper()).postDelayed({
            checkPermissions()
        }, 1500)
    }

    private fun checkPermissions() {
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            checkARCoreAndProceed()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun checkARCoreAndProceed() {
        val availability = ArCoreApk.getInstance().checkAvailability(this)

        when {
            availability.isSupported -> {
                // ARCore 설치 확인 및 업데이트
                when (ArCoreApk.getInstance().requestInstall(this, true)) {
                    ArCoreApk.InstallStatus.INSTALLED -> {
                        proceedToARActivity()
                    }
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        // 설치 요청됨, 사용자가 설치 후 다시 앱 실행
                    }
                }
            }
            availability.isTransient -> {
                // 재확인 필요
                Handler(Looper.getMainLooper()).postDelayed({
                    checkARCoreAndProceed()
                }, 500)
            }
            else -> {
                Toast.makeText(this, "이 기기는 ARCore를 지원하지 않습니다", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun proceedToARActivity() {
        startActivity(Intent(this, ARActivity::class.java))
        finish()
    }
}
