package com.katok.pro.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
import com.katok.pro.BuildConfig

class TokenManager private constructor(context: Context) {

    companion object {
        private const val PREF_NAME = "KatokPref_encrypted"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_LAST_REFRESH_TIME = "last_refresh_time"

        @Volatile
        private var instance: TokenManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): TokenManager {
            if (instance == null) {
                instance = TokenManager(context.applicationContext)
            }
            return instance!!
        }

        @JvmStatic
        @Synchronized
        fun getInstance(): TokenManager {
            if (instance == null) {
                throw IllegalStateException("TokenManager not initialized. Call getInstance(Context) first.")
            }
            return instance!!
        }
    }

    // ИСПРАВЛЕНО: val → var
    private var sharedPreferences: SharedPreferences

    init {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            if (BuildConfig.LOG_ENABLED) {
                Log.d("TokenManager", "Encrypted preferences initialized")
            }
        } catch (e: Exception) {
            if (BuildConfig.LOG_ENABLED) {
                Log.e("TokenManager", "Failed to create encrypted prefs", e)
            }
            throw RuntimeException("Security: Cannot create encrypted storage", e)
        }
        migrateOldTokens(context)
    }

    private fun migrateOldTokens(context: Context) {
        val oldPrefs = context.getSharedPreferences("KatokPref", Context.MODE_PRIVATE)
        if (!sharedPreferences.contains(KEY_ACCESS_TOKEN) && oldPrefs.contains(KEY_ACCESS_TOKEN)) {
            val oldAccess = oldPrefs.getString(KEY_ACCESS_TOKEN, null)
            val oldRefresh = oldPrefs.getString(KEY_REFRESH_TOKEN, null)
            val oldTime = oldPrefs.getLong(KEY_LAST_REFRESH_TIME, 0)
            if (oldAccess != null) {
                saveTokens(oldAccess, oldRefresh)
                setLastTokenRefreshTime(oldTime)
                oldPrefs.edit().clear().apply()
                if (BuildConfig.LOG_ENABLED) {
                    Log.d("TokenManager", "Migrated old tokens to encrypted storage")
                }
            }
        }
    }

    fun saveTokens(accessToken: String?, refreshToken: String?) {
        if (BuildConfig.LOG_ENABLED) {
            Log.d(
                "TokenManager",
                "Saving tokens. Access: ${accessToken != null}, Refresh: ${refreshToken != null}"
            )
        }
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_LAST_REFRESH_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getAccessToken(): String? = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    fun hasToken(): Boolean = !getAccessToken().isNullOrEmpty()

    fun setLastTokenRefreshTime(time: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_REFRESH_TIME, time).apply()
    }

    fun getLastTokenRefreshTime(): Long = sharedPreferences.getLong(KEY_LAST_REFRESH_TIME, 0)
}