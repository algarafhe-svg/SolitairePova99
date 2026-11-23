package com.solitaire.pova

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BotService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var overlay: TextView? = null

    override fun onServiceConnected() {
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        job?.cancel()
        scope.cancel()
        try { overlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } } catch (_: Exception) {}
        return super.onUnbind(intent)
    }

    // ---------- الزر العائم: نقرة = تشغيل/إيقاف ، ضغطة مطولة = صور تشخيص ----------
    private fun showOverlay() {
        val tv = TextView(this).apply {
            text = "▶"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(0xCC1565C0.toInt())
            setPadding(30, 12, 30, 12)
            setOnClickListener { toggle() }
            setOnLongClickListener { dumpNow(); true }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 30 }
        (getSystemService(WINDOW_SERVICE) as WindowManager).addView(tv, lp)
        overlay = tv
    }

    private fun setRunning(on: Boolean) {
        main.post { overlay?.text = if (on) "■" else "▶" }
    }

    fun toast(msg: String) {
        main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    private fun toggle() {
        if (job?.isActive == true) {
            job?.cancel()
            setRunning(false)
            toast("تم الإيقاف")
        } else {
            if (!Capture.running) { toast("فعّل التقاط الشاشة أولاً من التطبيق (الخطوة 2)"); return }
            setRunning(true)
            job = scope.launch {
                try {
                    BotRunner(this@BotService) { toast(it) }.run()
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) toast("خطأ: ${e.message}")
                } finally {
                    setRunning(false)
                }
            }
        }
    }

    fun dumpNow() {
        scope.launch {
            try {
                val f = Capture.frame() ?: run { toast("لا توجد لقطة"); return@launch }
                val v = Vision(f.width, f.height)
                val t = System.currentTimeMillis()
                saveImage(this@BotService, f, "raw_$t")
                saveImage(this@BotService, v.debugImage(f), "debug_$t")
                toast("حُفظت صورتان في Pictures/SolitairePova")
            } catch (e: Exception) {
                toast("فشل الحفظ: ${e.message}")
            }
        }
    }

    // ---------- الإيماءات ----------
    private suspend fun dispatch(g: GestureDescription): Boolean = suspendCancellableCoroutine { cont ->
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!ok && cont.isActive) cont.resume(false)
    }

    private fun gesture(s: StrokeDescription) = GestureDescription.Builder().addStroke(s).build()

    suspend fun tap(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        return dispatch(gesture(StrokeDescription(p, 0, 60)))
    }

    /**
     * سحب بثلاث مراحل بإصبع واحد متصل: (1) ضغط ثابت على الورقة، (2) انتقال إلى الهدف،
     * (3) ثبات فوق الهدف قبل رفع الإصبع — فتستقر الورقة في مكانها الصحيح.
     */
    suspend fun drag(x1: Float, y1: Float, x2: Float, y2: Float, hold: Long, move: Long, endHold: Long): Boolean {
        val s1 = StrokeDescription(Path().apply { moveTo(x1, y1) }, 0, hold, true)
        if (!dispatch(gesture(s1))) return false
        val s2 = s1.continueStroke(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, 0, move, true)
        if (!dispatch(gesture(s2))) return false
        val s3 = s2.continueStroke(Path().apply { moveTo(x2, y2) }, 0, endHold, false)
        return dispatch(gesture(s3))
    }
}
