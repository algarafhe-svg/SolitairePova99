package com.example.solitairefresh

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,48,32,32) }
    layout.addView(TextView(this).apply { text = "Solitaire Fresh\nFloating panel starter"; textSize = 22f })
    layout.addView(Button(this).apply { text = "Enable floating panel"; setOnClickListener {
      if (!Settings.canDrawOverlays(this@MainActivity)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
      else startService(Intent(this@MainActivity, FloatingPanelService::class.java))
    } })
    layout.addView(Button(this).apply { text = "Start / show panel"; setOnClickListener { if (Settings.canDrawOverlays(this@MainActivity)) startService(Intent(this@MainActivity, FloatingPanelService::class.java)) } })
    setContentView(layout)
  }
  override fun onResume() { super.onResume(); if (Settings.canDrawOverlays(this)) startService(Intent(this, FloatingPanelService::class.java)) }
}
