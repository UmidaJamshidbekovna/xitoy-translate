package com.example.ekrantarjimon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenTranslatorService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PROJECTION_RESULT = "projection_result"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "ekran_tarjimon"
        private const val NOTIF_ID = 1

        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var overlayView: OverlayView? = null
    private var loadingView: TextView? = null

    private var mediaProjection: MediaProjection? = null
    private val metrics = DisplayMetrics()

    private val bgThread = HandlerThread("capture").apply { start() }
    private val bgHandler = Handler(bgThread.looper)
    private val uiHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    private var busy = false
    private var pendingCapture = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PROJECTION_RESULT -> setupProjection(intent)
            else -> showBubble() // ACTION_START
        }
        return START_STICKY
    }

    // ---------- Suzuvchi tugma ----------

    private fun showBubble() {
        updateMetrics()
        if (bubbleView != null) return

        val bubble = TextView(this).apply {
            text = "译"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bubble_bg)
            elevation = dp(6).toFloat()
        }

        val params = WindowManager.LayoutParams(
            dp(58), dp(58),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(220)
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 14 || kotlin.math.abs(dy) > 14) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    safeUpdate(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        // Chekkaga yopishtirish
                        params.x = if (params.x + dp(29) < metrics.widthPixels / 2) dp(8)
                        else metrics.widthPixels - dp(66)
                        safeUpdate(bubble, params)
                    } else {
                        onBubbleTap()
                    }
                    true
                }
                else -> false
            }
        }

        bubbleView = bubble
        try {
            windowManager.addView(bubble, params)
        } catch (e: Exception) {
            Toast.makeText(this, "Tugmani chiqarib bo'lmadi. Ruxsatni tekshiring.", Toast.LENGTH_LONG).show()
        }
    }

    private fun onBubbleTap() {
        if (busy) return
        if (mediaProjection == null) {
            // Birinchi marta — ekran ruxsatini so'raymiz
            pendingCapture = true
            val i = Intent(this, ProjectionRequestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } else {
            startCapture()
        }
    }

    private fun setupProjection(intent: Intent) {
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
        if (code == 0 || data == null) {
            pendingCapture = false
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(code, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                mediaProjection = null
            }
        }, uiHandler)

        if (pendingCapture) {
            pendingCapture = false
            startCapture()
        }
    }

    // ---------- Suratga olish ----------

    private fun startCapture() {
        if (busy) return
        busy = true
        removeOverlay()
        bubbleView?.visibility = View.GONE
        uiHandler.postDelayed({ captureScreen() }, 300)
    }

    private fun captureScreen() {
        updateMetrics()
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val projection = mediaProjection
        if (projection == null) {
            finishCapture()
            return
        }

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image, width, height)
                image.close()
                reader.setOnImageAvailableListener(null, null)
                virtualDisplay?.release()
                reader.close()
                uiHandler.post {
                    showLoading()
                    runOcr(bitmap)
                }
            }
        }, bgHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "screen_capture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            bgHandler
        )

        if (virtualDisplay == null) {
            imageReader.close()
            Toast.makeText(this, "Ekranni o'qib bo'lmadi.", Toast.LENGTH_SHORT).show()
            finishCapture()
        }
    }

    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bmp = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, width, height)
    }

    // ---------- OCR + tarjima ----------

    private fun runOcr(bitmap: Bitmap) {
        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val raw = ArrayList<TranslatedBlock>()
                for (block in result.textBlocks) {
                    val box = block.boundingBox ?: continue
                    val txt = block.text.replace("\n", " ").trim()
                    if (txt.isNotEmpty()) raw.add(TranslatedBlock(box, txt))
                }
                if (raw.isEmpty()) {
                    hideLoading()
                    Toast.makeText(this, "Yozuv topilmadi.", Toast.LENGTH_SHORT).show()
                    finishCapture()
                } else {
                    translateAll(raw)
                }
            }
            .addOnFailureListener {
                hideLoading()
                Toast.makeText(this, "O'qishda xatolik.", Toast.LENGTH_SHORT).show()
                finishCapture()
            }
    }

    private fun translateAll(blocks: List<TranslatedBlock>) {
        scope.launch {
            val translated = withContext(Dispatchers.IO) {
                blocks.map { b ->
                    async { TranslatedBlock(b.rect, Translator.translate(b.text)) }
                }.awaitAll()
            }
            hideLoading()
            showOverlay(translated)
            finishCapture()
        }
    }

    // ---------- Ko'rsatish ----------

    private fun showOverlay(blocks: List<TranslatedBlock>) {
        removeOverlay()
        val view = OverlayView(this, blocks) { removeOverlay() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        overlayView = view
        try { windowManager.addView(view, params) } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }

    private fun showLoading() {
        if (loadingView != null) return
        val tv = TextView(this).apply {
            text = "  Tarjima qilinmoqda…  "
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#E6000000"))
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        loadingView = tv
        try { windowManager.addView(tv, params) } catch (_: Exception) {}
    }

    private fun hideLoading() {
        loadingView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        loadingView = null
    }

    private fun finishCapture() {
        busy = false
        bubbleView?.visibility = View.VISIBLE
    }

    // ---------- Yordamchi ----------

    private fun updateMetrics() {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
    }

    private fun safeUpdate(v: View, p: WindowManager.LayoutParams) {
        try { windowManager.updateViewLayout(v, p) } catch (_: Exception) {}
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ekran tarjimon", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)

        return builder
            .setContentTitle("Ekran tarjimon ishlayapti")
            .setContentText("Tarjima uchun ko'k tugmani bosing")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        hideLoading()
        removeOverlay()
        bubbleView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubbleView = null
        mediaProjection?.stop()
        mediaProjection = null
        bgThread.quitSafely()
    }
}
