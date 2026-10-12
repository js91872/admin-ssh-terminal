package com.adminssh.terminal

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("ssh_profiles", MODE_PRIVATE) }
    private val vault by lazy { CredentialVault(this) }
    private val log by lazy { File(filesDir, "ssh-history.txt") }
    private var ssh: InteractiveSsh? = null
    private var connected = false
    private var server = ""
    private var cmdColor = 0xff7dd3fc.toInt()
    private var outColor = 0xffbbf7d0.toInt()
    private var lastCommand = ""
    private val lastOutput = StringBuilder()
    private val transcript = SpannableStringBuilder()
    private lateinit var root: LinearLayout
    private lateinit var screen: TextView
    private lateinit var command: EditText
    private lateinit var scroll: ScrollView
    private val background = 0xff0f172a.toInt()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        cmdColor = prefs.getInt("cmdColor", cmdColor)
        outColor = prefs.getInt("outColor", outColor)
        login()
    }
    private fun page(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(background)
        }
        root.addView(TextView(this).apply {
            text = title; textSize = 22f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        setContentView(root)
    }
    private fun field(hintText: String, initial: String = "", password: Boolean = false): EditText {
        val edit = EditText(this).apply {
            hint = hintText; setText(initial); textSize = 16f
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xff1e293b.toInt())
            setSingleLine(true)
            if (password) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            }
        }
        root.addView(edit, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return edit
    }
    private fun login() {
        connected = false
        page("Connect to VPS")
        val host = field("Server IP or hostname", prefs.getString("host", "").orEmpty())
        val port = field("SSH port", prefs.getString("port", "22").orEmpty())
        port.inputType = InputType.TYPE_CLASS_NUMBER
        val user = field("Username", prefs.getString("user", "").orEmpty())
        user.setAutofillHints(View.AUTOFILL_HINT_USERNAME)
        val profile = { host.text.toString().trim() + ":" + port.text.toString().trim() + ":" + user.text.toString().trim() }
        val password = field("Password", vault.read(profile()).orEmpty(), true)
        val remember = CheckBox(this).apply {
            text = "Remember VPS details"; setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("remember", false)
        }
        root.addView(remember)
        root.addView(TextView(this).apply {
            text = "Android Password Manager autofill may appear when enabled in system settings."
            textSize = 12f; setTextColor(Color.LTGRAY)
        })
        root.addView(Button(this).apply {
            text = "Connect"; isAllCaps = false
            setOnClickListener {
                val h = host.text.toString().trim()
                val p = port.text.toString().toIntOrNull()
                val u = user.text.toString().trim()
                val pw = password.text.toString()
                if (h.isBlank() || h.contains(' ') || p == null || p !in 1..65535 || u.isBlank() || pw.isBlank()) {
                    Toast.makeText(this@MainActivity, "Enter valid server login details", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (remember.isChecked) {
                    prefs.edit().putString("host", h).putString("port", p.toString())
                        .putString("user", u).putBoolean("remember", true).apply()
                    vault.save(profile(), pw)
                } else {
                    val oldId = prefs.getString("host", "").orEmpty() + ":" +
                        prefs.getString("port", "22").orEmpty() + ":" + prefs.getString("user", "").orEmpty()
                    vault.delete(oldId)
                    prefs.edit().remove("host").remove("port").remove("user").putBoolean("remember", false).apply()
                }
                connect(h, p, u, pw)
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16) })
    }
    private fun terminal() {
        page("Admin SSH Terminal")
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(TextView(this).apply {
            text = server; textSize = 12f; setTextColor(Color.LTGRAY)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        val menuButton = Button(this).apply { text = "⋮"; textSize = 20f }
        topRow.addView(menuButton, LinearLayout.LayoutParams(dp(56), dp(48)))
        menuButton.setOnClickListener {
            val popup = PopupMenu(this, menuButton)
            onCreateOptionsMenu(popup.menu)
            popup.setOnMenuItemClickListener { item -> onOptionsItemSelected(item) }
            popup.show()
        }
        root.addView(topRow)
        scroll = ScrollView(this)
        screen = TextView(this).apply {
            textSize = 14f; typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE); setTextIsSelectable(true)
            setPadding(dp(4), dp(14), dp(4), dp(14))
            setLineSpacing(dp(2).toFloat(), 1.08f)
        }
        scroll.addView(screen)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        command = EditText(this).apply {
            hint = "Type command"; setSingleLine(true)
            textSize = 16f; typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setBackgroundColor(0xff1e293b.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnEditorActionListener { _, _, _ -> submit(); true }
        }
        row.addView(command, LinearLayout.LayoutParams(0, dp(52), 1f))
        row.addView(Button(this).apply {
            text = "↵"; setOnClickListener { submit() }
        }, LinearLayout.LayoutParams(dp(62), dp(52)))
        root.addView(row)
    }
    private fun append(value: String, color: Int) {
        if (!::screen.isInitialized) return
        val start = transcript.length
        transcript.append(value)
        transcript.setSpan(ForegroundColorSpan(color), start, transcript.length, 33)
        screen.text = transcript
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
    private fun connect(host: String, port: Int, user: String, password: String) {
        ssh?.disconnect()
        val connection = InteractiveSsh()
        ssh = connection
        server = user + "@" + host + ":" + port
        transcript.clear()
        terminal()
        append("Connecting to " + server + "...\n", outColor)
        Thread {
            var newFingerprint: String? = null
            connection.connect(host, port, user, password, prefs.getString("hostkey:" + host + ":" + port, null),
                { fingerprint ->
                    val latch = CountDownLatch(1)
                    var approved = false
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle("Verify SSH host key")
                            .setMessage("Server: " + host + ":" + port + "\nFingerprint: " + fingerprint +
                                "\nVerify against a trusted server record.")
                            .setPositiveButton("Trust") { _, _ -> approved = true; latch.countDown() }
                            .setNegativeButton("Cancel") { _, _ -> latch.countDown() }
                            .setOnCancelListener { latch.countDown() }.show()
                    }
                    val ok = latch.await(90, TimeUnit.SECONDS) && approved
                    if (ok) newFingerprint = fingerprint
                    ok
                },
                { output ->
                    newFingerprint?.let {
                        prefs.edit().putString("hostkey:" + host + ":" + port, it).apply()
                        newFingerprint = null
                    }
                    runOnUiThread {
                        connected = true
                        val clean = output.replace(Regex("\u001B\\[[0-?]*[ -/]*[@-~]"), "").replace("\r", "")
                        append(clean, outColor)
                        synchronized(lastOutput) { lastOutput.append(clean) }
                        try { log.appendText(clean) } catch (_: Exception) {}
                    }
                },
                { reason ->
                    runOnUiThread {
                        connected = false
                        append("\n" + reason + "\n", outColor)
                        if (::command.isInitialized) command.isEnabled = false
                    }
                })
        }.start()
    }
    private fun submit() {
        val value = command.text.toString()
        if (value.isBlank()) return
        if (!connected) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }
        lastCommand = value
        synchronized(lastOutput) { lastOutput.setLength(0) }
        append("\n$ " + value + "\n", cmdColor)
        try { log.appendText("\n[" + server + "] $ " + value + "\n") } catch (_: Exception) {}
        command.text.clear()
        Thread {
            try { ssh?.send(value) }
            catch (e: Exception) { runOnUiThread { append("Send failed: " + e.message + "\n", outColor) } }
        }.start()
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Command color")
        menu.add(0, 2, 1, "Output color")
        menu.add(0, 3, 2, "Copy last command + output")
        menu.add(0, 4, 3, "View history")
        menu.add(0, 5, 4, "Share TXT history")
        menu.add(0, 6, 5, "Disconnect")
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1, 2 -> {
                val colors = intArrayOf(0xff7dd3fc.toInt(), 0xff86efac.toInt(), Color.WHITE, 0xffffcc66.toInt(), 0xffd8b4fe.toInt())
                AlertDialog.Builder(this).setTitle(if (item.itemId == 1) "Command color" else "Output color")
                    .setItems(arrayOf("Blue", "Green", "White", "Amber", "Purple")) { _, index ->
                        if (item.itemId == 1) cmdColor = colors[index] else outColor = colors[index]
                        prefs.edit().putInt(if (item.itemId == 1) "cmdColor" else "outColor", colors[index]).apply()
                    }.show()
            }
            3 -> {
                val text = "$ " + lastCommand + "\n" + synchronized(lastOutput) { lastOutput.toString() }
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("SSH command and output", text))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            4 -> AlertDialog.Builder(this).setTitle("Saved history")
                .setMessage(if (log.exists()) log.readText().takeLast(20000) else "No history")
                .setPositiveButton("Close", null).show()
            5 -> AlertDialog.Builder(this).setTitle("Share terminal history?")
                .setMessage("History may contain secrets. Review before sharing.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Share") { _, _ ->
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, if (log.exists()) log.readText() else "")
                    }, "Share SSH history"))
                }.show()
            6 -> { ssh?.disconnect(); login() }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
    override fun onDestroy() {
        ssh?.disconnect()
        super.onDestroy()
    }
}
