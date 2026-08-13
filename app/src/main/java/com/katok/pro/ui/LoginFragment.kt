package com.katok.pro.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.messaging.FirebaseMessaging
import com.katok.pro.KatokApplication
import com.katok.pro.MainActivity
import com.katok.pro.R
import com.katok.pro.databinding.FragmentLoginBinding
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.AuthRepository
import com.katok.pro.repository.UserRepository
import com.katok.pro.services.WebSocketForegroundService
import com.katok.pro.util.PhoneUtils
import com.katok.pro.util.SessionManager
import com.katok.pro.util.TokenManager
import com.katok.pro.util.TokenRegistrationService
import com.katok.pro.workers.TokenRefreshScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : BaseFragment(R.layout.fragment_login) {

    private var binding: FragmentLoginBinding? = null
    private lateinit var viewModel: LoginViewModel
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var tokenManager: TokenManager
    @Inject lateinit var userRepository: UserRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = androidx.lifecycle.ViewModelProvider(requireActivity())[LoginViewModel::class.java]
        sessionManager = SessionManager(requireContext())
        tokenManager = TokenManager.getInstance(requireContext())
        authRepository = AuthRepository()

        binding?.btnLogin?.setOnClickListener { performLogin() }
        binding?.tvForgotPassword?.setOnClickListener { showForgotPasswordDialog() }
        binding?.tvRegisterLink?.setOnClickListener { openRegisterFragment() }

        viewModel.getPhone().observe(viewLifecycleOwner) { phone ->
            if (phone != null && binding?.etPhone?.text?.toString() != phone) {
                binding?.etPhone?.setText(phone)
            }
        }
        viewModel.getPassword().observe(viewLifecycleOwner) { password ->
            if (password != null && binding?.etPassword?.text?.toString() != password) {
                binding?.etPassword?.setText(password)
            }
        }

        binding?.etPhone?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setPhone(s.toString()) }
        })

        binding?.etPassword?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setPassword(s.toString()) }
        })
    }

    private fun performLogin() {
        val phone = PhoneUtils.formatPhoneNumber(binding?.etPhone?.text?.toString()?.trim() ?: "")
        val password = binding?.etPassword?.text?.toString()?.trim() ?: ""

        if (!PhoneUtils.isValidPhoneNumber(phone)) {
            binding?.etPhone?.error = "Неверный формат телефона"
            return
        }
        if (password.isEmpty()) {
            binding?.etPassword?.error = "Введите пароль"
            return
        }

        binding?.progressBar?.visibility = View.VISIBLE
        binding?.btnLogin?.isEnabled = false

        lifecycleScope.launch {
            val result = authRepository.login(phone, password)
            binding?.progressBar?.visibility = View.GONE
            binding?.btnLogin?.isEnabled = true

            when (result) {
                is NetworkResult.Success -> {
                    val loginResponse = result.data
                    val accessToken = loginResponse.accessToken
                    val refreshToken = loginResponse.refreshToken
                    val user = loginResponse.user

                    tokenManager.saveTokens(accessToken, refreshToken)
                    sessionManager.saveUser(user)

                    val tokenRegistrationService = TokenRegistrationService(requireContext())
                    tokenRegistrationService.registerAllTokens()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
                        }
                    }

                    TokenRefreshScheduler.scheduleNextRefresh(requireContext())

                    if (WebSocketForegroundService.getInstance() == null) {
                        WebSocketForegroundService.start(requireContext())
                    } else {
                        val intent = Intent(requireContext(), WebSocketForegroundService::class.java)
                        intent.putExtra("token", accessToken)
                        requireContext().startService(intent)
                    }

                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result != null) {
                            val fcmToken = task.result
                            lifecycleScope.launch {
                                userRepository.updateFcmToken(fcmToken)
                            }
                        }
                    }

                    Toast.makeText(context, "Вход выполнен успешно", Toast.LENGTH_SHORT).show()
                    NavHostFragment.findNavController(this@LoginFragment).navigate(R.id.navigation_main)

                    // Обновляем меню в MainActivity
                    (requireActivity() as? MainActivity)?.resetTokenCheck()
                    (requireActivity() as? MainActivity)?.refreshMenu()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun showForgotPasswordDialog() {
        NavHostFragment.findNavController(this).navigate(R.id.forgotPasswordFragment)
    }

    private fun openRegisterFragment() {
        NavHostFragment.findNavController(this).navigate(R.id.registerFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}