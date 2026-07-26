package com.katok.pro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.katok.pro.R
import com.katok.pro.databinding.FragmentFeedbackBinding
import com.katok.pro.util.SessionManager
import com.katok.pro.util.ToastHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FeedbackFragment : BaseFragment(R.layout.fragment_feedback) {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: FeedbackViewModel
    private lateinit var sessionManager: SessionManager

    private val subjects = arrayOf(
        "Выберите тему",
        "Проблемы с регистрацией",
        "Проблемы с авторизацией",
        "Отзывы",
        "Предложения"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        viewModel = androidx.lifecycle.ViewModelProvider(this)[FeedbackViewModel::class.java]

        setupSpinner()
        setupObservers()
        setupListeners()

        // Предзаполнение данными пользователя
        lifecycleScope.launch {
            val fullName = sessionManager.getUserName() ?: ""
            val phone = sessionManager.getUserPhone() ?: ""
            binding.etFullName.setText(fullName)
            binding.etPhone.setText(phone)
        }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, subjects)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSubject.adapter = adapter
        binding.spinnerSubject.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // nothing
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.isSending.collect { isSending ->
                binding.progressBar.visibility = if (isSending) View.VISIBLE else View.GONE
                binding.btnSend.isEnabled = !isSending
            }
        }
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                if (error != null) {
                    ToastHelper.showError(requireContext(), error)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.success.collect { success ->
                if (success) {
                    ToastHelper.showSuccess(requireContext(), "Сообщение отправлено!")
                    NavHostFragment.findNavController(this@FeedbackFragment).navigateUp()
                    viewModel.resetSuccess()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            sendFeedback()
        }
        binding.btnCancel.setOnClickListener {
            NavHostFragment.findNavController(this).navigateUp()
        }
    }

    private fun sendFeedback() {
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val subjectPosition = binding.spinnerSubject.selectedItemPosition
        val subject = if (subjectPosition > 0) subjects[subjectPosition] else null
        val message = binding.etMessage.text.toString().trim()

        if (subject == null) {
            ToastHelper.showError(requireContext(), "Выберите тему")
            return
        }
        if (message.isEmpty()) {
            binding.etMessage.error = "Введите сообщение"
            return
        }
        if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Введите корректный email"
            return
        }

        viewModel.sendFeedback(fullName, phone, email, subject, message)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}