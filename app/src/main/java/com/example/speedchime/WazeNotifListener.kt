package com.example.speedchime

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService

/** So serve pra saber se o Waze tem uma notificacao ativa (navegacao em andamento). */
class WazeNotifListener : NotificationListenerService() {

    override fun onListenerConnected() {
        inst = this
    }

    override fun onListenerDisconnected() {
        if (inst === this) inst = null
    }

    companion object {
        @Volatile
        private var inst: WazeNotifListener? = null

        fun wazeNotifPresent(): Boolean {
            val i = inst ?: return false
            return try {
                i.activeNotifications?.any { it.packageName == "com.waze" } ?: false
            } catch (e: Exception) {
                false
            }
        }

        fun hasAccess(c: Context): Boolean {
            val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
                ?: return false
            return flat.contains(c.packageName)
        }
    }
}
