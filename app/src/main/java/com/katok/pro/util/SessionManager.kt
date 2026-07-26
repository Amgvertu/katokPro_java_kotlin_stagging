package com.katok.pro.util

import android.content.Context
import com.katok.pro.model.User

class SessionManager(context: Context) {

    private val encryptedPrefs = EncryptedSessionManager(context)


    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_HOME_CITY_ID = "home_city_id"
        private const val KEY_HOME_CITY_NAME = "home_city_name"
        private const val KEY_SELECTED_CITY_ID = "selected_city_id"
        private const val KEY_SELECTED_CITY_NAME = "selected_city_name"

    }

    // Вспомогательные приватные методы
    private suspend fun saveString(key: String, value: String?) {
        encryptedPrefs.saveString(key, value)
    }

    private suspend fun saveInt(key: String, value: Int) {
        encryptedPrefs.saveInt(key, value)
    }

    private suspend fun saveBoolean(key: String, value: Boolean) {
        encryptedPrefs.saveBoolean(key, value)
    }

    private suspend fun getString(key: String): String? {
        return encryptedPrefs.getString(key)
    }

    private suspend fun getInt(key: String): Int {
        return encryptedPrefs.getInt(key)
    }

    private suspend fun getBoolean(key: String): Boolean {
        return encryptedPrefs.getBoolean(key)
    }

    // ========== Публичные методы ==========
    suspend fun createLoginSession(userId: String, phone: String, name: String, role: String) {
        saveString(KEY_USER_ID, userId)
        saveString(KEY_USER_PHONE, phone)
        saveString(KEY_USER_NAME, name)
        saveString(KEY_USER_ROLE, role)
        saveBoolean(KEY_IS_LOGGED_IN, true)
    }

    suspend fun saveUser(user: User?) {
        if (user == null) return
        saveString(KEY_USER_ID, user.id)
        saveString(KEY_USER_PHONE, user.phone)
        val profile = user.profile
        if (profile != null) {
            val firstName = profile.firstName ?: ""
            val lastName = profile.lastName ?: ""
            val fullName = "$firstName $lastName".trim()
            saveString(KEY_USER_NAME, if (fullName.isEmpty()) null else fullName)
            val homeCity = profile.homeCity
            if (homeCity != null) {
                saveInt(KEY_HOME_CITY_ID, homeCity.id)
                saveString(KEY_HOME_CITY_NAME, homeCity.name)
            }
        } else {
            saveString(KEY_USER_NAME, null)
        }
        saveString(KEY_USER_ROLE, user.role)
        saveBoolean(KEY_IS_LOGGED_IN, true)
    }

    suspend fun saveHomeCity(cityId: Int, cityName: String) {
        saveInt(KEY_HOME_CITY_ID, cityId)
        saveString(KEY_HOME_CITY_NAME, cityName)
    }

    suspend fun getHomeCityId(): String? = getInt(KEY_HOME_CITY_ID).takeIf { it > 0 }?.toString()
    suspend fun getHomeCityName(): String? = getString(KEY_HOME_CITY_NAME)

    suspend fun saveSelectedCity(cityId: Int, cityName: String) {
        saveInt(KEY_SELECTED_CITY_ID, cityId)
        saveString(KEY_SELECTED_CITY_NAME, cityName)
    }

    suspend fun getSelectedCityId(): Int = getInt(KEY_SELECTED_CITY_ID)
    suspend fun getSelectedCityName(): String? = getString(KEY_SELECTED_CITY_NAME)

    suspend fun getUserId(): String? = getString(KEY_USER_ID)
    suspend fun getUserPhone(): String? = getString(KEY_USER_PHONE)
    suspend fun getUserName(): String? = getString(KEY_USER_NAME)
    suspend fun getUserRole(): String? = getString(KEY_USER_ROLE)

    suspend fun isAdvertiser(): Boolean {
        val role = getUserRole()
        return role == "ADVERT"
    }

    suspend fun isLoggedIn(): Boolean = getBoolean(KEY_IS_LOGGED_IN)

    suspend fun isAdmin(): Boolean {
        val role = getUserRole()
        return role != null && (role == "ADMIN" || role == "MODERATOR")
    }

    suspend fun clear() = encryptedPrefs.clear()
    suspend fun logout() {
        clear()
    }

    suspend fun updateUserName(name: String) = saveString(KEY_USER_NAME, name)
    suspend fun updateUserRole(role: String) = saveString(KEY_USER_ROLE, role)
    suspend fun updateUserPhone(phone: String) = saveString(KEY_USER_PHONE, phone)
}