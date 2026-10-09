package com.adminssh.terminal

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.*
import org.json.JSONArray
import android.app.AlertDialog
import android.content.Intent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        heading("SSH command runner (beta)", 18)
        val host = EditText(this).apply { hint = "Server hostname or IP"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
        val port = EditText(this).apply { hint = "Port (22)"; inputType = 2; setText("22"); setTextColor(Color.WHITE) }
        val user = EditText(this).apply { hint = "Username"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
        val password = EditText(this).apply { hint = "Password"; inputType = 129; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY }
        val remoteCommand = EditText(this).apply { hint = "Command to run"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
        listOf(host, port, user, password, remoteCommand).forEach { root.addView(it) }
        val vault = CredentialVault(this)
        val history = HistoryStore(this)
        val connectionPrefs = getSharedPreferences("ssh_profiles", MODE_PRIVATE)
        host.setText(connectionPrefs.getString("host", ""))
        port.setText(connectionPrefs.getString("port", "22"))
        user.setText(connectionPrefs.getString("user", ""))
        val profileId = { host.text.toString().trim() + ":" + port.text.toString().trim() + ":" + user.text.toString().trim() }
        password.setText(vault.read(profileId()) ?: "")
        val output = TextView(this).apply {
            text = "SSH output will appear here"
            textSize = 13f; typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(210, 225, 235))
            setPadding(12, 20, 12, 20)
            setTextIsSelectable(true)
        }
        root.addView(output)
        Button(this).apply {
            text = "Run SSH command"
            setOnClickListener {
                val h = host.text.toString().trim()
                val pt = port.text.toString().toIntOrNull()
                val u = user.text.toString().trim()
                val pw = password.text.toString()
                val cmd = remoteCommand.text.toString().trim()
                if (h.isEmpty() || pt == null || pt !in 1..65535 || u.isEmpty() || pw.isEmpty() || cmd.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Complete all connection and command fields", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val id = profileId()
                connectionPrefs.edit().putString("host", h).putString("port", pt.toString()).putString("user", u).apply()
                vault.save(id, pw)
                output.text = "Connecting..."
                Thread {
                    try {
                        val trusted = connectionPrefs.getString("hostkey:$h:$pt", null)
                        var newlyApproved: String? = null
                        val result = SshRunner().execute(h, pt, u, pw, cmd, trusted) { fingerprint ->
                            val latch = CountDownLatch(1)
                            var approved = false
                            runOnUiThread {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Verify SSH server identity")
                                    .setMessage("Server: $h:$pt\\nHost key: $fingerprint\\nConfirm this fingerprint through a trusted channel before accepting.")
                                    .setPositiveButton("Trust host") { _, _ -> approved = true; latch.countDown() }
                                    .setNegativeButton("Cancel") { _, _ -> latch.countDown() }
                                    .setOnCancelListener { latch.countDown() }
                                    .show()
                            }
                            if (!latch.await(90, TimeUnit.SECONDS)) false else {
                                if (approved) newlyApproved = fingerprint
                                approved
                            }
                        }
                        newlyApproved?.let { connectionPrefs.edit().putString("hostkey:$h:$pt", it).apply() }
                        history.append("$u@$h:$pt", cmd, result.output, result.exitCode)
                        runOnUiThread { output.text = result.output.ifBlank { "(No output) Exit: ${result.exitCode}" } }
                    } catch (e: Exception) {
                        runOnUiThread { output.text = "SSH error: ${e.javaClass.simpleName}: ${e.message ?: "Connection failed"}" }
                    }
                }.start()
            }
        }.also { root.addView(it) }
        heading("Execution history", 18)
        val search = EditText(this).apply {
            hint = "Search server, command or output"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        root.addView(search)
        val historyResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(historyResults)
        fun refreshHistory(query: String) {
            historyResults.removeAllViews()
            val records = history.list()
            var found = 0
            for (i in records.length() - 1 downTo 0) {
                val item = records.optJSONObject(i) ?: continue
                val text = item.optString("server") + " > " + item.optString("command") +
                    "\\n" + item.optString("output")
                if (query.isNotBlank() && !text.contains(query, ignoreCase = true)) continue
                found++
                val time = java.text.DateFormat.getDateTimeInstance().format(
                    java.util.Date(item.optLong("timestamp"))
                )
                val line = "$time  |  " + text.take(1000)
                label(line, "#CBD5E1", historyResults)
            }
            if (found == 0) label("No matching records", "#94A3B8", historyResults)
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshHistory(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        Button(this).apply {
            text = "Share history as text"
            setOnClickListener {
                val records = history.list()
                val export = StringBuilder()
                for (i in 0 until records.length()) {
                    val item = records.optJSONObject(i) ?: continue
                    export.append("Server: ").append(item.optString("server")).append('\\n')
                        .append("Command: ").append(item.optString("command")).append('\\n')
                        .append("Output:\\n").append(item.optString("output")).append("\\n---\\n")
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Export sensitive terminal history?")
                    .setMessage("Terminal output may contain credentials or other secrets. Review it before sharing.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Continue") { _, _ ->
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, export.toString())
                        }, "Share terminal history"))
                    }.show()
            }
        }.also { root.addView(it) }
        refreshHistory("")

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
