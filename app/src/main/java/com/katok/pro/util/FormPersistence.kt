package com.katok.pro.util

import android.content.Context
import com.google.gson.Gson
import com.katok.pro.model.Ad
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FormPersistence(context: Context) {

    private val dataStoreManager = DataStoreManager(context)
    private val gson = Gson()
    private val KEY_AD = "cached_ad"

    suspend fun save(ad: Ad?) {
        val json = if (ad == null) null else gson.toJson(ad)
        dataStoreManager.putString(KEY_AD, json)
    }

    suspend fun load(): Ad? {
        val json = dataStoreManager.getString(KEY_AD).first()
        return if (json != null) {
            try {
                gson.fromJson(json, Ad::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    suspend fun clear() {
        dataStoreManager.putString(KEY_AD, null)
    }
}