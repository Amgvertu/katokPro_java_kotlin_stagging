package com.katok.pro.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.katok.pro.R
import com.katok.pro.databinding.FragmentAdminMessagesBinding
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.User
import com.katok.pro.model.admin.AdminMessageRequest
import com.katok.pro.repository.AdminMessageRepository
import com.katok.pro.repository.AdminRepository
import com.katok.pro.repository.LocationRepository
import com.katok.pro.util.SessionManager
import com.katok.pro.util.ToastHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class AdminMessagesFragment : BaseFragment(R.layout.fragment_admin_messages) {

    private var currentCityDialog: AlertDialog? = null
    private var currentTeamDialog: AlertDialog? = null
    private var _binding: FragmentAdminMessagesBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var adminRepository: AdminRepository
    private lateinit var adminMessageRepository: AdminMessageRepository
    private lateinit var sessionManager: SessionManager

    // ---------- Состояния для массовой рассылки ----------
    // Роли (выбранные чекбоксы)
    private val selectedRoles = mutableSetOf<String>() // "USER", "ADMIN", "MODERATOR"

    // Города
    private var allCitiesSelected = true   // по умолчанию выбраны все города
    private val selectedCityIds = mutableSetOf<Int>()
    private val selectedCityNames = mutableSetOf<String>()

    // Команды
    private var allTeamsSelected = true    // по умолчанию выбраны все команды
    private val selectedTeamNames = mutableSetOf<String>()

    // ---------- Состояния для индивидуальной рассылки ----------
    private val selectedUsers = mutableSetOf<User>()
    private var searchJob: Job? = null

    // ---------- Общие состояния ----------
    private var selectedImageFile: File? = null
    private var uploadedImageUrl: String? = null

    // Списки всех городов и команд (загружаются с сервера)
    private val allCities = mutableListOf<City>()
    private val allTeams = mutableListOf<String>()

    // Адаптер для найденных пользователей
    private lateinit var foundUsersAdapter: FoundUsersAdapter

    // Image picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                selectedImageFile = saveImageToTempFile(it)
                binding.ivPreview.setImageURI(it)
                binding.layoutImagePreview.visibility = View.VISIBLE
                uploadedImageUrl = null
                validateForm()
            } catch (e: Exception) {
                ToastHelper.showError(requireContext(), "Ошибка при выборе изображения")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        adminMessageRepository = AdminMessageRepository(requireContext())

        // Инициализация выбора ролей: по умолчанию ни одна не выбрана
        // (пользователь сам должен отметить)
        selectedRoles.clear()

        setupAdapters()
        setupListeners()
        loadCities()
        loadTeams()

        // Обновляем отображение выбранных городов/команд (по умолчанию "Все города" и "Все команды")
        updateSelectedCitiesDisplay()
        updateSelectedTeamsDisplay()
        validateForm()

        lifecycleScope.launch {
            if (!sessionManager.isAdmin()) {
                ToastHelper.showError(requireContext(), "Доступ запрещён")
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun setupAdapters() {
        // Spinner для выбора адресата (массовое / индивидуальное)
        val recipientTypes = resources.getStringArray(R.array.recipient_types)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, recipientTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRecipientType.adapter = adapter

        // Адаптер для найденных пользователей
        foundUsersAdapter = FoundUsersAdapter { user, isChecked ->
            if (isChecked) selectedUsers.add(user) else selectedUsers.remove(user)
            updateSelectedUsersCount()
            validateForm()
        }
        binding.rvFoundUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFoundUsers.adapter = foundUsersAdapter
    }

    private fun setupListeners() {
        // ---------- Тип сообщения (текстовое / графическое) ----------
        binding.rgMessageType.setOnCheckedChangeListener { _, checkedId ->
            val isText = checkedId == R.id.rbText
            binding.layoutTextFields.visibility = if (isText) View.VISIBLE else View.GONE
            binding.layoutGraphicFields.visibility = if (isText) View.GONE else View.VISIBLE
            validateForm()
        }

        // ---------- Адресат (массовое / индивидуальное) ----------
        binding.spinnerRecipientType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isMass = position == 0
                binding.layoutMassRecipients.visibility = if (isMass) View.VISIBLE else View.GONE
                binding.layoutIndividualRecipients.visibility = if (isMass) View.GONE else View.VISIBLE
                if (!isMass) {
                    // При переключении на индивидуальный режим сбрасываем поиск
                    foundUsersAdapter.submitList(emptyList())
                    selectedUsers.clear()
                    updateSelectedUsersCount()
                }
                validateForm()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // ---------- Чекбоксы ролей ----------
        binding.chkUsers.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedRoles.add("USER") else selectedRoles.remove("USER")
            validateForm()
        }
        binding.chkAdmins.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedRoles.add("ADMIN") else selectedRoles.remove("ADMIN")
            validateForm()
        }
        binding.chkModerators.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedRoles.add("MODERATOR") else selectedRoles.remove("MODERATOR")
            validateForm()
        }

        // ---------- Кнопки выбора городов и команд ----------
        binding.btnSelectCities.setOnClickListener { showCityMultiSelectDialog() }
        binding.btnSelectTeams.setOnClickListener { showTeamMultiSelectDialog() }

        // ---------- Поиск пользователей (индивидуальный режим) ----------
        binding.etSearchUser.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                val query = s.toString().trim()
                if (query.length < 2) {
                    foundUsersAdapter.submitList(emptyList())
                    binding.rvFoundUsers.visibility = View.GONE
                    return
                }
                searchJob = lifecycleScope.launch {
                    delay(300)
                    searchUsers(query)
                }
            }
        })

        // ---------- Загрузка изображения (графическое сообщение) ----------
        binding.btnPickImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnRemoveImage.setOnClickListener {
            selectedImageFile = null
            uploadedImageUrl = null
            binding.layoutImagePreview.visibility = View.GONE
            binding.ivPreview.setImageDrawable(null)
            validateForm()
        }

        // ---------- Отправка ----------
        binding.btnSend.setOnClickListener { sendMessage() }
    }

    // ===== Загрузка данных с сервера =====
    private fun loadCities() {
        lifecycleScope.launch {
            val result = locationRepository.getAllCitiesByCountry(1)
            if (result is NetworkResult.Success) {
                allCities.clear()
                allCities.addAll(result.data)
            }
        }
    }

    private fun loadTeams() {
        lifecycleScope.launch {
            val result = adminRepository.getTeams()
            if (result is NetworkResult.Success) {
                allTeams.clear()
                allTeams.addAll(result.data)
            } else {
                // fallback: извлечь команды из пользователей (если эндпоинт отсутствует)
                val usersResult = adminRepository.getAdminUsers(page = 0, size = 1000)
                if (usersResult is NetworkResult.Success) {
                    val teams = usersResult.data.content?.mapNotNull { it.profile?.team }?.distinct() ?: emptyList()
                    allTeams.addAll(teams)
                }
            }
        }
    }

    // ===== Диалог выбора городов (мультивыбор) =====
    private fun showCityMultiSelectDialog() {
        if (allCities.isEmpty()) {
            ToastHelper.showError(requireContext(), "Список городов пуст")
            return
        }

        val cityNames = mutableListOf<String>()
        cityNames.add(getString(R.string.all_cities))
        cityNames.addAll(allCities.map { it.name ?: "" })

        val checkedItems = BooleanArray(cityNames.size) { i ->
            if (i == 0) allCitiesSelected
            else allCitiesSelected || selectedCityIds.contains(allCities[i - 1].id)
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_cities)
            .setMultiChoiceItems(cityNames.toTypedArray(), checkedItems) { _, which, isChecked ->
                if (which == 0) {
                    allCitiesSelected = isChecked
                    if (isChecked) {
                        selectedCityIds.clear()
                        selectedCityNames.clear()
                        selectedCityIds.addAll(allCities.map { it.id })
                        selectedCityNames.addAll(allCities.mapNotNull { it.name })
                    } else {
                        selectedCityIds.clear()
                        selectedCityNames.clear()
                    }
                    // Закрываем текущий диалог и открываем новый
                    currentCityDialog?.dismiss()
                    showCityMultiSelectDialog()
                    return@setMultiChoiceItems
                } else {
                    if (allCitiesSelected) {
                        ToastHelper.showInfo(requireContext(), "Сначала снимите выбор 'Все города'")
                        return@setMultiChoiceItems
                    }
                    val city = allCities[which - 1]
                    if (isChecked) {
                        selectedCityIds.add(city.id)
                        city.name?.let { selectedCityNames.add(it) }
                    } else {
                        selectedCityIds.remove(city.id)
                        city.name?.let { selectedCityNames.remove(it) }
                    }
                }
            }
            .setPositiveButton("OK") { _, _ ->
                updateSelectedCitiesDisplay()
                validateForm()
            }
            .setNegativeButton("Отмена", null)

        val dialog = builder.create()
        currentCityDialog = dialog
        dialog.show()
    }

    private fun showTeamMultiSelectDialog() {
        if (allTeams.isEmpty()) {
            ToastHelper.showError(requireContext(), "Список команд пуст")
            return
        }

        val teamNames = mutableListOf<String>()
        teamNames.add(getString(R.string.all_teams))
        teamNames.addAll(allTeams)

        val checkedItems = BooleanArray(teamNames.size) { i ->
            if (i == 0) allTeamsSelected
            else allTeamsSelected || selectedTeamNames.contains(allTeams[i - 1])
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_teams)
            .setMultiChoiceItems(teamNames.toTypedArray(), checkedItems) { _, which, isChecked ->
                if (which == 0) {
                    allTeamsSelected = isChecked
                    if (isChecked) {
                        selectedTeamNames.clear()
                        selectedTeamNames.addAll(allTeams)
                    } else {
                        selectedTeamNames.clear()
                    }
                    currentTeamDialog?.dismiss()
                    showTeamMultiSelectDialog()
                    return@setMultiChoiceItems
                } else {
                    if (allTeamsSelected) {
                        ToastHelper.showInfo(requireContext(), "Сначала снимите выбор 'Все команды'")
                        return@setMultiChoiceItems
                    }
                    val team = allTeams[which - 1]
                    if (isChecked) selectedTeamNames.add(team) else selectedTeamNames.remove(team)
                }
            }
            .setPositiveButton("OK") { _, _ ->
                if (allTeamsSelected) {
                    selectedTeamNames.clear()
                    selectedTeamNames.addAll(allTeams)
                }
                updateSelectedTeamsDisplay()
                validateForm()
            }
            .setNegativeButton("Отмена", null)

        val dialog = builder.create()
        currentTeamDialog = dialog
        dialog.show()
    }

    // ===== Обновление отображения выбранных городов =====
    private fun updateSelectedCitiesDisplay() {
        binding.tvSelectedCities.text = if (allCitiesSelected) {
            "Все города"
        } else if (selectedCityNames.isEmpty()) {
            getString(R.string.no_cities_selected)
        } else {
            // Показываем до 10 названий, если больше – добавляем "..."
            val displayList = selectedCityNames.take(10)
            val suffix = if (selectedCityNames.size > 10) " ... (всего ${selectedCityNames.size})" else ""
            "Выбрано: ${displayList.joinToString(", ")}$suffix"
        }
    }

    // ===== Обновление отображения выбранных команд =====
    private fun updateSelectedTeamsDisplay() {
        binding.tvSelectedTeams.text = if (allTeamsSelected) {
            "Все команды"
        } else if (selectedTeamNames.isEmpty()) {
            getString(R.string.no_teams_selected)
        } else {
            val displayList = selectedTeamNames.take(10)
            val suffix = if (selectedTeamNames.size > 10) " ... (всего ${selectedTeamNames.size})" else ""
            "Выбрано: ${displayList.joinToString(", ")}$suffix"
        }
    }

    // ===== Поиск пользователей (индивидуальный режим) =====
    private suspend fun searchUsers(query: String) {
        // Используем правильный метод searchUsers из AdminRepository
        val result = adminRepository.searchUsers(query, 0, 10)

        when (result) {
            is NetworkResult.Success -> {
                val users = result.data.content ?: emptyList()
                if (users.isNotEmpty()) {
                    foundUsersAdapter.submitList(users)
                    binding.rvFoundUsers.visibility = View.VISIBLE
                } else {
                    foundUsersAdapter.submitList(emptyList())
                    binding.rvFoundUsers.visibility = View.GONE
                }
            }
            is NetworkResult.Error -> {
                // Если поиск не работает, показываем сообщение и скрываем список
                handleError(result)
                binding.rvFoundUsers.visibility = View.GONE
            }
            else -> {
                // Loading или другие состояния – можно игнорировать или показать прогресс
            }
        }
    }

    private fun updateSelectedUsersCount() {
        binding.tvSelectedUsersCount.text = "Выбрано: ${selectedUsers.size}"
    }

    // ===== Валидация формы =====
    private fun validateForm() {
        val isText = binding.rbText.isChecked
        val isMass = binding.spinnerRecipientType.selectedItemPosition == 0

        // Проверка содержимого
        val contentValid = if (isText) {
            !binding.etContent.text.toString().trim().isEmpty()
        } else {
            selectedImageFile != null
        }

        // Проверка получателей
        val recipientsValid = if (isMass) {
            // Должна быть выбрана хотя бы одна роль
            val hasRole = selectedRoles.isNotEmpty()
            // Должен быть выбран хотя бы один город (или все города)
            val hasCity = allCitiesSelected || selectedCityIds.isNotEmpty()
            // Должна быть выбрана хотя бы одна команда (или все команды)
            val hasTeam = allTeamsSelected || selectedTeamNames.isNotEmpty()
            hasRole && hasCity && hasTeam
        } else {
            selectedUsers.isNotEmpty()
        }

        binding.btnSend.isEnabled = contentValid && recipientsValid
    }

    // ===== Отправка сообщения =====
    private fun sendMessage() {
        val isText = binding.rbText.isChecked
        val title = if (isText) {
            binding.etTitle.text.toString().trim().takeIf { it.isNotEmpty() }
        } else null
        val content = if (isText) {
            binding.etContent.text.toString().trim()
        } else null
        val link = binding.etLink.text.toString().trim().takeIf { it.isNotEmpty() }
        val category = if (binding.rbPush.isChecked) "PUSH" else "INTERNAL"

        val isMass = binding.spinnerRecipientType.selectedItemPosition == 0
        val delivery = if (isMass) {
            AdminMessageRequest.DeliveryCriteria(
                allUsers = selectedRoles.contains("USER"),
                admins = selectedRoles.contains("ADMIN"),
                moderators = selectedRoles.contains("MODERATOR"),
                allCities = allCitiesSelected,
                cityIds = if (allCitiesSelected) emptyList() else selectedCityIds.toList(),
                allTeams = allTeamsSelected,
                teamNames = if (allTeamsSelected) emptyList() else selectedTeamNames.toList()
            )
        } else {
            AdminMessageRequest.DeliveryCriteria(
                userIds = selectedUsers.mapNotNull { it.id }
            )
        }

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnSend.isEnabled = false

            var imageUrl: String? = uploadedImageUrl
            if (!isText && selectedImageFile != null && imageUrl == null) {
                val mimeType = getMimeType(selectedImageFile!!)
                val part = MultipartBody.Part.createFormData(
                    "file",
                    selectedImageFile!!.name,
                    selectedImageFile!!.asRequestBody(mimeType.toMediaTypeOrNull())
                )
                val uploadResult = adminMessageRepository.uploadImage(part)
                if (uploadResult is NetworkResult.Success) {
                    imageUrl = uploadResult.data
                } else {
                    ToastHelper.showError(requireContext(), "Ошибка загрузки изображения")
                    binding.progressBar.visibility = View.GONE
                    binding.btnSend.isEnabled = true
                    return@launch
                }
            }

            val request = AdminMessageRequest(
                title = title,
                content = content ?: "",
                imageUrl = imageUrl,
                link = link,
                category = category,
                delivery = delivery
            )

            val result = adminMessageRepository.sendMessage(request)
            binding.progressBar.visibility = View.GONE
            binding.btnSend.isEnabled = true

            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Сообщение отправлено")
                    clearForm()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    // ===== Очистка формы после отправки =====
    private fun clearForm() {
        binding.etTitle.setText("")
        binding.etContent.setText("")
        binding.etLink.setText("")
        selectedImageFile = null
        uploadedImageUrl = null
        binding.layoutImagePreview.visibility = View.GONE
        binding.ivPreview.setImageDrawable(null)

        // Сброс массовых критериев
        selectedRoles.clear()
        allCitiesSelected = true
        selectedCityIds.clear()
        selectedCityNames.clear()
        allTeamsSelected = true
        selectedTeamNames.clear()
        updateSelectedCitiesDisplay()
        updateSelectedTeamsDisplay()

        // Сброс индивидуальных
        selectedUsers.clear()
        foundUsersAdapter.submitList(emptyList())
        binding.rvFoundUsers.visibility = View.GONE
        binding.etSearchUser.setText("")
        updateSelectedUsersCount()

        // Сброс чекбоксов ролей
        binding.chkUsers.isChecked = false
        binding.chkAdmins.isChecked = false
        binding.chkModerators.isChecked = false

        // Сброс радио
        binding.rbText.isChecked = true
        binding.rbSilent.isChecked = true
        binding.spinnerRecipientType.setSelection(0) // массовое

        validateForm()
    }

    // ===== Вспомогательные функции =====
    private fun saveImageToTempFile(uri: Uri): File {
        val timeStamp = System.currentTimeMillis().toString()
        val tempFile = File(requireContext().cacheDir, "msg_img_$timeStamp.jpg")
        requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return tempFile
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ===== Адаптер для найденных пользователей (индивидуальный режим) =====
    inner class FoundUsersAdapter(
        private val onCheckedChange: (User, Boolean) -> Unit
    ) : RecyclerView.Adapter<FoundUsersAdapter.ViewHolder>() {

        private var users = listOf<User>()

        fun submitList(list: List<User>) {
            users = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user_search, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]
            holder.bind(user)
        }

        override fun getItemCount(): Int = users.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val checkBox: CheckBox = itemView.findViewById(R.id.chkUser)
            private val tvName: TextView = itemView.findViewById(R.id.tvUserName)
            private val tvPhone: TextView = itemView.findViewById(R.id.tvUserPhone)

            fun bind(user: User) {
                val fullName = "${user.profile?.firstName ?: ""} ${user.profile?.lastName ?: ""}".trim()
                tvName.text = if (fullName.isNotEmpty()) fullName else "Пользователь"
                tvPhone.text = user.phone ?: ""
                checkBox.isChecked = selectedUsers.contains(user)
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    onCheckedChange(user, isChecked)
                }
                itemView.setOnClickListener {
                    checkBox.isChecked = !checkBox.isChecked
                }
            }
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}