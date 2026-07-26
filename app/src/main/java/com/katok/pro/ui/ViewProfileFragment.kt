package com.katok.pro.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.Glide
import com.katok.pro.R
import com.katok.pro.databinding.FragmentViewProfileBinding
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.Profile
import com.katok.pro.model.User
import com.katok.pro.network.ApiClient
import com.katok.pro.repository.UserRepository
import com.katok.pro.util.PhoneUtils
import com.katok.pro.util.ToastHelper
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class ViewProfileFragment : BaseFragment(R.layout.fragment_view_profile) {

    private var _binding: FragmentViewProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var userRepository: UserRepository
    private var userId: String? = null
    private var canShowPhone: Boolean = false
    private var phoneFromAd: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userRepository = UserRepository(requireContext())

        arguments?.let {
            userId = it.getString("userId")
            canShowPhone = it.getBoolean("canShowPhone", false)
            phoneFromAd = it.getString("phone")
            Log.d("ViewProfileFragment", "canShowPhone=$canShowPhone, phoneFromAd=$phoneFromAd")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { NavHostFragment.findNavController(this).navigateUp() }

        binding.tvPhoneValue.setOnClickListener { makePhoneCall() }

        loadProfile()
    }

    private fun loadProfile() {
        if (userId == null) {
            ToastHelper.showError(requireContext(), "ID пользователя не указан")
            return
        }

        // Если номер передан и есть право, показываем его сразу
        if (canShowPhone && !phoneFromAd.isNullOrEmpty()) {
            displayPhone(phoneFromAd!!)
        } else if (canShowPhone) {
            // Если номер не передан, но право есть, запрашиваем пользователя
            fetchUserPhone()
        }

        // Загружаем публичный профиль для остальных данных
        lifecycleScope.launch {
            val result = userRepository.getPublicProfile(userId!!)
            when (result) {
                is NetworkResult.Success -> {
                    result.data?.let { updateUI(it) }
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun fetchUserPhone() {
        lifecycleScope.launch {
            val result = userRepository.getUserById(userId!!)
            when (result) {
                is NetworkResult.Success -> {
                    result.data?.phone?.let { phone ->
                        if (phone.isNotEmpty()) {
                            displayPhone(phone)
                        }
                    }
                }
                is NetworkResult.Error -> {
                    Log.e("ViewProfileFragment", "Failed to fetch user phone: ${result.message}")
                }
                else -> {}
            }
        }
    }

    private fun displayPhone(phoneNumber: String) {
        val formatted = PhoneUtils.formatPhoneNumberForDisplay(phoneNumber)
        binding.tvPhoneValue.text = formatted
        binding.layoutPhone.visibility = View.VISIBLE
        phoneFromAd = phoneNumber // сохраняем для звонка
    }

    private fun updateUI(profile: Profile) {
        // Имя и фамилия
        val firstName = profile.firstName ?: ""
        val lastName = profile.lastName ?: ""
        val fullName = "$firstName $lastName".trim()
        binding.tvName.text = if (fullName.isEmpty()) "Пользователь" else fullName

        // Амплуа
        val position = profile.position
        if (!position.isNullOrEmpty()) {
            binding.tvPosition.text = position
            binding.tvPosition.visibility = View.VISIBLE
        } else {
            binding.tvPosition.visibility = View.GONE
        }

        // Уровень
        binding.tvLevelValue.text = profile.level ?: "—"

        // Номер игрока
        val number = profile.number
        if (number != null && number > 0) {
            binding.tvNumberValue.text = number.toString()
        } else {
            binding.tvNumberValue.text = "—"
        }

        // Команда
        binding.tvTeamValue.text = profile.team ?: "—"

        // Email
        val email = profile.email
        if (!email.isNullOrEmpty()) {
            binding.tvEmailValue.text = email
            binding.layoutEmail.visibility = View.VISIBLE
        } else {
            binding.layoutEmail.visibility = View.GONE
        }

        // Возраст
        val birthDate = profile.birthDate
        if (!birthDate.isNullOrEmpty()) {
            val age = calculateAge(birthDate)
            binding.tvBirthDateValue.text = "$age лет"
        } else {
            binding.tvBirthDateValue.text = "не указан"
        }

        // Город
        val homeCity = profile.homeCity
        if (homeCity != null) {
            binding.tvCityValue.text = homeCity.name
        } else {
            binding.tvCityValue.text = "не указан"
        }

        // Аватар
        val avatarUrl = profile.avatarUrl
        if (!avatarUrl.isNullOrEmpty()) {
            val normalizedUrl = ApiClient.normalizeResourceUrl(avatarUrl)
            Glide.with(this)
                .load(normalizedUrl)
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(binding.ivAvatar)
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
        }
    }

    private fun calculateAge(birthDateStr: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val birthDate = sdf.parse(birthDateStr) ?: return 0
            val dob = Calendar.getInstance().apply { time = birthDate }
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            age
        } catch (e: ParseException) {
            0
        }
    }

    private fun makePhoneCall() {
        if (phoneFromAd.isNullOrEmpty()) {
            ToastHelper.showError(requireContext(), "Номер телефона не указан")
            return
        }
        val formattedPhone = PhoneUtils.formatPhoneNumberForDisplay(phoneFromAd!!)
        AlertDialog.Builder(requireContext())
            .setTitle("Звонок")
            .setMessage("Позвонить по номеру $formattedPhone?")
            .setPositiveButton("Позвонить") { _, _ ->
                val cleanPhone = phoneFromAd!!.replace("[^\\d+]".toRegex(), "")
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
                startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}