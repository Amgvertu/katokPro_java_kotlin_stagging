package com.katok.pro.util

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.navigation.Navigation
import com.katok.pro.R
import com.katok.pro.model.Profile
import com.katok.pro.repository.UserRepository
import kotlinx.coroutines.runBlocking

object ProfileHelper {

    /**
     * Проверяет, заполнены ли обязательные поля профиля (Имя и Амплуа).
     * Возвращает Profile, если всё заполнено, иначе null.
     * При необходимости показывает диалог с предложением перейти в профиль.
     */
    suspend fun getValidProfileOrShowDialog(
        context: Context,
        userRepository: UserRepository,
        navController: androidx.navigation.NavController? = null
    ): Profile? {
        // Пытаемся получить профиль из кэша
        var profile = userRepository.getCachedProfile()
        // Если кэш пуст – загружаем с сервера
        if (profile == null) {
            val result = userRepository.getMyProfile()
            if (result is com.katok.pro.model.NetworkResult.Success) {
                profile = result.data
                userRepository.cacheProfile(profile)
            } else {
                // Не удалось загрузить – считаем, что профиль не заполнен
                return null
            }
        }

        // Проверяем обязательные поля
        val firstName = profile?.firstName
        val position = profile?.position
        if (firstName.isNullOrEmpty() || position.isNullOrEmpty()) {
            // Показываем диалог
            showProfileIncompleteDialog(context, navController)
            return null
        }
        return profile
    }

    private fun showProfileIncompleteDialog(context: Context, navController: androidx.navigation.NavController?) {
        AlertDialog.Builder(context)
            .setTitle("Заполните профиль")
            .setMessage("Для создания объявления / отклика заполните, пожалуйста, Имя и Амплуа в профиле.")
            .setPositiveButton("Перейти в профиль") { _, _ ->
                navController?.navigate(R.id.navigation_profile)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}