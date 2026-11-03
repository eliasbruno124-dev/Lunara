package de.lunara.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lunara.app.core.locale.AppLanguage
import de.lunara.app.MainActivity
import de.lunara.app.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LunaraNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Android 12 and older: resolve texts in the language chosen since process start.
    private fun text(id: Int): String = AppLanguage.wrap(context).getString(id)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CYCLE_CHANNEL,
                    text(R.string.notification_channel_cycle),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = text(R.string.notification_channel_cycle_description)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    SUPPORT_CHANNEL,
                    text(R.string.notification_channel_support),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = text(R.string.notification_channel_support_description)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
            ),
        )
    }

    @SuppressLint("MissingPermission")
    fun publish(candidate: NotificationCandidate, hideSensitiveContent: Boolean): Boolean {
        if (!notificationsAllowed()) return false
        createChannels()
        val (title, body) = localizedContent(candidate.content)
        val generic = NotificationCompat.Builder(context, channel(candidate.type))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text(R.string.notification_generic_title))
            .setContentText(text(R.string.notification_generic_body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val builder = NotificationCompat.Builder(context, channel(candidate.type))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setVisibility(
                if (hideSensitiveContent) {
                    NotificationCompat.VISIBILITY_PRIVATE
                } else {
                    NotificationCompat.VISIBILITY_PUBLIC
                },
            )
        if (hideSensitiveContent) builder.setPublicVersion(generic)
        return try {
            NotificationManagerCompat.from(context).notify(notificationId(candidate.type), builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    fun notificationsAllowed(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun localizedContent(content: NotificationContent): Pair<String, String> = when (content) {
        NotificationContent.PRIVATE_REMINDER ->
            R.string.notification_generic_title to R.string.notification_generic_body
        NotificationContent.ESTIMATED_PERIOD_WINDOW_STARTS_TOMORROW ->
            R.string.notification_period_title to R.string.notification_period_body
        NotificationContent.ROUGH_ESTIMATED_FERTILE_WINDOW_STARTS_TOMORROW ->
            R.string.notification_fertile_title to R.string.notification_fertile_body
        NotificationContent.DAILY_LOG_REMINDER ->
            R.string.notification_log_title to R.string.notification_log_body
        NotificationContent.PARTNER_TIP_AVAILABLE ->
            R.string.notification_tip_title to R.string.notification_tip_body
    }.let { (title, body) -> text(title) to text(body) }

    private fun channel(type: NotificationType): String = when (type) {
        NotificationType.PARTNER_TIP -> SUPPORT_CHANNEL
        else -> CYCLE_CHANNEL
    }

    private fun notificationId(type: NotificationType): Int = when (type) {
        NotificationType.PERIOD_WINDOW_STARTS_TOMORROW -> 1_001
        NotificationType.FERTILE_WINDOW_STARTS_TOMORROW -> 1_002
        NotificationType.DAILY_LOG_REMINDER -> 1_003
        NotificationType.PARTNER_TIP -> 2_001
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CYCLE_CHANNEL = "lunara_cycle_reminders_v1"
        const val SUPPORT_CHANNEL = "lunara_support_reminders_v1"
    }
}
