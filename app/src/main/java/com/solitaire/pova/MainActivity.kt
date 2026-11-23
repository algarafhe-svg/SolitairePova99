package com.solitaire.pova

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val reqCapture = 42
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }
        fun btn(t: String, a: () -> Unit) {
            root.addView(Button(this).apply { text = t; setOnClickListener { a() } })
        }
        root.addView(TextView(this).apply { text = "Solitaire Pova Bot"; textSize = 22f })
        btn("1) تفعيل خدمة إمكانية الوصول") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btn("2) السماح بالتقاط الشاشة") {
            val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(m.createScreenCaptureIntent(), reqCapture)
        }
        status = TextView(this).apply { textSize = 16f; setPadding(0, 20, 0, 20) }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "3) افتح اللعبة في لعبة جديدة قبل أي حركة، ثم اضغط الزر ▶ العائم أعلى الشاشة.\n" +
                "ضغطة مطولة على الزر = حفظ صورتي تشخيص في Pictures/SolitairePova."
        })
        setContentView(root)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqCapture && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, CaptureService::class.java).putExtra("code", resultCode).putExtra("data", data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            status.text = "✔ التقاط الشاشة يعمل"
        }
    }
}
