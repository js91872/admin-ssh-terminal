package com.adminssh.terminal

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.*
import org.json.JSONArray

/**
 * Local-only command library and terminal theme preview.
 * No SSH connection is attempted in this milestone.
 */
class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("commands_v1", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var commands: LinearLayout
    private lateinit var preview: TextView
    private var themeIndex = 0
    private val palettes = listOf(
        Triple("Classic Green", "#101810", "#75F29A"),
        Triple("White on Black", "#101010", "#F5F5F5"),
        Triple("Amber", "#21170B", "#FFCB70"),
        Triple("Cyan Blue", "#101B2C", "#70DBFF"),
        Triple("Matrix", "#000000", "#00FF41"),
        Triple("Black on White", "#FFFFFF", "#222222")
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeIndex = prefs.getInt("theme", 0).coerceIn(palettes.indices)
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        scroll.addView(root)
        setContentView(scroll)
        heading("Admin SSH Terminal", 24)
        label("Command library • Terminal colors", "#7DD3FC")
        label("SSH connection and execution are not available in this preview build.", "#FBBF24")
        heading("Terminal color preset", 18)
        val selector = Spinner(this)
        selector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, palettes.map { it.first })
        selector.setSelection(themeIndex)
        root.addView(selector)
        preview = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setPadding(18, 22, 18, 22)
            text = "root@server:~# git status\\nOn branch main\\nWorking tree clean"
        }
        root.addView(preview)
        selector.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                themeIndex = position
                prefs.edit().putInt("theme", position).apply()
                applyPalette()
            }
        }
        heading("Saved commands", 18)
        val name = EditText(this).apply { hint = "Task name"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
        val command = EditText(this).apply { hint = "Command (e.g. uptime)"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); typeface = Typeface.MONOSPACE }
        root.addView(name)
        root.addView(command)
        Button(this).apply {
            text = "Save command"
            setOnClickListener {
                val n = name.text.toString().trim()
                val c = command.text.toString().trim()
                if (n.isEmpty() || c.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Enter a name and command", Toast.LENGTH_SHORT).show()
                } else {
                    val items = loadCommands()
                    items.put(org.json.JSONObject().put("name", n).put("command", c))
                    prefs.edit().putString("items", items.toString()).apply()
                    name.text.clear(); command.text.clear()
                    renderCommands()
                }
            }
        }.also { root.addView(it) }
        commands = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(commands)
        renderCommands()
    }
    private fun loadCommands(): JSONArray = try { JSONArray(prefs.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
    private fun renderCommands() {
        commands.removeAllViews()
        val items = loadCommands()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val title = item.optString("name")
            val command = item.optString("command")
            label("$title  —  $command", "#E2E8F0", commands)
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            Button(this).apply {
                text = "Copy"
                setOnClickListener {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(title, command))
                    Toast.makeText(this@MainActivity, "Command copied", Toast.LENGTH_SHORT).show()
                }
            }.also { actions.addView(it) }
            Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    val updated = JSONArray()
                    for (j in 0 until items.length()) if (j != i) updated.put(items.getJSONObject(j))
                    prefs.edit().putString("items", updated.toString()).apply()
                    renderCommands()
                }
            }.also { actions.addView(it) }
            commands.addView(actions)
        }
    }
    private fun applyPalette() {
        val p = palettes[themeIndex]
        preview.setBackgroundColor(Color.parseColor(p.second))
        preview.setTextColor(Color.parseColor(p.third))
    }
    private fun heading(value: String, size: Int) {
        root.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 12); typeface = Typeface.DEFAULT_BOLD
        })
    }
    private fun label(value: String, color: String, target: LinearLayout = root) {
        target.addView(TextView(this).apply {
            text = value; textSize = 14f; setTextColor(Color.parseColor(color))
            setPadding(0, 8, 0, 8); typeface = Typeface.MONOSPACE
        })
    }
}
