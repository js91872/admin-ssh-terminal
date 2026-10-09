package com.adminssh.terminal

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            setBackgroundColor(Color.rgb(12, 22, 31))
        }
        fun line(value: String, size: Float, color: Int) {
            root.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                typeface = Typeface.MONOSPACE
                setPadding(0, 12, 0, 12)
            })
        }
        line("Admin SSH Terminal", 24f, Color.WHITE)
        line("SSH client • Saved commands • Output history", 14f, Color.rgb(117, 242, 154))
        line("Initial Android project scaffold", 16f, Color.LTGRAY)
        line("SSH connections and history are not implemented in this build.", 13f, Color.LTGRAY)
        setContentView(root)
    }
}
