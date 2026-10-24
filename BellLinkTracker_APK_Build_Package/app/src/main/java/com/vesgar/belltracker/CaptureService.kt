
package com.vesgar.belltracker

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class CaptureService : Service() {

    private val channel = "bell_link_status"

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var active = false
    private var previousCount = -1
    private var stable = 0
    private var bonusLatched = false
    private var lastProcessed = 0L

    private lateinit var detector: BellDetector

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        detector = BellDetector(this)

        val manager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                channel,
                "Bell Link takip",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun notification(message: String): Notification {

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Bell Link takip")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        // Kotlin null-safety düzeltmesi
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (active) {
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(
            "resultCode",
            Activity.RESULT_CANCELED
        )

        val data: Intent? =
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    "resultData",
                    Intent::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>("resultData")
            }

        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {

            startForeground(
                11,
                notification("Ekran izleniyor"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

            val manager =
                getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

            projection = manager.getMediaProjection(
                resultCode,
                data
            )

            val display = resources.displayMetrics

            val w = display.widthPixels
            val h = display.heightPixels

            reader = ImageReader.newInstance(
                w,
                h,
                PixelFormat.RGBA_8888,
                2
            )

            projection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopSelf()
                    }
                },
                handler
            )

            virtualDisplay = projection?.createVirtualDisplay(
                "BellTracker",
                w,
                h,
                display.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface,
                null,
                handler
            )

            active = true

            reader?.setOnImageAvailableListener(
                { imageReader ->

                    val image =
                        imageReader.acquireLatestImage()
                            ?: return@setOnImageAvailableListener

                    try {

                        val now = SystemClock.elapsedRealtime()

                        if (now - lastProcessed >= 1100) {

                            lastProcessed = now

                            val plane = image.planes[0]

                            val stride =
                                plane.rowStride / plane.pixelStride

                            val full = Bitmap.createBitmap(
                                stride,
                                h,
                                Bitmap.Config.ARGB_8888
                            )

                            full.copyPixelsFromBuffer(
                                plane.buffer
                            )

                            val bitmap = Bitmap.createBitmap(
                                full,
                                0,
                                0,
                                w,
                                h
                            )

                            full.recycle()

                            try {
                                executor.execute {
                                    try {
                                        process(bitmap)
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                            } catch (_: RejectedExecutionException) {
                                bitmap.recycle()
                            }
                        }

                    } catch (_: Exception) {
                        // Görüntü işleme hatası
                    } finally {
                        image.close()
                    }

                },
                handler
            )

        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    private fun process(bitmap: Bitmap) {

        val bells = detector.count(bitmap)

        if (bells == previousCount) {
            stable++
        } else {
            previousCount = bells
            stable = 1
        }

        if (stable < 3) return

        val preferences = getSharedPreferences(
            "tracker",
            MODE_PRIVATE
        )

        var bonuses = preferences.getInt(
            "bonuses",
            0
        )

        if (bells >= 5 && !bonusLatched) {

            bonusLatched = true
            bonuses++

            val bonusNotification =
                Notification.Builder(this, channel)
                    .setSmallIcon(
                        android.R.drawable.ic_dialog_info
                    )
                    .setContentTitle(
                        "BONUS ADAYI: $bells ÇAN"
                    )
                    .setContentText(
                        "En az 5 çan algılandı. Oyundan doğrulayın."
                    )
                    .setAutoCancel(true)
                    .build()

            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE)
                    as NotificationManager

            notificationManager.notify(
                12,
                bonusNotification
            )
        }

        if (bells < 5) {
            bonusLatched = false
        }

        preferences.edit()
            .putInt("bells", bells)
            .putInt("bonuses", bonuses)
            .putString("status", "Takip aktif")
            .apply()

        val manager =
            getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager

        manager.notify(
            11,
            notification(
                "Çan: $bells | Bonus kayıt: $bonuses"
            )
        )
    }

    override fun onDestroy() {

        active = false

        reader?.setOnImageAvailableListener(null, null)

        virtualDisplay?.release()
        virtualDisplay = null

        reader?.close()
        reader = null

        projection?.stop()
        projection = null

        executor.shutdown()

        super.onDestroy()
    }
}
