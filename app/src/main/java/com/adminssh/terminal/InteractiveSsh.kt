package com.adminssh.terminal

import android.util.Base64
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.connection.channel.direct.Session
import java.security.MessageDigest
import java.security.PublicKey

/** Persistent PTY shell. Call connect on a worker thread, never the UI thread. */
class InteractiveSsh {
    private var client: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    @Volatile private var connected = false

    fun connect(
        host: String, port: Int, user: String, password: String,
        trustedFingerprint: String?, approve: (String) -> Boolean,
        onOutput: (String) -> Unit, onExit: (String) -> Unit
    ) {
        try {
            val ssh = SSHClient()
            client = ssh
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun findExistingAlgorithms(hostname: String, port: Int): MutableList<String> = mutableListOf()
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    val bytes = MessageDigest.getInstance("SHA-256").digest(key.encoded)
                    val fingerprint = "SHA256:" + Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING)
                    return if (trustedFingerprint == null) approve(fingerprint) else trustedFingerprint == fingerprint
                }
            })
            ssh.connect(host, port)
            ssh.authPassword(user, password)
            val s = ssh.startSession()
            session = s
            s.allocateDefaultPTY()
            val sh = s.startShell()
            shell = sh
            connected = true
            val input = sh.inputStream
            val bytes = ByteArray(8192)
            while (connected) {
                val count = input.read(bytes)
                if (count < 0) break
                if (count > 0) onOutput(String(bytes, 0, count, Charsets.UTF_8))
            }
            onExit("Connection closed")
        } catch (e: Exception) {
            onExit("Connection failed: " + (e.message ?: e.javaClass.simpleName))
        } finally {
            disconnect()
        }
    }
    @Synchronized fun send(command: String) {
        if (!connected) throw IllegalStateException("Not connected")
        val out = shell?.outputStream ?: throw IllegalStateException("Shell closed")
        out.write((command + "\n").toByteArray(Charsets.UTF_8))
        out.flush()
    }
    @Synchronized fun disconnect() {
        connected = false
        try { shell?.close() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        try { client?.disconnect() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
    }
}
