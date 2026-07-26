package com.katok.pro.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.Glide
import com.katok.pro.R
import com.katok.pro.databinding.FragmentAdminUserDetailBinding
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.Profile
import com.katok.pro.model.User
import com.katok.pro.network.ApiClient
import com.katok.pro.repository.AdminRepository
import com.katok.pro.repository.LocationRepository
import com.katok.pro.util.Constants
import com.katok.pro.util.PhoneUtils
import com.katok.pro.util.ToastHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AdminUserDetailFragment : BaseFragment(R.layout.fragment_admin_user_detail ) {

    private var _binding: FragmentAdminUserDetailBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var adminRepository: AdminRepository
    @Inject lateinit var locationRepository: LocationRepository

    private var userId: String? = null
    private var currentUser: User? = null
    private var selectedCityId: Int? = null
    private val allCities = mutableListOf<City>()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString("userId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUserDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        setupListeners()
        loadCities()
        loadUserData()
    }

    private fun setupSpinners() {
        // Роли
        val roles = arrayOf("USER", "MODERATOR", "ADMIN")
        val roleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, roles)
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRole.adapter = roleAdapter

        // Амплуа (используем AutoCompleteTextView)
        val positionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            Constants.POSITIONS
        )
        binding.etPosition.setAdapter(positionAdapter)
        binding.etPosition.setOnClickListener {
            binding.etPosition.showDropDown()
        }
        binding.etPosition.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etPosition.showDropDown()
        }

        // Уровень (используем AutoCompleteTextView)
        val levelAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            Constants.LEVELS
        )
        binding.etLevel.setAdapter(levelAdapter)
        binding.etLevel.setOnClickListener {
            binding.etLevel.showDropDown()
        }
        binding.etLevel.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etLevel.showDropDown()
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            NavHostFragment.findNavController(this).navigateUp()
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        binding.btnBlock.setOnClickListener {
            currentUser?.let { user ->
                val newStatus = if (user.status == "ACTIVE") "BLOCKED" else "ACTIVE"
                val action = if (newStatus == "BLOCKED") "заблокировать" else "разблокировать"
                AlertDialog.Builder(requireContext())
                    .setTitle("Подтверждение")
                    .setMessage("Вы уверены, что хотите $action пользователя?")
                    .setPositiveButton("Да") { _, _ ->
                        changeUserStatus(user.id ?: "", newStatus)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }

        binding.btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Удаление пользователя")
                .setMessage("Вы уверены, что хотите удалить этого пользователя? Это действие необратимо.")
                .setPositiveButton("Удалить") { _, _ ->
                    deleteUser()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        // Выбор города
        binding.etCity.setOnClickListener {
            showCitySelector()
        }

        // Выбор даты рождения
        binding.etBirthDate.setOnClickListener {
            showDatePicker()
        }
        binding.etBirthDate.setFocusable(false)
        binding.etBirthDate.setClickable(true)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        var year = calendar.get(Calendar.YEAR)
        var month = calendar.get(Calendar.MONTH)
        var day = calendar.get(Calendar.DAY_OF_MONTH)

        // Если есть текущая дата, используем её
        val currentText = binding.etBirthDate.text.toString()
        if (currentText.isNotEmpty()) {
            try {
                val date = dateFormat.parse(currentText)
                date?.let {
                    val cal = Calendar.getInstance().apply { time = it }
                    year = cal.get(Calendar.YEAR)
                    month = cal.get(Calendar.MONTH)
                    day = cal.get(Calendar.DAY_OF_MONTH)
                }
            } catch (_: ParseException) {
                // Если не удалось распарсить, используем текущую дату
            }
        }

        DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format("%02d.%02d.%04d", selectedDay, selectedMonth + 1, selectedYear)
                binding.etBirthDate.setText(formattedDate)
            },
            year, month, day
        ).show()
    }

    private fun loadCities() {
        lifecycleScope.launch {
            val result = locationRepository.getAllCitiesByCountry(1)
            if (result is NetworkResult.Success) {
                allCities.clear()
                allCities.addAll(result.data)
            }
        }
    }

    private fun loadUserData() {
        if (userId == null) {
            ToastHelper.showError(requireContext(), "ID пользователя не указан")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = adminRepository.getUserById(userId!!)
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    currentUser = result.data
                    displayUser(currentUser!!)
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun displayUser(user: User) {
        binding.tvPhone.text = PhoneUtils.formatPhoneNumberForDisplay(user.phone)

        binding.etFirstName.setText(user.profile?.firstName ?: "")
        binding.etLastName.setText(user.profile?.lastName ?: "")
        binding.etEmail.setText(user.profile?.email ?: "")
        binding.etTeam.setText(user.profile?.team ?: "")

        // Устанавливаем амплуа (с учётом регистра)
        val position = user.profile?.position
        if (!position.isNullOrEmpty()) {
            val positionCapitalized = position.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            val positionIndex = Constants.POSITIONS.indexOf(positionCapitalized)
            if (positionIndex >= 0) {
                binding.etPosition.setText(Constants.POSITIONS[positionIndex], false)
            } else {
                binding.etPosition.setText(positionCapitalized, false)
            }
        } else {
            binding.etPosition.setText("", false)
        }

// Устанавливаем уровень
        val level = user.profile?.level
        if (!level.isNullOrEmpty()) {
            val levelIndex = Constants.LEVELS.indexOf(level.uppercase(Locale.getDefault()))
            if (levelIndex >= 0) {
                binding.etLevel.setText(Constants.LEVELS[levelIndex], false)
            } else {
                binding.etLevel.setText(level, false)
            }
        } else {
            binding.etLevel.setText("", false)
        }

// Номер
        binding.etNumber.setText(user.profile?.number?.toString() ?: "")

// Дата рождения
        val birthDate = user.profile?.birthDate
        if (!birthDate.isNullOrEmpty()) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = sdf.parse(birthDate)
                date?.let {
                    binding.etBirthDate.setText(dateFormat.format(it))
                } ?: run {
                    binding.etBirthDate.setText(birthDate)
                }
            } catch (_: ParseException) {
                binding.etBirthDate.setText(birthDate)
            }
        } else {
            binding.etBirthDate.setText("")
        }

// Город
        user.profile?.homeCity?.let {
            binding.etCity.setText(it.name)
            selectedCityId = it.id
        } ?: run {
            binding.etCity.setText("")
            selectedCityId = null
        }

        // Аватар
        val avatarUrl = user.profile?.avatarUrl
        if (!avatarUrl.isNullOrEmpty()) {
            val normalizedUrl = ApiClient.normalizeResourceUrl(avatarUrl)
            Glide.with(this)
                .load(normalizedUrl)
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(binding.ivAvatar)
        }

        // Роль
        val rolePos = when (user.role) {
            "USER" -> 0
            "MODERATOR" -> 1
            "ADMIN" -> 2
            else -> 0
        }
        binding.spinnerRole.setSelection(rolePos)

        binding.btnBlock.text = if (user.status == "ACTIVE") "Заблокировать" else "Разблокировать"
        binding.btnSaveProfile.isEnabled = true
    }

    private fun saveProfile() {
        val profile = Profile().apply {
            firstName = binding.etFirstName.text.toString().trim().takeIf { it.isNotEmpty() }
            lastName = binding.etLastName.text.toString().trim().takeIf { it.isNotEmpty() }
            email = binding.etEmail.text.toString().trim().takeIf { it.isNotEmpty() }
            team = binding.etTeam.text.toString().trim().takeIf { it.isNotEmpty() }

            // Амплуа – сохраняем в нижнем регистре для сервера
            val position = binding.etPosition.text.toString().trim()
            this.position = if (position.isEmpty()) null else position.lowercase(Locale.getDefault())

            // Уровень – сохраняем в верхнем регистре
            val level = binding.etLevel.text.toString().trim()
            this.level = if (level.isEmpty()) null else level.uppercase(Locale.getDefault())

            val numberStr = binding.etNumber.text.toString().trim()
            number = numberStr.toIntOrNull()

            // Дата рождения
            val birthDateStr = binding.etBirthDate.text.toString().trim()
            if (birthDateStr.isNotEmpty()) {
                try {
                    val date = dateFormat.parse(birthDateStr)
                    birthDate = date?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) }
                } catch (_: ParseException) {
                    birthDate = birthDateStr
                }
            } else {
                birthDate = null
            }

            if (selectedCityId != null && selectedCityId!! > 0) {
                homeCityId = selectedCityId
            }
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSaveProfile.isEnabled = false

        lifecycleScope.launch {
            val result = adminRepository.updateUserProfile(userId!!, profile)
            binding.progressBar.visibility = View.GONE
            binding.btnSaveProfile.isEnabled = true
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Профиль обновлён")
                    val selectedRole = binding.spinnerRole.selectedItem.toString()
                    if (selectedRole != currentUser?.role) {
                        lifecycleScope.launch {
                            val roleResult = adminRepository.changeUserRole(userId!!, selectedRole)
                            if (roleResult is NetworkResult.Error) {
                                ToastHelper.showError(requireContext(), "Ошибка смены роли: ${roleResult.message}")
                            } else {
                                ToastHelper.showSuccess(requireContext(), "Роль изменена")
                            }
                            loadUserData()
                        }
                    } else {
                        loadUserData()
                    }
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun changeUserStatus(userId: String, newStatus: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = adminRepository.changeUserStatus(userId, newStatus)
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Статус изменён")
                    loadUserData()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun deleteUser() {
        if (userId == null) return
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = adminRepository.deleteUser(userId!!)
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Пользователь удалён (заблокирован)")
                    NavHostFragment.findNavController(this@AdminUserDetailFragment).navigateUp()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun showCitySelector() {
        if (allCities.isEmpty()) {
            ToastHelper.showInfo(requireContext(), "Загрузка списка городов...")
            loadCities()
            return
        }

        CitySelectorDialog(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            object : CitySelectorDialog.OnCitySelectedListener {
                override fun onCitySelected(city: City) {
                    selectedCityId = city.id
                    binding.etCity.setText(city.name)
                }
            },
            locationRepository
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}