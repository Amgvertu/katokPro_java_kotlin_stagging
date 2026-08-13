package com.katok.pro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import ru.rustore.sdk.pushclient.RuStorePushClient
import com.katok.pro.network.ApiClient
import com.katok.pro.util.FormPersistence
import com.katok.pro.util.GlobalErrorHandler
import com.katok.pro.util.TokenManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class KatokApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Инициализация RuStore Push SDK
        try {
            RuStorePushClient.init(
                application = this,
                projectId = "jl4uTL62lrP4Kx3GlhMfSMi7y6f6eY0d",

            )
            Log.d("KatokApp", "✅ RuStore SDK initialized")
        } catch (e: Exception) {
            Log.e("KatokApp", "Failed to init RuStore SDK", e)
        }
        // Инициализация глобального обработчика ошибок
        GlobalErrorHandler.init(this)

        Log.d("KatokApp", "Application onCreate START")
        TokenManager.getInstance(this)

        ApiClient.init(this)
        CoroutineScope(Dispatchers.IO).launch {
            FormPersistence(this@KatokApplication).clear()
        }
        Log.d("KatokApp", "FormPersistence cleared")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "verification_channel",
                "Коды подтверждения",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Уведомления с кодами для подтверждения операций"

            val notificationChannel = NotificationChannel(
                "notification_channel",
                "Уведомления",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationChannel.description = "Уведомления о событиях в приложении"

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.let {
                it.createNotificationChannel(channel)
                it.createNotificationChannel(notificationChannel)
            }
        }

        // Удаляем старые SharedPreferences файлы (миграция на DataStore)
        try {
            getSharedPreferences("form_cache", Context.MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("profile_cache", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (e: Exception) {
            android.util.Log.e("KatokApp", "Ошибка при очистке старых SharedPreferences", e)
        }
    }
}