package com.katok.pro.util

import android.content.Context
import android.widget.Toast

object ToastHelper {

    @JvmStatic
    fun showSuccess(context: Context, message: String) {
        Toast.makeText(context, "✅ $message", Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun showError(context: Context, message: String) {
        Toast.makeText(context, "❌ $message", Toast.LENGTH_LONG).show()
    }

    @JvmStatic
    fun showInfo(context: Context, message: String) {
        Toast.makeText(context, "ℹ️ $message", Toast.LENGTH_SHORT).show()
    }
}
