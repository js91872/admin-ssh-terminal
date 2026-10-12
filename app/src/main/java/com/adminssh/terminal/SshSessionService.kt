package com.adminssh.terminal

import android.app.*
import android.content.*
import android.os.*
import java.util.concurrent.CopyOnWriteArraySet

/** Owns SSH independently of the Activity; a foreground notification keeps it alive. */
class SshSessionService : Service() {
    inner class LocalBinder : Binder() { fun service(): SshSessionService = this@SshSessionService }
    private val binder = LocalBinder()
    private val listeners = CopyOnWriteArraySet<Listener>()
    interface Listener {
        fun onText(value: String)
        fun onState(active: Boolean, message: String)
        fun approveKey(fingerprint: String): Boolean
    }
    private val main = Handler(Looper.getMainLooper())
    private var ssh: InteractiveSsh? = null
    private var generation = 0
    @Volatile var active = false
        private set
    @Volatile var connecting = false
        private set
    var serverLabel: String = ""
        private set
    private val transcript = StringBuilder()
    private val transcriptLock = Any()

    override fun onBind(intent: Intent?): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    fun subscribe(listener: Listener) {
        listeners.add(listener)
        val existing = synchronized(transcriptLock) { transcript.toString() }
        if (existing.isNotEmpty()) listener.onText(existing)
        listener.onState(active, if (connecting) "Connecting" else if (active) "Connected" else "Disconnected")
    }
    fun unsubscribe(listener: Listener) { listeners.remove(listener) }

    private fun publish(value: String) {
        synchronized(transcriptLock) {
            transcript.append(value)
            if (transcript.length > 200000) transcript.delete(0, transcript.length - 150000)
        }
        main.post { listeners.forEach { it.onText(value) } }
    }

    private fun notifyState(message: String) {
        main.post { listeners.forEach { it.onState(active, message) } }
    }

    private fun notification(message: String): Notification {
        val channelId = "ssh_active_session"
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(channelId, "SSH connection",
                    NotificationManager.IMPORTANCE_LOW))
        }
        val pending = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Admin SSH Terminal")
            .setContentText(message)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    fun connect(host: String, port: Int, user: String, password: String,
                trustedFingerprint: String?) {
        disconnect(false)
        val id = ++generation
        serverLabel = "$user@$host:$port"
        synchronized(transcriptLock) { transcript.setLength(0) }
        val connection = InteractiveSsh()
        ssh = connection
        connecting = true
        active = false
        startForeground(1001, notification("Connecting to $serverLabel"))
        publish("Connecting to $serverLabel...\n")
        notifyState("Connecting")
        Thread {
            connection.connect(host, port, user, password, trustedFingerprint,
                { fingerprint ->
                    // Host verification requires an active UI. Never auto-accept keys.
                    val gate = java.util.concurrent.CountDownLatch(1)
                    var accepted = false
                    main.post {
                        val listener = listeners.firstOrNull()
                        if (listener != null) {
                            Thread {
                                accepted = listener.approveKey(fingerprint)
                                gate.countDown()
                            }.start()
                        } else gate.countDown()
                    }
                    gate.await(90, java.util.concurrent.TimeUnit.SECONDS) && accepted
                },
                { output ->
                    if (id != generation) return@connect
                    if (!active) {
                        active = true
                        connecting = false
                        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                            .notify(1001, notification("Connected to $serverLabel"))
                        notifyState("Connected")
                    }
                    publish(output.replace(Regex("\\u001B\\\\[[0-?]*[ -/]*[@-~]"), "").replace("\r", ""))
                },
                { reason ->
                    if (id != generation) return@connect
                    active = false
                    connecting = false
                    publish("\n$reason\n")
                    notifyState(reason)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                })
        }.start()
    }

    fun send(command: String) {
        val connection = ssh ?: return
        Thread {
            try { connection.send(command) }
            catch (e: Exception) { publish("\nSend failed: ${e.message}\n") }
        }.start()
    }

    fun disconnect(stop: Boolean = true) {
        ++generation
        ssh?.disconnect()
        ssh = null
        active = false
        connecting = false
        if (stop) {
            notifyState("Disconnected")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    override fun onDestroy() {
        disconnect(false)
        super.onDestroy()
    }
}
