package com.katok.pro.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.method.ScrollingMovementMethod
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.katok.pro.R
import com.katok.pro.databinding.FragmentRegisterBinding
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.AuthRepository
import com.katok.pro.repository.LocationRepository
import com.katok.pro.repository.UserRepository
import com.katok.pro.util.PhoneUtils
import com.katok.pro.util.SessionManager
import com.katok.pro.util.ToastHelper
import com.katok.pro.util.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RegisterFragment : BaseFragment(R.layout.fragment_register) {

    @Inject lateinit var userRepository: UserRepository
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var authRepository: AuthRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var tokenManager: TokenManager
    private lateinit var viewModel: RegisterViewModel

    @Inject lateinit var locationRepository: LocationRepository
    private var pendingPhone: String? = null
    private var pendingPassword: String? = null
    private var selectedCityId = 0
    private var selectedCityName = ""
    private var selectedRegionId = 0
    private var selectedCountryId = 1

    // Таймер для повторной отправки кода
    private var secondsRemaining = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    // Флаг, что код уже отправлен
    private var codeSent = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        tokenManager = TokenManager.getInstance(requireContext())
        authRepository = AuthRepository()

        viewModel = ViewModelProvider(requireActivity())[RegisterViewModel::class.java]
        observeViewModel()
        setupTextWatchers()

        binding.etCity.setOnClickListener { selectCity() }

        // Настройка кнопок
        binding.btnSendCode.isEnabled = false
        binding.btnSendCode.setOnClickListener { sendCode() }

        binding.btnRegister.isEnabled = false
        binding.btnRegister.setOnClickListener { confirmRegistration() }
        binding.btnRegister.visibility = View.GONE

        // Чекбокс управляет активностью обеих кнопок
        binding.cbAgreement.setOnCheckedChangeListener { _, _ ->
            updateButtonsState()
        }

        binding.tvLoginLink.setOnClickListener { openLoginFragment() }

        // Настройка ссылки на пользовательское соглашение
        val agreementText = SpannableString("Ознакомиться с пользовательским соглашением")
        agreementText.setSpan(UnderlineSpan(), 0, agreementText.length, 0)
        binding.tvAgreementLink.text = agreementText
        binding.tvAgreementLink.movementMethod = LinkMovementMethod.getInstance()
        binding.tvAgreementLink.setOnClickListener {
            showAgreementDialog()
        }

        loadSavedCity()
        updateButtonsState()
    }

    private fun observeViewModel() {
        viewModel.getPhone().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etPhone.text.toString() != value) {
                binding.etPhone.setText(value)
            }
        }
        viewModel.getPassword().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etPassword.text.toString() != value) {
                binding.etPassword.setText(value)
            }
        }
        viewModel.getConfirmPassword().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etConfirmPassword.text.toString() != value) {
                binding.etConfirmPassword.setText(value)
            }
        }
        viewModel.getCityName().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etCity.text.toString() != value) {
                binding.etCity.setText(value)
            }
        }
    }

    private fun setupTextWatchers() {
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateButtonsState()
            }
        }
        binding.etPhone.addTextChangedListener(watcher)
        binding.etPassword.addTextChangedListener(watcher)
        binding.etConfirmPassword.addTextChangedListener(watcher)

        // Отслеживаем ввод кода для активации кнопки регистрации
        binding.etCode.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateButtonsState()
            }
        })
    }

    private fun loadSavedCity() {
        lifecycleScope.launch {
            val savedCityId = sessionManager.getSelectedCityId()
            val savedCityName = sessionManager.getSelectedCityName()

            if (savedCityId > 0 && !savedCityName.isNullOrEmpty()) {
                selectedCityId = savedCityId
                selectedCityName = savedCityName
                binding.etCity.setText(savedCityName)
                viewModel.setCityId(selectedCityId)
                viewModel.setCityName(selectedCityName)
                updateButtonsState()
            } else if (sessionManager.isLoggedIn()) {
                val homeCityId = sessionManager.getHomeCityId()
                val homeCityName = sessionManager.getHomeCityName()
                if (!homeCityId.isNullOrEmpty() && homeCityName != null) {
                    selectedCityId = homeCityId.toInt()
                    selectedCityName = homeCityName
                    binding.etCity.setText(homeCityName)
                    viewModel.setCityId(selectedCityId)
                    viewModel.setCityName(selectedCityName)
                    updateButtonsState()
                }
            }
        }
    }

    private fun selectCity() {
        val dialog = CitySelectorDialog(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            object : CitySelectorDialog.OnCitySelectedListener {
                override fun onCitySelected(city: City) {
                    selectedCityId = city.id
                    selectedCityName = city.name ?: ""
                    binding.etCity.setText(city.name)
                    viewModel.setCityId(selectedCityId)
                    viewModel.setCityName(selectedCityName)
                    lifecycleScope.launch {
                        sessionManager.saveSelectedCity(selectedCityId, selectedCityName)
                    }
                    updateButtonsState()
                    city.region?.let {
                        selectedRegionId = it.id ?: 0
                        viewModel.setRegionId(selectedRegionId)
                        selectedCountryId = 1
                        viewModel.setCountryId(1)
                    }
                }
            },
            locationRepository
        )
        dialog.show()
    }

    private fun sendCode() {
        val formattedPhone = PhoneUtils.formatPhoneNumber(_binding?.etPhone?.text?.toString()?.trim() ?: "")
        val password = _binding?.etPassword?.text?.toString()?.trim() ?: ""
        val confirmPassword = _binding?.etConfirmPassword?.text?.toString()?.trim() ?: ""

        if (!PhoneUtils.isValidPhoneNumber(formattedPhone)) {
            _binding?.etPhone?.error = "Неверный формат телефона"
            return
        }
        if (password.length < 4) {
            _binding?.etPassword?.error = "Пароль минимум 4 символа"
            return
        }
        if (password != confirmPassword) {
            _binding?.etConfirmPassword?.error = "Пароли не совпадают"
            return
        }
        if (selectedCityId == 0) {
            ToastHelper.showError(requireContext(), "Выберите город")
            return
        }

        pendingPhone = formattedPhone
        pendingPassword = password

        _binding?.progressBar?.visibility = View.VISIBLE
        _binding?.btnSendCode?.isEnabled = false

        lifecycleScope.launch {
            val result = authRepository.sendRegistrationCode(pendingPhone!!)
            _binding?.progressBar?.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    codeSent = true
                    ToastHelper.showInfo(requireContext(), "Код подтверждения отправлен на номер $pendingPhone")
                    _binding?.etCode?.visibility = View.VISIBLE
                    _binding?.btnRegister?.visibility = View.VISIBLE
                    _binding?.btnRegister?.isEnabled = true
                    _binding?.etCode?.requestFocus()
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(_binding?.etCode, InputMethodManager.SHOW_IMPLICIT)
                    startTimer()
                    updateButtonsState()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                    _binding?.btnSendCode?.isEnabled = true
                }
                else -> {}
            }
        }
    }

    private fun startTimer() {
        secondsRemaining = 60
        _binding?.btnSendCode?.isEnabled = false
        _binding?.btnSendCode?.text = "Повторно через $secondsRemaining сек"

        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = object : Runnable {
            override fun run() {
                secondsRemaining--
                if (secondsRemaining > 0) {
                    _binding?.btnSendCode?.text = "Повторно через $secondsRemaining сек"
                    timerHandler.postDelayed(this, 1000)
                } else {
                    _binding?.btnSendCode?.text = "Отправить код повторно"
                    updateButtonsState()
                }
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun confirmRegistration() {
        val code = binding.etCode.text.toString().trim()
        if (code.isEmpty()) {
            ToastHelper.showError(requireContext(), "Введите код")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnRegister.isEnabled = false
        binding.btnSendCode.isEnabled = false

        lifecycleScope.launch {
            val result = authRepository.registerWithVerification(
                pendingPhone!!, pendingPassword!!, code,
                selectedCountryId, selectedRegionId, selectedCityId
            )
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Регистрация успешна!")
                    val loginResponse = result.data
                    tokenManager.saveTokens(loginResponse.accessToken, loginResponse.refreshToken)
                    sessionManager.saveUser(loginResponse.user)
                    // Переход на профиль
                    NavHostFragment.findNavController(this@RegisterFragment)
                        .navigate(R.id.navigation_profile)

// Показываем тост
                    Toast.makeText(requireContext(), "Для правильного функционирования приложения – заполните профиль", Toast.LENGTH_LONG).show()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                    binding.btnRegister.isEnabled = true
                    binding.btnSendCode.isEnabled = true
                }
                else -> {}
            }
        }
    }

    private fun openLoginFragment() {
        NavHostFragment.findNavController(this).navigate(R.id.loginFragment)
    }

    private fun updateButtonsState() {
        val isChecked = _binding?.cbAgreement?.isChecked ?: false
        val isFormValid = isFormValid()

        val canSendCode = if (!codeSent) {
            isChecked && isFormValid
        } else {
            isChecked && secondsRemaining == 0
        }
        _binding?.btnSendCode?.isEnabled = canSendCode

        val registerEnabled = codeSent && isChecked && !_binding?.etCode?.text.toString().trim().isNullOrEmpty()
        _binding?.btnRegister?.isEnabled = registerEnabled

        if (codeSent && secondsRemaining == 0) {
            _binding?.btnSendCode?.text = "Отправить код повторно"
        }
    }

    private fun isFormValid(): Boolean {
        val formattedPhone = PhoneUtils.formatPhoneNumber(_binding?.etPhone?.text?.toString()?.trim() ?: "")
        val password = _binding?.etPassword?.text?.toString()?.trim() ?: ""
        val confirm = _binding?.etConfirmPassword?.text?.toString()?.trim() ?: ""
        return PhoneUtils.isValidPhoneNumber(formattedPhone) &&
                password.length >= 4 &&
                password == confirm &&
                selectedCityId != 0
    }

    private fun showAgreementDialog() {
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Загрузка")
            .setMessage("Загрузка пользовательского соглашения...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            val result = userRepository.getTermsOfService()
            progressDialog.dismiss()
            when (result) {
                is NetworkResult.Success -> {
                    val agreement = result.data
                    val content = agreement?.content ?: "Содержание не найдено"
                    showAgreementContentDialog(content)
                }
                is NetworkResult.Error -> {
                    ToastHelper.showError(requireContext(), "Не удалось загрузить соглашение: ${result.message}")
                }
                else -> {}
            }
        }
    }

    private fun showAgreementContentDialog(content: String) {
        val scrollView = ScrollView(requireContext())
        val textView = TextView(requireContext()).apply {
            text = content
            setTextSize(14f)
            setPadding(40, 20, 40, 20)
            movementMethod = ScrollingMovementMethod()
        }
        scrollView.addView(textView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Пользовательское соглашение")
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .setNegativeButton("Закрыть", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        _binding = null
    }
}