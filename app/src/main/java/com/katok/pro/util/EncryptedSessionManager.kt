package com.katok.pro.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedSessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "encrypted_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun saveString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    suspend fun saveInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    suspend fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    suspend fun getString(key: String): String? = prefs.getString(key, null)

    suspend fun getInt(key: String, default: Int = 0): Int = prefs.getInt(key, default)

    suspend fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)

    suspend fun clear() {
        prefs.edit().clear().apply()
    }
}