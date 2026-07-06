package com.example.ekrantarjimon

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    // Ekranni suratga olishga ruxsat so'rash natijasi
    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val intent = Intent(this, ScreenTranslatorService::class.java).apply {
                    putExtra(ScreenTranslatorService.EXTRA_CODE, result.resultCode)
                    putExtra(ScreenTranslatorService.EXTRA_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, intent)
                Toast.makeText(this, "Tarjimon yoqildi. Ekrandagi ko'k tugmani bosing.", Toast.LENGTH_LONG).show()
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "Ekranga ruxsat berilmadi.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        findViewById<TextView>(R.id.info).text =
            "1) 'Boshlash' tugmasini bosing\n" +
            "2) Ekran ustida ko'rsatish ruxsatini bering\n" +
            "3) Ekranni yozib olishga ruxsat bering\n" +
            "4) Istalgan ilovada ko'k tugmani bosing — xitoycha yozuv o'zbekchaga tarjima bo'ladi"

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            startFlow()
        }

        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, ScreenTranslatorService::class.java))
            Toast.makeText(this, "Tarjimon o'chirildi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startFlow() {
        // 1. Ekran ustida ko'rsatish ruxsati
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Iltimos, 'Boshqa ilovalar ustida ko'rsatish' ruxsatini yoqing.", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // 2. Bildirishnoma ruxsati (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        // 3. Ekranni yozib olish ruxsati
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
