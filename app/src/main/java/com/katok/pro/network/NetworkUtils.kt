package com.katok.pro.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.view.View
import com.google.android.material.snackbar.Snackbar

object NetworkUtils {

    @JvmStatic
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting
    }

    // Новый метод: показать Snackbar с кнопкой повтора
    @JvmStatic
    fun showNoInternetSnackbar(view: View, onRetry: Runnable) {
        val snackbar = Snackbar.make(view, "Нет подключения к интернету", Snackbar.LENGTH_INDEFINITE)
        snackbar.setAction("Повторить") { onRetry.run() }
        snackbar.show()
    }
}
