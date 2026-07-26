package com.katok.pro.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.katok.pro.R
import com.katok.pro.databinding.FragmentForgotPasswordBinding
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.AuthRepository
import com.katok.pro.util.ApiUtils
import com.katok.pro.util.PhoneUtils
import com.katok.pro.util.ToastHelper
import kotlinx.coroutines.launch

class ForgotPasswordFragment : BaseFragment(R.layout.fragment_forgot_password) {

    private var binding: FragmentForgotPasswordBinding? = null
    private lateinit var authRepository: AuthRepository
    private var secondsRemaining = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authRepository = AuthRepository()

        binding?.toolbar?.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding?.btnSendCode?.setOnClickListener { sendCode() }
        binding?.btnResetPassword?.setOnClickListener { resetPassword() }

        setupTimer()
    }

    private fun setupTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (secondsRemaining > 0) {
                    secondsRemaining--
                    binding?.tvTimer?.text = "Повторно через $secondsRemaining сек"
                    binding?.tvTimer?.visibility = View.VISIBLE
                    binding?.btnSendCode?.isEnabled = false
                    timerHandler.postDelayed(this, 1000)
                } else {
                    binding?.tvTimer?.visibility = View.GONE
                    binding?.btnSendCode?.isEnabled = true
                }
            }
        }
    }

    private fun sendCode() {
        val rawPhone = binding?.etPhone?.text?.toString()?.trim() ?: ""
        val formattedPhone = PhoneUtils.formatPhoneNumber(rawPhone)

        if (!PhoneUtils.isValidPhoneNumber(formattedPhone)) {
            binding?.etPhone?.error = "Неверный формат телефона"
            return
        }

        binding?.btnSendCode?.isEnabled = false
        binding?.progressBar?.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = authRepository.sendPasswordResetCode(formattedPhone)
            binding?.progressBar?.visibility = View.GONE

            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showInfo(requireContext(), "Код подтверждения отправлен на номер $formattedPhone")
                    startTimer()
                }
                is NetworkResult.Error -> {
                    binding?.btnSendCode?.isEnabled = true
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun resetPassword() {
        val rawPhone = binding?.etPhone?.text?.toString()?.trim() ?: ""
        val formattedPhone = PhoneUtils.formatPhoneNumber(rawPhone)
        val code = binding?.etCode?.text?.toString()?.trim() ?: ""
        val newPassword = binding?.etNewPassword?.text?.toString()?.trim() ?: ""
        val confirmPassword = binding?.etConfirmPassword?.text?.toString()?.trim() ?: ""

        if (!PhoneUtils.isValidPhoneNumber(formattedPhone)) {
            binding?.etPhone?.error = "Неверный формат телефона"
            return
        }
        if (code.isEmpty()) {
            binding?.etCode?.error = "Введите код"
            return
        }
        if (newPassword.length < 4) {
            binding?.etNewPassword?.error = "Пароль минимум 4 символа"
            return
        }
        if (newPassword != confirmPassword) {
            binding?.etConfirmPassword?.error = "Пароли не совпадают"
            return
        }

        binding?.progressBar?.visibility = View.VISIBLE
        binding?.btnResetPassword?.isEnabled = false

        lifecycleScope.launch {
            val result = authRepository.resetPassword(formattedPhone, code, newPassword)
            binding?.progressBar?.visibility = View.GONE
            binding?.btnResetPassword?.isEnabled = true
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Пароль изменён")
                    Navigation.findNavController(requireActivity(), R.id.nav_host_fragment).navigateUp()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        binding = null
    }
    private fun startTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        secondsRemaining = 60
        binding?.btnSendCode?.isEnabled = false
        binding?.btnSendCode?.text = "Повторно через $secondsRemaining сек"
        binding?.tvTimer?.visibility = View.VISIBLE
        timerHandler.post(timerRunnable!!)
    }

}