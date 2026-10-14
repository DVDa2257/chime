package com.example.speedchime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Decide se o Waze esta "ativo": na frente, com notificacao ligada,
 * ou que saiu da frente ha menos de GRACE_MS.
 */
object WazeWatch {
    private const val WAZE = "com.waze"
    private const val GRACE_MS = 60_000L

    private var lastQueryEnd = 0L
    private var fg = false
    private var lastFgAt = 0L

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Synchronized
    fun isActive(c: Context): Boolean {
        val now = System.currentTimeMillis()
        updateForeground(c, now)
        val notif = WazeNotifListener.wazeNotifPresent()
        if (fg || notif) lastFgAt = now
        return fg || notif || (now - lastFgAt < GRACE_MS)
    }

    @Suppress("DEPRECATION")
    private fun updateForeground(c: Context, now: Long) {
        if (!hasUsageAccess(c)) return
        try {
            val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val begin = if (lastQueryEnd == 0L) now - 3_600_000L else lastQueryEnd
            val events = usm.queryEvents(begin, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.packageName != WAZE) continue
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    fg = true
                    lastFgAt = e.timeStamp
                } else if (e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    fg = false
                    lastFgAt = e.timeStamp
                }
            }
            lastQueryEnd = now - 2000L
        } catch (ex: Exception) {
            // sem acesso ou erro: mantem o ultimo estado
        }
    }
}
