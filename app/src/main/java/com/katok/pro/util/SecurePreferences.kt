package com.katok.pro.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

class SecurePreferences private constructor(context: Context) {

    companion object {
        private const val FCM_PREF_NAME = "fcm_encrypted"
        @Volatile
        private var instance: SecurePreferences? = null

        @JvmStatic
        fun getInstance(context: Context): SecurePreferences {
            if (instance == null) {
                synchronized(SecurePreferences::class.java) {
                    if (instance == null) {
                        instance = SecurePreferences(context.applicationContext)
                    }
                }
            }
            return instance!!
        }
    }

    private val prefs: SharedPreferences

    init {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                context,
                FCM_PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            throw RuntimeException("Failed to init encrypted prefs", e)
        } catch (e: IOException) {
            throw RuntimeException("Failed to init encrypted prefs", e)
        }
    }

    fun saveFcmToken(token: String) {
        prefs.edit().putString("fcm_token", token).apply()
    }

    fun getFcmToken(): String? {
        return prefs.getString("fcm_token", null)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
