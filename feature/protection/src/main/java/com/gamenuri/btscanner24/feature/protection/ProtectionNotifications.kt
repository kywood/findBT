package com.gamenuri.btscanner24.feature.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import com.gamenuri.btscanner24.core.ble.ScanFailure
import java.util.Date

/**
 * Everything protection mode (#163) puts in the notification shade: the one that stays while it is
 * on, the left-behind alert and the reminder after a restart. Device names appear only here, on the
 * user's own phone (#19).
 */
internal class ProtectionNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.protection_channel_status),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.protection_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /** The notification that stays while protection is on. [blind]: why it cannot hear right now. */
    fun status(watched: Int, blind: ScanFailure?): Notification {
        val text = when {
            blind == ScanFailure.BLUETOOTH_OFF -> context.getString(R.string.protection_status_bluetooth_off)
            blind == ScanFailure.LOCATION_OFF -> context.getString(R.string.protection_status_location_off)
            blind != null -> context.getString(R.string.protection_status_cannot_scan)
            watched == 0 -> context.getString(R.string.protection_status_none)
            else -> context.resources.getQuantityString(R.plurals.protection_status_watching, watched, watched)
        }
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_protection)
            .setContentTitle(context.getString(R.string.protection_status_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open(REQUEST_STATUS, Protection.OPEN_SCREEN, address = null))
            .addAction(0, context.getString(R.string.protection_turn_off), button(REQUEST_STOP, ACTION_STOP, null))
            .build()
    }

    fun updateStatus(watched: Int, blind: ScanFailure?) {
        notify(STATUS_ID, status(watched, blind))
    }

    /** "You may have left Keys behind", one per device, replaced when it happens again. */
    fun leftBehind(address: String, name: String, lastHeardAt: Long) {
        val time = DateFormat.getTimeFormat(context).format(Date(lastHeardAt))
        val view = open(alertId(address), Protection.OPEN_LAST_SEEN, address)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_protection)
            .setContentTitle(context.getString(R.string.protection_alert_title, name))
            .setContentText(context.getString(R.string.protection_alert_text, time))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(context.getString(R.string.protection_alert_text, time)),
            )
            .setWhen(lastHeardAt)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(view)
            .addAction(0, context.getString(R.string.protection_alert_view), view)
            .addAction(
                0,
                context.getString(R.string.protection_mute_hour),
                button(alertId(address), ACTION_MUTE_HOUR, address),
            )
            .addAction(
                0,
                context.getString(R.string.protection_mute_today),
                button(alertId(address), ACTION_MUTE_TODAY, address),
            )
            .build()
        notify(alertId(address), notification)
    }

    fun cancelAlert(address: String) {
        manager?.cancel(alertId(address))
    }

    /** Protection is off although the user left it on: after a restart, or when Android would not let it run. */
    fun turnedOff(afterRestart: Boolean) {
        val turnOn = open(REQUEST_TURN_ON, Protection.OPEN_TURN_ON, address = null)
        val text = context.getString(
            if (afterRestart) R.string.protection_off_restart else R.string.protection_off_stopped,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_protection)
            .setContentTitle(context.getString(R.string.protection_off_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(turnOn)
            .addAction(0, context.getString(R.string.protection_turn_on_short), turnOn)
            .build()
        notify(TURNED_OFF_ID, notification)
    }

    fun cancelTurnedOff() {
        manager?.cancel(TURNED_OFF_ID)
    }

    private fun notify(id: Int, notification: Notification) {
        try {
            manager?.notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications were switched off for the app after protection was turned on.
        }
    }

    /** Opens the app; MainActivity reads the extras. Intents differ in extras only, so each needs its own [request]. */
    private fun open(request: Int, what: String, address: String?): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .addFlags(OPEN_FLAGS)
            .putExtra(Protection.EXTRA_OPEN, what)
            .putExtra(Protection.EXTRA_ADDRESS, address)
        return PendingIntent.getActivity(context, request, intent, FLAGS)
    }

    /** A button handled by the running service itself; nothing happens when it is gone. */
    private fun button(request: Int, action: String, address: String?): PendingIntent {
        val intent = Intent(action).setPackage(context.packageName).putExtra(Protection.EXTRA_ADDRESS, address)
        return PendingIntent.getBroadcast(context, request, intent, FLAGS)
    }

    private fun alertId(address: String): Int = ALERT_ID_BASE + (address.hashCode() and ALERT_ID_MASK)

    companion object {
        const val STATUS_ID = 1630
        const val ACTION_STOP = "com.gamenuri.btscanner24.protection.STOP"
        const val ACTION_MUTE_HOUR = "com.gamenuri.btscanner24.protection.MUTE_HOUR"
        const val ACTION_MUTE_TODAY = "com.gamenuri.btscanner24.protection.MUTE_TODAY"
        const val ACTION_TICK = "com.gamenuri.btscanner24.protection.TICK"

        private const val CHANNEL_STATUS = "protection_status"
        private const val CHANNEL_ALERTS = "protection_alerts"
        private const val TURNED_OFF_ID = 1631
        private const val ALERT_ID_BASE = 2_000_000
        private const val ALERT_ID_MASK = 0xFFFFF
        private const val REQUEST_STATUS = 1
        private const val REQUEST_STOP = 2
        private const val REQUEST_TURN_ON = 3
        private const val OPEN_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
