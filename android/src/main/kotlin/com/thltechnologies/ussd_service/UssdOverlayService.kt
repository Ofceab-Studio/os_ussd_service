package com.thltechnologies.ussd_service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat

class UssdOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val CHANNEL_ID = "UssdOverlayServiceChannel"

    companion object {
        private var instance: UssdOverlayService? = null
        private var overlayMessage = "Envoi en cours"
        
        fun isRunning(): Boolean = instance != null
        
        fun updateMessage(message: String) {
            overlayMessage = message
            instance?.updateOverlayText(message)
        }
        
        fun canDrawOverlay(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
        
        fun openOverlaySettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        startForegroundService()
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "USSD Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USSD Service")
            .setContentText("Envoi en cours")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }

    private fun createOverlayView() {
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // ── Root: plain white background (same as login Scaffold) ─────
        val rootLayout = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        // ── TOP HALF: large orange circle (same as login decoration) ──
        // The circle is 1.4× screen width, offset upward by 45% of its size
        val circleSize = (screenW * 1.4).toInt()
        val circleTop = -(circleSize * 0.45).toInt()
        val circleLeft = -(screenW * 0.2).toInt()

        val orangeCircle = android.view.View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                // Radial gradient: #FFAA2A → #FF9000 (same as login)
                colors = intArrayOf(
                    Color.parseColor("#FFAA2A"),
                    Color.parseColor("#FF9000")
                )
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = circleSize * 0.65f
            }
        }
        val circleParams = android.widget.FrameLayout.LayoutParams(circleSize, circleSize).apply {
            topMargin = circleTop
            leftMargin = circleLeft
        }
        rootLayout.addView(orangeCircle, circleParams)

        // Small decorative bubble bottom-right (same as login)
        val bubbleSize = (screenW * 0.5).toInt()
        val bubble = android.view.View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#12FF9000")) // very subtle orange
            }
        }
        val bubbleParams = android.widget.FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = -(bubbleSize / 3)
            rightMargin = -(bubbleSize / 3)
        }
        rootLayout.addView(bubble, bubbleParams)

        // ── KEPLER logo area (top 30% of screen, centered on orange) ──
        val logoArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val logoAreaParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            (screenH * 0.30).toInt()
        ).apply { gravity = Gravity.TOP }

        // "K" letter as logo placeholder (bold white, large)
        val logoText = TextView(this).apply {
            text = "K"
            textSize = 56f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
        logoArea.addView(logoText)

        // "KEPLER" label in white with wide letter spacing
        val brandLabel = TextView(this).apply {
            text = "KEPLER"
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            letterSpacing = 0.4f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        logoArea.addView(brandLabel)
        rootLayout.addView(logoArea, logoAreaParams)

        // ── WHITE CARD: rounded top corners, bottom half of screen ────
        val cardTopRadius = (36 * dm.density).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (28 * dm.density).toInt(),
                (40 * dm.density).toInt(),
                (28 * dm.density).toInt(),
                (28 * dm.density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(
                    cardTopRadius.toFloat(), cardTopRadius.toFloat(),  // top-left
                    cardTopRadius.toFloat(), cardTopRadius.toFloat(),  // top-right
                    0f, 0f,                                            // bottom-right
                    0f, 0f                                             // bottom-left
                )
            }
        }
        val cardParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { topMargin = (screenH * 0.27).toInt() }
        rootLayout.addView(card, cardParams)

        // ── Orange spinner inside white card ───────────────────────────
        val spinnerSize = (56 * dm.density).toInt()
        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#FF9000")
            )
            layoutParams = LinearLayout.LayoutParams(spinnerSize, spinnerSize).apply {
                bottomMargin = (20 * dm.density).toInt()
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        card.addView(spinner)

        // ── Message text ("Envoi en cours") ───────────────────────────
        val messageView = TextView(this).apply {
            tag = "overlay_message"
            text = overlayMessage
            textSize = 18f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#0A1628"))  // AppColors.background (dark navy)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(messageView)

        overlayView = rootLayout

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager?.addView(overlayView, params)
            println("UssdOverlayService: Overlay displayed")
        } catch (e: Exception) {
            println("UssdOverlayService: Error displaying overlay: ${e.message}")
        }
    }

    private fun updateOverlayText(message: String) {
        overlayView?.findViewWithTag<TextView>("overlay_message")?.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            println("UssdOverlayService: Error removing overlay: ${e.message}")
        }
        overlayView = null
        instance = null
        println("UssdOverlayService: Overlay removed")
    }
}
