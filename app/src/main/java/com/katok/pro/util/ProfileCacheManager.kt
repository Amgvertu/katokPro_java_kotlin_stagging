package com.katok.pro.util

import android.content.Context
import com.google.gson.Gson
import com.katok.pro.model.Profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ProfileCacheManager(context: Context) {

    private val dataStoreManager = DataStoreManager(context)
    private val gson = Gson()
    private val KEY_PROFILE = "cached_profile"

    suspend fun saveProfile(profile: Profile?) {
        val json = if (profile == null) null else gson.toJson(profile)
        dataStoreManager.putString(KEY_PROFILE, json)
    }


    suspend fun getCachedProfile(): Profile? {
        val json = dataStoreManager.getString(KEY_PROFILE).first()
        return if (json != null) {
            try {
                gson.fromJson(json, Profile::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    suspend fun clear() {
        dataStoreManager.putString(KEY_PROFILE, null)
    }
}