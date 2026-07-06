package com.example.ekrantarjimon

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Shaffof, ko'rinmas oyna. Faqat "Ekranni yozib olish" ruxsatini so'raydi
 * va natijani xizmatga uzatib, darhol yopiladi.
 */
class ProjectionRequestActivity : ComponentActivity() {

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val intent = Intent(this, ScreenTranslatorService::class.java).apply {
                    action = ScreenTranslatorService.ACTION_PROJECTION_RESULT
                    putExtra(ScreenTranslatorService.EXTRA_CODE, result.resultCode)
                    putExtra(ScreenTranslatorService.EXTRA_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, intent)
            } else {
                Toast.makeText(this, "Ekranga ruxsat berilmadi.", Toast.LENGTH_SHORT).show()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            launcher.launch(mpm.createScreenCaptureIntent())
        } catch (e: Exception) {
            Toast.makeText(this, "Ekran ruxsatini so'rab bo'lmadi.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
