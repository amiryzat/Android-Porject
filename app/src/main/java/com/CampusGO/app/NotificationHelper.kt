package com.CampusGO.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase

object NotificationHelper {
    private const val CHANNEL_ID = "campusgo_notifications"
    private const val CHANNEL_NAME = "CampusGO Alerts"
    private const val CHANNEL_DESC = "Real-time notifications for tasks and chats"
    private const val TAG = "NotificationHelper"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel registered successfully")
        }
    }

    fun showPopupNotification(
        context: Context,
        title: String,
        body: String,
        type: String,
        taskId: String = "",
        chatId: String = ""
    ) {
        val prefs = context.getSharedPreferences("CampusGO_Prefs", Context.MODE_PRIVATE)
        val notifsEnabled = prefs.getBoolean("notifications_enabled", true)
        if (!notifsEnabled) {
            Log.d(TAG, "Notifications are disabled by user, ignoring local alert.")
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = when (type) {
            "CHAT" -> {
                Intent(context, ChatActivity::class.java).apply {
                    putExtra("taskId", taskId)
                    putExtra("chatId", chatId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            "TASK_STATUS" -> {
                Intent(context, TaskTrackingActivity::class.java).apply {
                    putExtra("taskId", taskId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            else -> {
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)

        val notificationId = System.currentTimeMillis().toInt()
        manager.notify(notificationId, builder.build())
        Log.d(TAG, "Displaying local notification $notificationId: $title - $body")
    }

    fun sendNotificationToUser(
        receiverUserId: String,
        title: String,
        body: String,
        type: String,
        taskId: String = "",
        chatId: String = ""
    ) {
        val db = FirebaseDatabase.getInstance().reference
        val notifRef = db.child("notifications").push()
        val notifId = notifRef.key ?: return

        val notification = mapOf(
            "id" to notifId,
            "receiverUserId" to receiverUserId,
            "title" to title,
            "body" to body,
            "type" to type,
            "timestamp" to System.currentTimeMillis(),
            "taskId" to taskId,
            "chatId" to chatId
        )

        notifRef.setValue(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Notification row created successfully: $notifId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create notification row", e)
            }
    }
}
