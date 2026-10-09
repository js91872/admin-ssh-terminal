package com.adminssh.terminal

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey
import android.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Executes a single command over SSH. Not yet a PTY/interactive terminal. */
class SshRunner {
    data class Result(val output: String, val exitCode: Int?)
    fun execute(
        host: String, port: Int, username: String, password: String, command: String,
        approvedFingerprint: String?,
        approveNewHost: (String) -> Boolean
    ): Result {
        SSHClient().use { ssh ->
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
                    val fingerprint = "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
                    return if (approvedFingerprint != null) {
                        approvedFingerprint == fingerprint
                    } else {
                        approveNewHost(fingerprint)
                    }
                }
            })
            ssh.connect(host, port)
            ssh.authPassword(username, password)
            ssh.startSession().use { session ->
                val process = session.exec(command)
                process.inputStream.use { stdout ->
                    process.errorStream.use { stderr ->
                        val out = StringBuilder()
                        val t1 = Thread { stdout.bufferedReader().forEachLine { synchronized(out) { out.appendLine(it) } } }
                        val t2 = Thread { stderr.bufferedReader().forEachLine { synchronized(out) { out.appendLine(it) } } }
                        t1.start(); t2.start()
                        process.join(120, TimeUnit.SECONDS)
                        if (process.isOpen) process.close()
                        t1.join(2000); t2.join(2000)
                        return Result(out.toString(), process.exitStatus)
                    }
                }
            }
        }
    }
}
