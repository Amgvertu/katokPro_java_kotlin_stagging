package com.katok.pro.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class DataStoreManager(private val context: Context) {

    fun getString(key: String): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[stringPreferencesKey(key)]
        }
    }

    suspend fun putString(key: String, value: String?) {
        context.dataStore.edit { preferences ->
            val prefKey = stringPreferencesKey(key)
            if (value != null) {
                preferences[prefKey] = value
            } else {
                preferences.remove(prefKey)
            }
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun putBoolean(key: String, value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(key)] = value
        }
    }

    suspend fun getBoolean(key: String, default: Boolean = false): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[booleanPreferencesKey(key)] ?: default
        }.first()
    }
}