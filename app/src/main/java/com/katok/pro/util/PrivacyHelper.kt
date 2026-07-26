package com.katok.pro.util

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.katok.pro.R
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PrivacyHelper {

    private const val PRIVACY_ACCEPTED_KEY = "privacy_accepted"

    suspend fun isPrivacyAccepted(context: Context): Boolean {
        return DataStoreManager(context).getBoolean(PRIVACY_ACCEPTED_KEY, false)
    }

    suspend fun setPrivacyAccepted(context: Context, accepted: Boolean) {
        DataStoreManager(context).putBoolean(PRIVACY_ACCEPTED_KEY, accepted)
    }

    /**
     * Показать диалог согласия, если ещё не принято.
     * Возвращает true, если согласие уже было или получено сейчас.
     */
    suspend fun showPrivacyDialogIfNeeded(
        context: Context,
        lifecycleScope: CoroutineScope,
        onAccepted: () -> Unit = {}
    ): Boolean {
        if (isPrivacyAccepted(context)) {
            return true
        }

        // Показываем диалог (блокирующий)
        val accepted = withContext(Dispatchers.Main) {
            showDialogAndWait(context, lifecycleScope)
        }
        if (accepted) {
            setPrivacyAccepted(context, true)
            onAccepted()
        }
        return accepted
    }

    private suspend fun showDialogAndWait(context: Context, lifecycleScope: CoroutineScope): Boolean {
        // Используем CompletableDeferred для ожидания ответа
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        lifecycleScope.launch {
            // Загружаем текст соглашения
            val userRepo = UserRepository(context)
            var content = "Пользовательское соглашение временно недоступно. Пожалуйста, примите условия для продолжения."
            val result = userRepo.getTermsOfService()
            if (result is NetworkResult.Success) {
                content = result.data?.content ?: content
            }

            val dialog = AlertDialog.Builder(context)
                .setTitle("Пользовательское соглашение")
                .setMessage(content)
                .setPositiveButton("Принимаю") { _, _ ->
                    deferred.complete(true)
                }
                .setCancelable(false)
                .create()

            // Обрабатываем ошибку загрузки – даём кнопку "Повторить"
            if (result is NetworkResult.Error) {
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Повторить") { _, _ ->
                    // Закрываем диалог и повторяем вызов
                    dialog.dismiss()
                    // Повторно вызываем этот же метод (зацикливаем, пока не загрузится)
                    lifecycleScope.launch {
                        val retryResult = showDialogAndWait(context, lifecycleScope)
                        deferred.complete(retryResult)
                    }
                }
            }
            dialog.show()
        }
        return deferred.await()
    }
}