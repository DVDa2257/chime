package com.example.speedchime

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * Servico em primeiro plano. Liga o GPS so quando o Waze esta ativo (ou sempre, se
 * "seguir o Waze" estiver desligado) e toca o chime sem parar enquanto a velocidade
 * do GPS for maior ou igual ao limite.
 */
class SpeedService : Service(), LocationListener {

    companion object {
        private const val CHANNEL = "speedchime"
        private const val NOTIF_ID = 1

        @Volatile var running = false
        @Volatile var wazeActive = false
        @Volatile var speedNow = 0
        @Volatile var chiming = false

        fun start(c: Context) {
            c.startForegroundService(Intent(c, SpeedService::class.java))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, SpeedService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var lm: LocationManager
    private lateinit var nm: NotificationManager
    private lateinit var player: ChimePlayer

    private var gpsOn = false
    private var lastFixAt = 0L
    private var over = false
    private var lastNotifText = ""

    private val chimeLoop = object : Runnable {
        override fun run() {
            if (!over) return
            val cfg = Prefs.read(this@SpeedService)
            player.play(cfg)
            handler.postDelayed(this, cfg.intervalMs.toLong())
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val cfg = Prefs.read(this@SpeedService)
            if (!cfg.enabled) {
                stopSelf()
                return
            }
            val active = if (cfg.followWaze) WazeWatch.isActive(this@SpeedService) else true
            wazeActive = active

            if (active && !gpsOn) startGps()
            if (!active && gpsOn) {
                stopGps()
                setOver(false)
            }

            // sem sinal de GPS ha mais de 5 s: para o chime
            if (gpsOn && SystemClock.elapsedRealtime() - lastFixAt > 5000L) {
                speedNow = 0
                setOver(false)
            }

            val text = when {
                !active -> "Aguardando o Waze"
                !gpsOn -> "Sem GPS"
                else -> "GPS $speedNow km/h, limite ${cfg.limit} km/h"
            }
            if (text != lastNotifText) {
                lastNotifText = text
                nm.notify(NOTIF_ID, buildNotif(text))
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        player = ChimePlayer(this)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Speed Chime", NotificationManager.IMPORTANCE_LOW)
        )
        try {
            val n = buildNotif("Iniciando")
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return
        }
        running = true
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopGps()
        over = false
        chiming = false
        running = false
        wazeActive = false
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper()
            )
            gpsOn = true
            lastFixAt = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            gpsOn = false
        }
    }

    private fun stopGps() {
        try {
            lm.removeUpdates(this)
        } catch (e: Exception) {
        }
        gpsOn = false
        speedNow = 0
    }

    override fun onLocationChanged(location: Location) {
        lastFixAt = SystemClock.elapsedRealtime()
        val kmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0
        speedNow = Math.round(kmh).toInt()
        val cfg = Prefs.read(this)
        setOver(speedNow >= cfg.limit)
    }

    private fun setOver(v: Boolean) {
        if (v == over) return
        over = v
        chiming = v
        handler.removeCallbacks(chimeLoop)
        if (v) handler.post(chimeLoop)
    }

    private fun buildNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Speed Chime")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
