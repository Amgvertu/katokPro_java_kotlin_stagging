package com.katok.pro.util

import android.content.pm.PackageManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.katok.pro.MainActivity
import com.katok.pro.R
import com.katok.pro.model.ApiResponse

object ApiUtils {

    @JvmStatic
    fun extractCodeFromResponse(response: ApiResponse<*>?): String? {
        if (response != null && response.data is Map<*, *>) {
            val map = response.data as Map<*, *>
            val codeObj = map["code"]
            if (codeObj != null) return codeObj.toString()
        }
        return null
    }

    @JvmStatic
    fun showCodeNotification(context: Context, code: String?, title: String, message: String) {
        try {
            if (code == null || code.isEmpty()) {
                Log.d("ApiUtils", "No code provided, skipping notification")
                return
            }
            val finalMessage = "$message\nКод: $code"
            Log.d("ApiUtils", "showCodeNotification: code=$code, title=$title")

            Handler(Looper.getMainLooper()).post {
                try {
                    val intent = Intent(context, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    val pendingIntent = PendingIntent.getActivity(
                        context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val builder = NotificationCompat.Builder(context, "verification_channel")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(title)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(finalMessage))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)

                    val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }

                    if (hasPermission) {
                        val notificationManager = NotificationManagerCompat.from(context)
                        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
                        Log.d("ApiUtils", "Notification sent")
                    } else {
                        Log.w("ApiUtils", "No POST_NOTIFICATIONS permission, showing toast instead")
                        ToastHelper.showInfo(context, "Код подтверждения: $code")
                    }
                } catch (e: Exception) {
                    Log.e("ApiUtils", "Error showing notification", e)
                    ToastHelper.showInfo(context, "Код подтверждения: $code")
                }
            }
        } catch (e: Exception) {
            Log.e("ApiUtils", "Error in showCodeNotification", e)
        }
    }
}
