package com.example.solitairefresh

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingPanelService : Service() {
  private lateinit var wm: WindowManager
  private lateinit var panel: LinearLayout
  private lateinit var params: WindowManager.LayoutParams
  private var minimized = false
  private var running = false
  private var speed = 1
  private lateinit var status: TextView
  override fun onBind(intent: Intent?): IBinder? = null
  override fun onCreate() {
    super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager
    panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,14,18,14); setBackgroundColor(0xEE202A38.toInt()) }
    val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    header.addView(Button(this).apply { text = "Drag"; setOnTouchListener(DragTouch() ) })
    header.addView(Button(this).apply { text = "—"; setOnClickListener { minimize() } })
    header.addView(Button(this).apply { text = "×"; setOnClickListener { stopSelf() } })
    panel.addView(header)
    status = TextView(this).apply { text = "Status: Ready"; setTextColor(0xFFFFFFFF.toInt()); textSize=16f }
    panel.addView(status)
    panel.addView(LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL
      addView(Button(this@FloatingPanelService).apply { text="Start"; setOnClickListener { running=true; status.text="Status: Running (UI demo)" } })
      addView(Button(this@FloatingPanelService).apply { text="Pause"; setOnClickListener { running=false; status.text="Status: Paused" } })
      addView(Button(this@FloatingPanelService).apply { text="Stop"; setOnClickListener { running=false; status.text="Status: Stopped" } })
    })
    panel.addView(LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL
      addView(Button(this@FloatingPanelService).apply { text="Speed −"; setOnClickListener { speed=(speed-1).coerceAtLeast(1); status.text="Speed $speed" } })
      addView(Button(this@FloatingPanelService).apply { text="Speed +"; setOnClickListener { speed=(speed+1).coerceAtMost(5); status.text="Speed $speed" } })
    })
    params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; x=60; y=180 }
    wm.addView(panel, params)
  }
  private fun minimize() { minimized=!minimized; for(i in 1 until panel.childCount) panel.getChildAt(i).visibility=if(minimized) View.GONE else View.VISIBLE }
  private inner class DragTouch: View.OnTouchListener { var sx=0; var sy=0; var x0=0; var y0=0
    override fun onTouch(v:View, e:MotionEvent):Boolean { when(e.action){ MotionEvent.ACTION_DOWN->{sx=e.rawX.toInt();sy=e.rawY.toInt();x0=params.x;y0=params.y;return true}; MotionEvent.ACTION_MOVE->{params.x=x0+e.rawX.toInt()-sx;params.y=y0+e.rawY.toInt()-sy;wm.updateViewLayout(panel,params);return true} }; return true }
  }
  override fun onDestroy(){ if(::panel.isInitialized) wm.removeView(panel); super.onDestroy() }
}
