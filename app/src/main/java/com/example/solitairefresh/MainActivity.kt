package com.example.solitairefresh

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var game: KlondikeView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply {
            text = "سوليتير • Klondike"
            textSize = 22f
            setPadding(16, 12, 16, 8)
        })
        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = "لعبة جديدة"; setOnClickListener { game.newGame() } })
        row.addView(Button(this).apply { text = "تلميح"; setOnClickListener { game.showHint() } })
        root.addView(row)
        game = KlondikeView(this)
        root.addView(game, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
}
