package com.adminssh.terminal

class SessionLog {
    private val lines = StringBuilder()
    @Synchronized fun append(text: String) { lines.append(text) }
    @Synchronized fun snapshot(): String = lines.toString()
}
