package com.katok.pro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.katok.pro.R
import com.katok.pro.model.NetworkResult
import com.katok.pro.util.ToastHelper

abstract class BaseFragment(@LayoutRes private val layoutResId: Int) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(layoutResId, container, false)
    }

    protected fun handleError(result: NetworkResult<*>): Boolean {
        if (result is NetworkResult.Error) {
            if (result.code == 401) {
                // Переход на логин
                findNavController().navigate(R.id.loginFragment)
                return true
            } else {
                ToastHelper.showError(requireContext(), result.message)
                return true
            }
        }
        return false
    }
}