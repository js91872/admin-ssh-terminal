package com.adminssh.terminal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Offline execution records. Stored in private app storage; no automatic deletion. */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("execution_history_v1", Context.MODE_PRIVATE)
    @Synchronized fun append(server: String, command: String, output: String, exitCode: Int?) {
        val items = list()
        items.put(JSONObject().put("timestamp", System.currentTimeMillis())
            .put("server", server).put("command", command)
            .put("output", output).put("exitCode", exitCode ?: JSONObject.NULL))
        prefs.edit().putString("records", items.toString()).apply()
    }
    fun list(): JSONArray = try { JSONArray(prefs.getString("records", "[]")) } catch (_: Exception) { JSONArray() }
}
