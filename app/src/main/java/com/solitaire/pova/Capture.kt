package com.solitaire.pova

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.delay

object Capture {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private var last: Bitmap? = null
    var w = 0
    var h = 0

    val running get() = projection != null

    @Suppress("DEPRECATION")
    fun start(ctx: Context, code: Int, data: Intent) {
        stop()
        val mgr = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mgr.getMediaProjection(code, data)
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stop() }
        }, Handler(Looper.getMainLooper()))
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        w = dm.widthPixels
        h = dm.heightPixels
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        vd = p.createVirtualDisplay("solitaire-cap", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
        projection = p
        reader = r
    }

    fun stop() {
        try { vd?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        vd = null; reader = null; projection = null; last = null
    }

    /** آخر لقطة متاحة (تبقى نفسها إذا لم تتغير الشاشة) */
    @Synchronized
    fun grab(): Bitmap? {
        val r = reader ?: return last
        val img = r.acquireLatestImage()
        if (img != null) {
            try {
                val pl = img.planes[0]
                val rowPixels = pl.rowStride / pl.pixelStride
                val tmp = Bitmap.createBitmap(rowPixels, h, Bitmap.Config.ARGB_8888)
                tmp.copyPixelsFromBuffer(pl.buffer)
                last = Bitmap.createBitmap(tmp, 0, 0, w, h)
                tmp.recycle()
            } finally {
                img.close()
            }
        }
        return last
    }

    suspend fun frame(): Bitmap? {
        repeat(30) {
            grab()?.let { return it }
            delay(50)
        }
        return null
    }
}

fun saveImage(ctx: Context, bmp: Bitmap, name: String) {
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SolitairePova")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

class CaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("cap", "Screen capture", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "cap")
            .setContentTitle("Solitaire Pova")
            .setContentText("Screen capture is running")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, n)

        val code = intent?.getIntExtra("code", 0) ?: 0
        val data: Intent? = intent?.getParcelableExtra("data")
        if (data != null) Capture.start(this, code, data)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Capture.stop()
        super.onDestroy()
    }
}
