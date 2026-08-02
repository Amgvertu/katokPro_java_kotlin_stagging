package com.katok.pro.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.katok.pro.KatokApplication
import com.katok.pro.R
import com.katok.pro.databinding.FragmentProfileInfoBinding
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.Profile
import com.katok.pro.network.ApiClient
import com.katok.pro.repository.LocationRepository
import com.katok.pro.repository.UserRepository
import com.katok.pro.util.Constants
import com.katok.pro.util.ImageCompressor
import com.katok.pro.util.PhoneUtils
import com.katok.pro.util.SessionManager
import com.katok.pro.util.TokenManager
import com.katok.pro.util.ToastHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ProfileInfoFragment : BaseFragment(R.layout.fragment_profile_info) {

    private var currentUserId: String? = null
    private var _binding: FragmentProfileInfoBinding? = null
    private val binding get() = _binding!!

    private lateinit var userRepository: UserRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var tokenManager: TokenManager
    private lateinit var viewModel: ProfileInfoViewModel

    @Inject lateinit var locationRepository: LocationRepository

    private var selectedCityId = 0
    private var selectedCityName = ""
    private var selectedRegionId = 0
    private var selectedCountryId = 1
    private val cityList = mutableListOf<City>()

    private val positionOptions = Constants.POSITIONS.toTypedArray()
    private val levelOptions = Constants.LEVELS.toTypedArray()

    private val displayFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val serverFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var isProfileLoaded = false
    private var selectedAvatarFile: File? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                selectedAvatarFile = saveImageToTempFile(it)
                selectedAvatarFile?.let { file ->
                    Glide.with(this)
                        .load(file)
                        .circleCrop()
                        .into(binding.ivAvatar)
                    showUploadAvatarDialog()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ToastHelper.showError(requireContext(), "Ошибка при выборе изображения")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        tokenManager = TokenManager.getInstance(requireContext())
        userRepository = UserRepository(requireContext())

        binding.ivAvatar.setOnClickListener { showAvatarChangeOptions() }

        viewModel = ViewModelProvider(requireActivity())[ProfileInfoViewModel::class.java]
        observeViewModel()
        setupTextWatchers()
        setupDatePicker()
        setupPositionPicker()
        setupLevelPicker()
        setupCityPicker()

        binding.btnSave.setOnClickListener { saveProfile() }

        loadCities()

        if (viewModel.getFirstName().value == null && !isProfileLoaded) {
            loadProfile()
        } else {
            restoreFromViewModel()
        }

        lifecycleScope.launch {
            val phone = sessionManager.getUserPhone()
            if (!TextUtils.isEmpty(phone)) {
                binding.tvPhone.text = PhoneUtils.formatPhoneNumberForDisplay(phone!!)
                binding.tvPhone.visibility = View.VISIBLE
            } else {
                binding.tvPhone.visibility = View.GONE
            }
        }

        lifecycleScope.launch {
            currentUserId = sessionManager.getUserId()
            // Если профиль ещё не загружен – загружаем
            if (!isProfileLoaded) {
                loadProfile()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.getFirstName().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etFirstName.text.toString() != value)
                binding.etFirstName.setText(value)
        }
        viewModel.getLastName().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etLastName.text.toString() != value)
                binding.etLastName.setText(value)
        }
        viewModel.getBirthDate().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etBirthDate.text.toString() != value)
                binding.etBirthDate.setText(value)
        }
        viewModel.getPosition().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etPosition.text.toString() != value)
                binding.etPosition.setText(value)
        }
        viewModel.getLevel().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etLevel.text.toString() != value)
                binding.etLevel.setText(value)
        }
        viewModel.getNumber().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etNumber.text.toString() != value)
                binding.etNumber.setText(value)
        }
        viewModel.getTeam().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etTeam.text.toString() != value)
                binding.etTeam.setText(value)
        }
        viewModel.getEmail().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etEmail.text.toString() != value)
                binding.etEmail.setText(value)
        }
        viewModel.getCityName().observe(viewLifecycleOwner) { value ->
            if (value != null && binding.etCity.text.toString() != value)
                binding.etCity.setText(value)
        }
    }

    private fun setupTextWatchers() {
        binding.etFirstName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setFirstName(s.toString()) }
        })
        binding.etLastName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setLastName(s.toString()) }
        })
        binding.etBirthDate.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setBirthDate(s.toString()) }
        })
        binding.etPosition.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setPosition(s.toString()) }
        })
        binding.etLevel.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setLevel(s.toString()) }
        })
        binding.etNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setNumber(s.toString()) }
        })
        binding.etTeam.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setTeam(s.toString()) }
        })
        binding.etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setEmail(s.toString()) }
        })
    }

    private fun restoreFromViewModel() {
        viewModel.getFirstName().value?.let { binding.etFirstName.setText(it) }
        viewModel.getLastName().value?.let { binding.etLastName.setText(it) }
        viewModel.getBirthDate().value?.let { binding.etBirthDate.setText(it) }
        viewModel.getPosition().value?.let { binding.etPosition.setText(it) }
        viewModel.getLevel().value?.let { binding.etLevel.setText(it) }
        viewModel.getNumber().value?.let { binding.etNumber.setText(it) }
        viewModel.getTeam().value?.let { binding.etTeam.setText(it) }
        viewModel.getEmail().value?.let { binding.etEmail.setText(it) }
        viewModel.getCityName().value?.let {
            selectedCityName = it
            binding.etCity.setText(selectedCityName)
        }
        viewModel.getCityId().value?.let { selectedCityId = it }
        viewModel.getRegionId().value?.let { selectedRegionId = it }
        viewModel.getCountryId().value?.let { selectedCountryId = it }
    }

    private fun setupDatePicker() {
        binding.etBirthDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            var year = calendar.get(Calendar.YEAR)
            var month = calendar.get(Calendar.MONTH)
            var day = calendar.get(Calendar.DAY_OF_MONTH)
            val birthDateStr = binding.etBirthDate.text.toString()
            if (birthDateStr.isNotEmpty()) {
                try {
                    val date = displayFormat.parse(birthDateStr)
                    date?.let {
                        val cal = Calendar.getInstance().apply { time = it }
                        year = cal.get(Calendar.YEAR)
                        month = cal.get(Calendar.MONTH)
                        day = cal.get(Calendar.DAY_OF_MONTH)
                    }
                } catch (_: ParseException) {}
            }
            DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                val formatted = String.format("%02d.%02d.%04d", selectedDay, selectedMonth + 1, selectedYear)
                binding.etBirthDate.setText(formatted)
            }, year, month, day).show()
        }
    }

    private fun setupPositionPicker() {
        binding.etPosition.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Выберите амплуа")
                .setItems(positionOptions) { _, which -> binding.etPosition.setText(positionOptions[which]) }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun setupLevelPicker() {
        binding.etLevel.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Выберите уровень")
                .setItems(levelOptions) { _, which -> binding.etLevel.setText(levelOptions[which]) }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun setupCityPicker() {
        binding.etCity.setOnClickListener { showCitySelectorDialog() }
    }

    private fun loadCities() {
        lifecycleScope.launch {
            val result = locationRepository.getAllCitiesByCountry(1)
            if (result is NetworkResult.Success) {
                cityList.clear()
                cityList.addAll(result.data)
            }
        }
    }

    private fun showCitySelectorDialog() {
        if (cityList.isEmpty()) {
            Toast.makeText(context, "Загрузка списка городов...", Toast.LENGTH_SHORT).show()
            loadCities()
            return
        }
        val dialog = CitySelectorDialog(requireContext(), viewLifecycleOwner.lifecycleScope, object : CitySelectorDialog.OnCitySelectedListener {
            override fun onCitySelected(city: City) {
                selectedCityId = city.id
                selectedCityName = city.name ?: ""
                binding.etCity.setText(selectedCityName)
                viewModel.setCityId(selectedCityId)
                viewModel.setCityName(selectedCityName)
                city.region?.let {
                    selectedRegionId = it.id ?: 0
                    viewModel.setRegionId(selectedRegionId)
                }
                viewModel.setCountryId(selectedCountryId)
            }
        },
            locationRepository
            )
        dialog.show()
    }

    private fun loadProfile() {
        clearViewModelFields()
        lifecycleScope.launch {
            // Сначала показываем кэш, если есть
            val cachedProfile = userRepository.getCachedProfile()
            if (cachedProfile != null && isAdded && _binding != null) {
                updateViewModelFromProfile(cachedProfile)
                restoreFromViewModel()
                displayAvatar(cachedProfile)
                isProfileLoaded = true
            }

            if (!isAdded || _binding == null) return@launch
            binding.progressBar.visibility = View.VISIBLE
            val result = userRepository.getMyProfile()
            if (!isAdded || _binding == null) return@launch
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    val profile = result.data
                    userRepository.cacheProfile(profile)
                    updateViewModelFromProfile(profile)
                    restoreFromViewModel()
                    displayAvatar(profile)
                    isProfileLoaded = true
                    currentUserId = sessionManager.getUserId()
                }
                is NetworkResult.Error -> {
                    // Если есть кэш, не показываем ошибку, чтобы не беспокоить пользователя
                    if (cachedProfile == null) {
                        handleError(result)
                    } else {
                        // Если кэш есть, но сервер вернул ошибку, просто игнорируем её,
                        // потому что пользователь уже видит данные из кэша.
                        ToastHelper.showError(requireContext(), "Не удалось обновить данные с сервера")
                    }
                }

                else -> {}
            }
        }
    }

    private fun displayAvatar(profile: Profile) {
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

    private fun updateViewModelFromProfile(profile: Profile) {
        viewModel.setFirstName(profile.firstName ?: "")
        viewModel.setLastName(profile.lastName ?: "")

        val birthDateStr = profile.birthDate
        if (!birthDateStr.isNullOrEmpty()) {
            try {
                val date = serverFormat.parse(birthDateStr)
                date?.let { viewModel.setBirthDate(displayFormat.format(it)) }
            } catch (_: ParseException) {
                viewModel.setBirthDate(birthDateStr)
            }
        } else {
            viewModel.setBirthDate("")
        }

        val position = profile.position
        if (!position.isNullOrEmpty()) {
            viewModel.setPosition(position.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            })
        } else {
            viewModel.setPosition("")
        }

        viewModel.setLevel(profile.level ?: "")
        viewModel.setNumber(profile.number?.toString() ?: "")
        viewModel.setTeam(profile.team ?: "")
        viewModel.setEmail(profile.email ?: "")

        profile.homeCity?.let { city ->
            viewModel.setCityId(city.id)
            viewModel.setCityName(city.name ?: "")
            city.region?.let { viewModel.setRegionId(it.id ?: 0) }
        } ?: run {
            profile.homeCityId?.let { viewModel.setCityId(it) }
        }

        viewModel.setCountryId(1)
    }

    private fun saveProfile() {
        val email = binding.etEmail.text.toString().trim()
        if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(requireContext(), "Введите корректный email адрес", Toast.LENGTH_SHORT).show()
            return
        }

        val profile = Profile().apply {
            firstName = binding.etFirstName.text.toString().trim().takeIf { it.isNotEmpty() }
            lastName = binding.etLastName.text.toString().trim().takeIf { it.isNotEmpty() }

            val birthDateStr = binding.etBirthDate.text.toString().trim()
            if (birthDateStr.isNotEmpty()) {
                try {
                    val date = displayFormat.parse(birthDateStr)
                    birthDate = date?.let { serverFormat.format(it) }
                } catch (_: ParseException) {}
            }

            val position = binding.etPosition.text.toString().trim()
            this.position = if (position.isEmpty()) null else position.lowercase(Locale.getDefault())

            level = binding.etLevel.text.toString().trim().takeIf { it.isNotEmpty() }
            val numberStr = binding.etNumber.text.toString().trim()
            number = numberStr.toIntOrNull()
            team = binding.etTeam.text.toString().trim().takeIf { it.isNotEmpty() }
            this.email = email.takeIf { it.isNotEmpty() }

            if (selectedCityId > 0) {
                homeCityId = selectedCityId
                homeCountryId = selectedCountryId
                if (selectedRegionId > 0) homeRegionId = selectedRegionId
                lifecycleScope.launch {
                    sessionManager.saveHomeCity(selectedCityId, selectedCityName)
                }
            }
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            if (!isAdded || _binding == null) return@launch
            binding.progressBar.visibility = View.VISIBLE
            val result = userRepository.updateProfile(profile)
            if (!isAdded || _binding == null) return@launch
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    Toast.makeText(requireContext(), "Профиль сохранен", Toast.LENGTH_SHORT).show()
                    loadProfile()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun showAvatarChangeOptions() {
        AlertDialog.Builder(requireContext())
            .setTitle("Аватар")
            .setMessage("Выберите действие")
            .setPositiveButton("Выбрать новое фото") { _, _ -> selectNewAvatar() }
            .setNegativeButton("Удалить аватар") { _, _ -> confirmDeleteAvatar() }
            .setNeutralButton("Отмена", null)
            .show()
    }

    private fun selectNewAvatar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 1004)
                return
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1004)
                return
            }
        }
        imagePickerLauncher.launch("image/*")
    }

    private fun saveImageToTempFile(uri: Uri): File {
        // Используем компрессор, он сам создаёт временный файл
        val compressedFile = ImageCompressor.compressImage(
            contentResolver = requireContext().contentResolver,
            uri = uri,
            maxSizeBytes = 2 * 1024 * 1024, // 2 МБ
            maxWidth = 1024,
            maxHeight = 1024
        )

        // Если сжатие не удалось, сохраняем как есть
        if (compressedFile == null) {
            ToastHelper.showError(requireContext(), "Не удалось обработать изображение")
            val fallbackFile = File(requireContext().cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(fallbackFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return fallbackFile
        }

        return compressedFile
    }

    private fun showUploadAvatarDialog() {
        val file = selectedAvatarFile ?: return

        // Проверяем размер перед показом диалога
        val sizeMB = file.length() / (1024.0 * 1024.0)
        if (sizeMB > 10) {
            ToastHelper.showError(requireContext(), "Изображение слишком большое (${String.format("%.1f", sizeMB)} МБ). Пожалуйста, выберите другое.")
            selectedAvatarFile = null
            loadProfile()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Загрузить аватар")
            .setMessage("Установить выбранное изображение как аватар?")
            .setPositiveButton("Да") { _, _ -> uploadAvatar() }
            .setNegativeButton("Отмена") { _, _ ->
                selectedAvatarFile?.delete()
                loadProfile()
            }
            .show()
    }

    private fun uploadAvatar() {
        val file = selectedAvatarFile ?: return

        // Проверяем размер перед загрузкой (на всякий случай)
        if (file.length() > 10 * 1024 * 1024) {
            ToastHelper.showError(requireContext(), "Изображение слишком большое. Пожалуйста, выберите другое.")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = userRepository.uploadAvatar(file)
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Аватар обновлён")
                    val avatarUrl = result.data
                    if (!avatarUrl.isNullOrEmpty()) {
                        val normalizedUrl = ApiClient.normalizeResourceUrl(avatarUrl)
                        Glide.with(this@ProfileInfoFragment)
                            .load(normalizedUrl)
                            .placeholder(R.drawable.ic_default_avatar)
                            .error(R.drawable.ic_default_avatar)
                            .circleCrop()
                            .into(binding.ivAvatar)
                    }
                    loadProfile()
                    selectedAvatarFile?.delete()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun confirmDeleteAvatar() {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить аватар")
            .setMessage("Вы уверены, что хотите удалить аватар?")
            .setPositiveButton("Да") { _, _ -> deleteAvatar() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteAvatar() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = userRepository.deleteAvatar()
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Аватар удалён")
                    // Очищаем кэш Glide и показываем стандартную иконку
                    Glide.with(this@ProfileInfoFragment).clear(binding.ivAvatar)
                    binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
                    // Обновляем локальный кэш профиля
                    val cachedProfile = userRepository.getCachedProfile()
                    cachedProfile?.avatarUrl = null
                    cachedProfile?.let { userRepository.cacheProfile(it) }
                }
                is NetworkResult.Error -> {
                    ToastHelper.showError(requireContext(), "Не удалось удалить аватар: ${result.message}")
                }
                else -> {}
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1004 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            imagePickerLauncher.launch("image/*")
        } else if (requestCode == 1004) {
            ToastHelper.showError(requireContext(), "Нет разрешения на чтение хранилища")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val newUserId = sessionManager.getUserId()
            if (currentUserId != newUserId) {
                // Пользователь сменился – сбрасываем состояние и перезагружаем
                currentUserId = newUserId
                clearViewModelFields()
                isProfileLoaded = false
                loadProfile()
            }
        }
    }

    private fun clearViewModelFields() {
        viewModel.setFirstName("")
        viewModel.setLastName("")
        viewModel.setBirthDate("")
        viewModel.setPosition("")
        viewModel.setLevel("")
        viewModel.setNumber("")
        viewModel.setTeam("")
        viewModel.setEmail("")
        viewModel.setCityName("")
        viewModel.setCityId(0)
        viewModel.setRegionId(0)
        viewModel.setCountryId(1)
    }


}