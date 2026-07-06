package com.example.ekrantarjimon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var overlayStatus: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var overlayBtn: Button
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayStatus = findViewById(R.id.overlayStatus)
        serviceStatus = findViewById(R.id.serviceStatus)
        overlayBtn = findViewById(R.id.overlayBtn)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)

        overlayBtn.setOnClickListener { requestOverlayPermission() }

        startBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Avval 1-qadamdagi ruxsatni bering.", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
                return@setOnClickListener
            }
            ensureNotificationPermission()
            val intent = Intent(this, ScreenTranslatorService::class.java)
                .setAction(ScreenTranslatorService.ACTION_START)
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "Tarjimon yoqildi. Ekrandagi ko'k tugmani bosing.", Toast.LENGTH_LONG).show()
            refreshUi()
            moveTaskToBack(true)
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, ScreenTranslatorService::class.java))
            Toast.makeText(this, "Tarjimon o'chirildi.", Toast.LENGTH_SHORT).show()
            refreshUi()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val hasOverlay = Settings.canDrawOverlays(this)
        if (hasOverlay) {
            overlayStatus.text = "✓  1. Ekran ustida ko'rsatish — ruxsat berilgan"
            overlayStatus.setTextColor(Color.parseColor("#2E7D32"))
            overlayBtn.text = "Ruxsat berilgan ✓"
            overlayBtn.isEnabled = false
        } else {
            overlayStatus.text = "✗  1. Ekran ustida ko'rsatish — ruxsat kerak"
            overlayStatus.setTextColor(Color.parseColor("#C62828"))
            overlayBtn.text = "Ruxsat berish"
            overlayBtn.isEnabled = true
        }

        val running = ScreenTranslatorService.isRunning
        if (running) {
            serviceStatus.text = "✓  2. Tarjimon ishlayapti"
            serviceStatus.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            serviceStatus.text = "2. Tarjimonni yoqing"
            serviceStatus.setTextColor(Color.parseColor("#757575"))
        }
        startBtn.isEnabled = hasOverlay && !running
        stopBtn.isEnabled = running
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
        Toast.makeText(this, "'Ekran ustida ko'rsatish' ni yoqing, so'ng ortga qayting.", Toast.LENGTH_LONG).show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }
}
