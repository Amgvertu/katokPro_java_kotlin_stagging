package com.katok.pro.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.katok.pro.R
import com.katok.pro.databinding.FragmentProfileSettingsBinding
import com.katok.pro.model.CodeResponse
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.UserRepository
import com.katok.pro.util.ApiUtils
import com.katok.pro.util.SessionManager
import com.katok.pro.util.ToastHelper
import com.katok.pro.util.TokenManager
import kotlinx.coroutines.launch

class ProfileSettingsFragment : BaseFragment(R.layout.fragment_profile_settings) {

    private var _binding: FragmentProfileSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var userRepository: UserRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var tokenManager: TokenManager
    private lateinit var viewModel: ProfileSettingsViewModel

    private var pendingNewPhone: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        tokenManager = TokenManager.getInstance(requireContext())
        userRepository = UserRepository(requireContext())

        viewModel = ViewModelProvider(requireActivity())[ProfileSettingsViewModel::class.java]
        observeViewModel()
        setupTextWatchers()

        binding.btnChangePassword.setOnClickListener { changePassword() }
        binding.btnChangePhone.setOnClickListener { changePhone() }
    }

    private fun observeViewModel() {
        viewModel.getOldPassword().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etOldPassword.text.toString() != value) {
                binding.etOldPassword.setText(value)
            }
        }
        viewModel.getNewPassword().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etNewPassword.text.toString() != value) {
                binding.etNewPassword.setText(value)
            }
        }
        viewModel.getConfirmPassword().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etConfirmPassword.text.toString() != value) {
                binding.etConfirmPassword.setText(value)
            }
        }
        viewModel.getNewPhone().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etNewPhone.text.toString() != value) {
                binding.etNewPhone.setText(value)
            }
        }
    }

    private fun setupTextWatchers() {
        binding.etOldPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setOldPassword(s.toString()) }
        })
        binding.etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setNewPassword(s.toString()) }
        })
        binding.etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setConfirmPassword(s.toString()) }
        })
        binding.etNewPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setNewPhone(s.toString()) }
        })
    }

    private fun changePassword() {
        val oldPassword = binding.etOldPassword.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (oldPassword.isEmpty()) {
            binding.etOldPassword.error = "Введите старый пароль"
            return
        }
        if (newPassword.isEmpty()) {
            binding.etNewPassword.error = "Введите новый пароль"
            return
        }
        if (newPassword.length < 4) {
            binding.etNewPassword.error = "Пароль должен быть минимум 4 символа"
            return
        }
        if (newPassword != confirmPassword) {
            binding.etConfirmPassword.error = "Пароли не совпадают"
            return
        }

        binding.passwordProgressBar.visibility = View.VISIBLE
        binding.btnChangePassword.isEnabled = false

        lifecycleScope.launch {
            val result = userRepository.changePassword(oldPassword, newPassword)
            binding.passwordProgressBar.visibility = View.GONE
            binding.btnChangePassword.isEnabled = true
            when (result) {
                is NetworkResult.Success -> {
                    Toast.makeText(requireContext(), "Пароль изменен", Toast.LENGTH_SHORT).show()
                    binding.etOldPassword.setText("")
                    binding.etNewPassword.setText("")
                    binding.etConfirmPassword.setText("")
                    viewModel.setOldPassword("")
                    viewModel.setNewPassword("")
                    viewModel.setConfirmPassword("")
                }
                is NetworkResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun changePhone() {
        val newPhone = binding.etNewPhone.text.toString().trim()
        if (newPhone.isEmpty()) {
            binding.etNewPhone.error = "Введите новый телефон"
            return
        }
        if (!newPhone.matches("^\\+7\\d{10}$".toRegex())) {
            binding.etNewPhone.error = "Формат: +7XXXXXXXXXX"
            return
        }
        pendingNewPhone = newPhone
        binding.phoneProgressBar.visibility = View.VISIBLE
        binding.btnChangePhone.isEnabled = false

        lifecycleScope.launch {
            val result = userRepository.sendPhoneChangeCode(pendingNewPhone!!)
            binding.phoneProgressBar.visibility = View.GONE
            binding.btnChangePhone.isEnabled = true
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showInfo(requireContext(), "Код подтверждения отправлен на номер $pendingNewPhone")
                    showPhoneVerificationDialog(null)
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun showPhoneVerificationDialog(prefilledCode: String?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_verification, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val etCode = dialogView.findViewById<android.widget.EditText>(R.id.etCode)
        val btnSendCode = dialogView.findViewById<android.widget.Button>(R.id.btnSendCode)
        val btnVerify = dialogView.findViewById<android.widget.Button>(R.id.btnVerify)
        val progressBarCode = dialogView.findViewById<android.widget.ProgressBar>(R.id.progressBarCode)

        if (!prefilledCode.isNullOrEmpty()) {
            etCode.setText(prefilledCode)
        }

        dialog.show()

        btnSendCode.setOnClickListener {
            progressBarCode.visibility = View.VISIBLE
            btnSendCode.isEnabled = false
            lifecycleScope.launch {
                val result = userRepository.sendPhoneChangeCode(pendingNewPhone!!)
                progressBarCode.visibility = View.GONE
                btnSendCode.isEnabled = true
                when (result) {
                    is NetworkResult.Success -> {
                        ToastHelper.showInfo(requireContext(), "Код подтверждения отправлен повторно на номер $pendingNewPhone")
                        // Не заполняем поле кода
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }

        btnVerify.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (TextUtils.isEmpty(code)) {
                Toast.makeText(requireContext(), "Введите код", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            progressBarCode.visibility = View.VISIBLE
            btnVerify.isEnabled = false
            lifecycleScope.launch {
                val result = userRepository.changePhoneWithVerification(pendingNewPhone!!, code)
                progressBarCode.visibility = View.GONE
                btnVerify.isEnabled = true
                when (result) {
                    is NetworkResult.Success -> {
                        Toast.makeText(requireContext(), "Телефон изменён", Toast.LENGTH_SHORT).show()
                        binding.etNewPhone.setText("")
                        viewModel.setNewPhone("")
                        sessionManager.updateUserPhone(pendingNewPhone!!)
                        dialog.dismiss()
                    }
                    is NetworkResult.Error -> {
                        handleError(result)
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}