package com.katok.pro.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.katok.pro.KatokApplication
import com.katok.pro.R
import com.katok.pro.databinding.FragmentCreateAdBinding
import com.katok.pro.model.*
import com.katok.pro.network.RealtimeEventBus
import com.katok.pro.network.WebSocketManager
import com.katok.pro.repository.AdRepository
import com.katok.pro.repository.LocationRepository
import com.katok.pro.repository.UserRepository
import com.katok.pro.services.WebSocketForegroundService
import com.katok.pro.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import com.katok.pro.model.DuplicateAd

@AndroidEntryPoint
class CreateAdFragment : BaseFragment(R.layout.fragment_create_ad) {

    private var _binding: FragmentCreateAdBinding? = null
    private val binding get() = _binding!!

    private lateinit var adRepository: AdRepository
    private lateinit var userRepository: UserRepository
    @Inject lateinit var locationRepository: LocationRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var tokenManager: TokenManager
    private lateinit var formPersistence: FormPersistence
    private var adId: String? = null
    private val rinksList = mutableListOf<Rink>()
    private val cityList = mutableListOf<City>()
    private var selectedCityId: Int? = null
    private var selectedCityName = ""
    private var selectedType = 0
    private var selectedSubType = 0
    private var anyTimeSelected = false
    private val selectedRinks = mutableListOf<Rink>()
    private var isMultiRinkSelection = false
    private var isRestoring = false
    private var formRestored = false
    private var prefilledLevel: String? = null
    private var webSocketManager: WebSocketManager? = null
    private val displayFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val serverFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            adId = it.getString("adId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateAdBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRepositories()
        initViews()
        setupSpinners()
        setupListeners()
        lifecycleScope.launch {
            loadUserData()
        }
        loadCities()
        lifecycleScope.launch {
            restoreFormIfNeeded()
        }
        if (adId != null) loadAdForEdit()
        observeRealtimeEvents()
    }

    private fun initRepositories() {
        adRepository = AdRepository()
        userRepository = UserRepository(requireContext())
        sessionManager = SessionManager(requireContext())
        tokenManager = TokenManager.getInstance(requireContext())
        formPersistence = FormPersistence(requireContext())
    }

    private fun initViews() {
        binding.spinnerCategory
        binding.spinnerType
        binding.layoutTypeGroup
        binding.etCity
        binding.etDate
        binding.etTimeStart
        binding.etTimeFrom
        binding.etTimeTo
        binding.etRink
        binding.layoutRinkGroup
        binding.layoutTimeSingle
        binding.layoutTimeRange
        binding.cbAnyTime
        binding.layoutLevelGroup
        binding.layoutTeamGroup
        binding.layoutPlayersGroup
        binding.layoutDeliveryGroup
        binding.layoutLevelMulti
        binding.cbLevelA
        binding.cbLevelB
        binding.cbLevelC
        binding.cbLevelD
        binding.cbLevelE
        binding.cbLevelF
        binding.cbLevelG
        binding.cbLevelH
        binding.etTeam
        binding.cbShowTeam
        binding.spinnerGoalieCount
        binding.layoutGoalieCount
        binding.layoutFieldPlayers
        binding.etDefenders
        binding.etForwards
        binding.tvTotalPlayers
        binding.cbDelivery
        binding.etPayment
        binding.tvContactName
        binding.tvContactPhone
        binding.btnSubmit
        binding.btnCancel
        binding.layoutSelectedRinks
        binding.rvSelectedRinks

        binding.rvSelectedRinks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSelectedRinks.isNestedScrollingEnabled = true

        binding.etTeam.isEnabled = false
        binding.etTeam.isFocusable = false
        binding.etTeam.isClickable = false

        binding.etCity.isClickable = true
        binding.etCity.isFocusable = false
        binding.etCity.isCursorVisible = false

        binding.cbAnyTime.setOnCheckedChangeListener { _, isChecked ->
            anyTimeSelected = isChecked
            if (isChecked) {
                binding.layoutTimeSingle.visibility = View.GONE
                binding.layoutTimeRange.visibility = View.GONE
            } else {
                if (selectedType == 2 || (selectedType == 3 && selectedSubType == 1)) {
                    binding.layoutTimeRange.visibility = View.VISIBLE
                    binding.layoutTimeSingle.visibility = View.GONE
                } else {
                    binding.layoutTimeSingle.visibility = View.VISIBLE
                    binding.layoutTimeRange.visibility = View.GONE
                }
            }
        }

        binding.etRink.setOnClickListener { showRinkSelectionDialog() }

        val currentDate = displayFormat.format(Date())
        binding.etDate.setText(currentDate)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        binding.etTimeStart.setText(currentTime)
        binding.etTimeFrom.setText(currentTime)
        val cal = Calendar.getInstance()
        cal.add(Calendar.HOUR_OF_DAY, 1)
        binding.etTimeTo.setText(timeFormat.format(cal.time))
    }

    private fun setupSpinners() {
        val categories = arrayOf("Выберите категорию", "Ищу игрока", "Ищу лёд", "Товарищеский матч", "Ищу специалиста")
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = categoryAdapter

        val goalieCounts = arrayOf("1 вратарь", "2 вратаря")
        val goalieAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, goalieCounts)
        goalieAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGoalieCount.adapter = goalieAdapter
    }

    private fun setupListeners() {
        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isRestoring) return
                if (position > 0) {
                    binding.layoutTypeGroup.visibility = View.VISIBLE
                    updateTypeSpinner(position)
                    resetFormForNewCategory()
                } else {
                    binding.layoutTypeGroup.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isRestoring) return
                if (position > 0) {
                    onTypeSelected(binding.spinnerCategory.selectedItemPosition, position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.etCity.setOnClickListener { showCitySelector() }
        binding.etDate.setOnClickListener { showDatePicker() }
        binding.etTimeStart.setOnClickListener { showTimePicker(binding.etTimeStart) }
        binding.etTimeFrom.setOnClickListener { showTimePicker(binding.etTimeFrom) }
        binding.etTimeTo.setOnClickListener { showTimePicker(binding.etTimeTo) }

        binding.etDefenders.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateTotalPlayers() }
        })
        binding.etForwards.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateTotalPlayers() }
        })

        binding.btnSubmit.setOnClickListener { submitAd() }
        binding.btnCancel.setOnClickListener {
            NavHostFragment.findNavController(this@CreateAdFragment).popBackStack()
            resetForm()
        }
    }

    private fun updateTypeSpinner(categoryPosition: Int) {
        val types = when (categoryPosition) {
            1 -> arrayOf("Выберите тип", "Вратаря", "Полевого")
            2 -> arrayOf("Выберите тип", "Вратарь", "Полевой")
            3 -> arrayOf("Выберите тип", "Ищу", "Предлагаю")
            4 -> arrayOf("Выберите тип", "Судья", "Фотограф", "Медик", "Тренер")
            else -> arrayOf()
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerType.adapter = adapter
    }

    private fun onTypeSelected(categoryPos: Int, typePos: Int) {
        val oldType = selectedType
        val oldSubType = selectedSubType
        selectedType = categoryPos
        selectedSubType = typePos
        updateFieldsByTypeAndSubType(selectedType, selectedSubType)
        resetFieldsForNewType(oldType, oldSubType)
        view?.requestLayout()
    }

    private fun updateFieldsByTypeAndSubType(type: Int, subType: Int) {
        // Скрываем все группы
        binding.layoutLevelGroup.visibility = View.GONE
        binding.layoutTeamGroup.visibility = View.GONE
        binding.layoutPlayersGroup.visibility = View.GONE
        binding.layoutDeliveryGroup.visibility = View.GONE
        binding.layoutTimeRange.visibility = View.GONE
        binding.layoutTimeSingle.visibility = View.VISIBLE
        binding.layoutLevelMulti.visibility = View.GONE
        binding.layoutFieldPlayers.visibility = View.GONE
        binding.layoutGoalieCount.visibility = View.GONE
        binding.spinnerGoalieCount.visibility = View.GONE
        binding.cbAnyTime.visibility = View.GONE

        // Время
        if (type == 2 || (type == 3 && subType == 1)) {
            binding.layoutTimeRange.visibility = View.VISIBLE
            binding.layoutTimeSingle.visibility = View.GONE
            binding.cbAnyTime.visibility = View.VISIBLE
        } else {
            binding.layoutTimeSingle.visibility = View.VISIBLE
            binding.layoutTimeRange.visibility = View.GONE
        }

        // Уровень
        if (type != 4) {
            binding.layoutLevelGroup.visibility = View.VISIBLE
            binding.layoutLevelMulti.visibility = View.VISIBLE
        }

        // Команда
        if (type == 1 || type == 3 || type == 4) {
            binding.layoutTeamGroup.visibility = View.VISIBLE
            if (type == 3) {
                binding.cbShowTeam.visibility = View.GONE
                binding.cbShowTeam.isChecked = true
            } else {
                binding.cbShowTeam.visibility = View.VISIBLE
            }
        }

        // Количество игроков
        if (type == 1) {
            binding.layoutPlayersGroup.visibility = View.VISIBLE
            if (subType == 1) {
                binding.layoutGoalieCount.visibility = View.VISIBLE
                binding.spinnerGoalieCount.visibility = View.VISIBLE
            } else if (subType == 2) {
                binding.layoutFieldPlayers.visibility = View.VISIBLE
            }
        }

        // Доставка
        if ((type == 1 && subType == 1) || type == 4 || (type == 2 && subType == 1)) {
            binding.layoutDeliveryGroup.visibility = View.VISIBLE
            if ((type == 1 && subType == 1) || type == 4) {
                binding.cbDelivery.text = "Доставка"
            } else {
                binding.cbDelivery.text = "Нужна доставка"
            }
        } else {
            binding.cbDelivery.text = "Нужна доставка"
        }

        // Оплата
        binding.etPayment.visibility = if (type == 2 && subType == 2) View.GONE else View.VISIBLE

        // Режим выбора ЛДС
        isMultiRinkSelection = when {
            type == 1 || (type == 3 && subType == 2) || type == 4 -> false
            else -> true
        }


    }

    private fun resetFieldsForNewType(oldType: Int, oldSubType: Int) {
        if (selectedType == 4) {
            listOf(binding.cbLevelA, binding.cbLevelB, binding.cbLevelC, binding.cbLevelD,
                binding.cbLevelE, binding.cbLevelF, binding.cbLevelG, binding.cbLevelH).forEach { it.isChecked = false }
        }
        if (selectedType != 1) {
            binding.spinnerGoalieCount.setSelection(0)
            binding.etDefenders.setText("")
            binding.etForwards.setText("")
            binding.tvTotalPlayers.text = "Требуется игроков: 0"
        } else {
            if (selectedSubType == 1 && oldSubType == 2) {
                binding.etDefenders.setText("")
                binding.etForwards.setText("")
            } else if (selectedSubType == 2 && oldSubType == 1) {
                binding.spinnerGoalieCount.setSelection(0)
            }
        }
        val deliveryRequired = (selectedType == 1 && selectedSubType == 1) || selectedType == 4 || (selectedType == 2 && selectedSubType == 1)
        if (!deliveryRequired) binding.cbDelivery.isChecked = false
        val timeRangeSupported = (selectedType == 2) || (selectedType == 3 && selectedSubType == 1)
        if (!timeRangeSupported) {
            anyTimeSelected = false
            binding.cbAnyTime.isChecked = false
            binding.layoutTimeSingle.visibility = View.VISIBLE
            binding.layoutTimeRange.visibility = View.GONE
        }
        val newMulti = (selectedType == 2) || (selectedType == 3 && selectedSubType == 1)
        val oldMulti = (oldType == 2) || (oldType == 3 && oldSubType == 1)
        if (newMulti != oldMulti && !newMulti && selectedRinks.size > 1) {
            val first = selectedRinks[0]
            selectedRinks.clear()
            selectedRinks.add(first)
            updateSelectedRinksDisplay()
        }
        if (selectedType == 2) binding.cbShowTeam.isChecked = true
        if (selectedType == 2 && selectedSubType == 2) binding.etPayment.setText("")
        if (selectedType != 1) binding.layoutPlayersGroup.visibility = View.GONE
        if (selectedType != 2 && !(selectedType == 3 && selectedSubType == 1)) {
            binding.layoutTimeRange.visibility = View.GONE
            binding.layoutTimeSingle.visibility = View.VISIBLE
        }
        if (selectedType == 4) binding.layoutLevelGroup.visibility = View.GONE
    }

    private fun resetFormForNewCategory() {
        listOf(binding.cbLevelA, binding.cbLevelB, binding.cbLevelC, binding.cbLevelD,
            binding.cbLevelE, binding.cbLevelF, binding.cbLevelG, binding.cbLevelH).forEach { it.isChecked = false }
        binding.spinnerGoalieCount.setSelection(0)
        binding.etDefenders.setText("")
        binding.etForwards.setText("")
        binding.tvTotalPlayers.text = "Требуется игроков: 0"
        binding.cbDelivery.isChecked = false
        anyTimeSelected = false
        binding.cbAnyTime.isChecked = false
        binding.layoutTimeSingle.visibility = View.VISIBLE
        binding.layoutTimeRange.visibility = View.GONE
        selectedRinks.clear()
        updateSelectedRinksDisplay()
        selectedType = 0
        selectedSubType = 0
        binding.layoutLevelGroup.visibility = View.GONE
        binding.layoutTeamGroup.visibility = View.GONE
        binding.layoutPlayersGroup.visibility = View.GONE
        binding.layoutDeliveryGroup.visibility = View.GONE
        binding.layoutTimeRange.visibility = View.GONE
        binding.layoutTimeSingle.visibility = View.VISIBLE
        binding.layoutGoalieCount.visibility = View.GONE
        binding.layoutFieldPlayers.visibility = View.GONE
        binding.cbAnyTime.visibility = View.GONE
        binding.spinnerType.setSelection(0)
        lifecycleScope.launch {
            formPersistence.clear()
        }
        formRestored = false
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        var year = calendar.get(Calendar.YEAR)
        var month = calendar.get(Calendar.MONTH)
        var day = calendar.get(Calendar.DAY_OF_MONTH)
        val currentDate = binding.etDate.text.toString()
        if (currentDate.isNotEmpty()) {
            try {
                val date = displayFormat.parse(currentDate)
                date?.let {
                    val cal = Calendar.getInstance().apply { time = it }
                    year = cal.get(Calendar.YEAR)
                    month = cal.get(Calendar.MONTH)
                    day = cal.get(Calendar.DAY_OF_MONTH)
                }
            } catch (_: ParseException) {}
        }
        DatePickerDialog(requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                binding.etDate.setText(String.format("%02d.%02d.%04d", selectedDay, selectedMonth + 1, selectedYear))
            }, year, month, day).show()
    }

    private fun showTimePicker(timeField: EditText) {
        val calendar = Calendar.getInstance()
        var hour = calendar.get(Calendar.HOUR_OF_DAY)
        var minute = calendar.get(Calendar.MINUTE)
        val current = timeField.text.toString()
        if (current.isNotEmpty()) {
            try {
                val parts = current.split(":")
                hour = parts[0].toInt()
                minute = parts[1].toInt()
            } catch (_: Exception) {}
        }
        TimePickerDialog(requireContext(),
            { _, selectedHour, selectedMinute ->
                timeField.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
            }, hour, minute, true).show()
    }

    private fun updateTotalPlayers() {
        val defenders = binding.etDefenders.text.toString().toIntOrNull() ?: 0
        val forwards = binding.etForwards.text.toString().toIntOrNull() ?: 0
        binding.tvTotalPlayers.text = "Требуется игроков: ${defenders + forwards}"
    }

    private fun showCitySelector() {
        if (!isAdded || context == null) return
        val dialog = CitySelectorDialog(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            object : CitySelectorDialog.OnCitySelectedListener {
                override fun onCitySelected(city: City) {
                    selectedCityId = city.id
                    selectedCityName = city.name ?: ""
                    binding.etCity.setText(selectedCityName)
                    loadRinks(city.id)
                }
            },
            locationRepository
        )
        dialog.show()
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

    private fun loadRinks(cityId: Int) {
        lifecycleScope.launch {
            if (!isAdded) return@launch
            val result = adRepository.getRinksByCity(cityId)
            if (!isAdded) return@launch
            if (result is NetworkResult.Success) {
                rinksList.clear()
                rinksList.addAll(result.data)
                binding.layoutRinkGroup.visibility = View.VISIBLE
            } else {
                handleError(result)
            }
        }
    }

    private fun showRinkSelectionDialog() {
        if (rinksList.isEmpty()) {
            ToastHelper.showError(requireContext(), "Сначала выберите город")
            return
        }
        val items = Array(rinksList.size) { i ->
            val rink = rinksList[i]
            SpannableStringBuilder().apply {
                append(rink.name ?: "")
                append("\n    ")
                if (!rink.address.isNullOrEmpty()) append(rink.address)
                setSpan(StyleSpan(Typeface.BOLD), 0, (rink.name?.length ?: 0), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(AbsoluteSizeSpan(14, true), 0, (rink.name?.length ?: 0), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (!rink.address.isNullOrEmpty()) {
                    val start = (rink.name?.length ?: 0) + 1 + 4
                    val end = start + (rink.address?.length ?: 0)
                    setSpan(StyleSpan(Typeface.NORMAL), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(AbsoluteSizeSpan(11, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        val checked = BooleanArray(rinksList.size) { i -> selectedRinks.contains(rinksList[i]) }
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Выберите ЛДС")
        if (isMultiRinkSelection) {
            builder.setMultiChoiceItems(items, checked) { _, which, isChecked ->
                val rink = rinksList[which]
                if (isChecked) { if (!selectedRinks.contains(rink)) selectedRinks.add(rink) }
                else selectedRinks.remove(rink)
            }
        } else {
            var checkedItem = -1
            if (selectedRinks.isNotEmpty()) {
                val selected = selectedRinks[0]
                for (i in rinksList.indices) {
                    if (rinksList[i] == selected) { checkedItem = i; break }
                }
            }
            builder.setSingleChoiceItems(items, checkedItem) { _, which ->
                selectedRinks.clear()
                selectedRinks.add(rinksList[which])
            }
        }
        builder.setPositiveButton("OK") { _, _ -> updateSelectedRinksDisplay() }
        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    private fun updateSelectedRinksDisplay() {
        if (selectedRinks.isEmpty()) {
            binding.etRink.setText(null)
            binding.layoutSelectedRinks.visibility = View.GONE
        } else {
            if (isMultiRinkSelection) {
                binding.etRink.setText("Выбрано: ${selectedRinks.size} ЛДС")
                binding.layoutSelectedRinks.visibility = View.VISIBLE
                binding.rvSelectedRinks.adapter = SelectedRinksAdapter(selectedRinks)
            } else {
                val rink = selectedRinks[0]
                val name = rink.name ?: ""
                val address = rink.address ?: ""
                SpannableStringBuilder().apply {
                    append(name)
                    if (address.isNotEmpty()) {
                        append("\n    ")
                        append(address)
                    }
                    setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(AbsoluteSizeSpan(14, true), 0, name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (address.isNotEmpty()) {
                        val start = name.length + 1 + 4
                        val end = start + address.length
                        setSpan(StyleSpan(Typeface.NORMAL), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(AbsoluteSizeSpan(11, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    binding.etRink.setText(this)
                }
                binding.layoutSelectedRinks.visibility = View.GONE
            }
        }
    }

    private inner class SelectedRinksAdapter(private val rinks: List<Rink>) : RecyclerView.Adapter<SelectedRinksAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_selected_rink, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rink = rinks[position]
            holder.tvName.text = rink.name
            holder.tvName.setTypeface(null, Typeface.BOLD)
            if (!rink.address.isNullOrEmpty()) {
                holder.tvAddress.text = rink.address
                holder.tvAddress.visibility = View.VISIBLE
            } else holder.tvAddress.visibility = View.GONE
        }
        override fun getItemCount() = rinks.size
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName = itemView.findViewById<TextView>(R.id.tvRinkName)
            val tvAddress = itemView.findViewById<TextView>(R.id.tvRinkAddress)
        }
    }

    private suspend fun loadUserData() {
        val userName = sessionManager.getUserName() ?: ""
        val userPhone = sessionManager.getUserPhone() ?: ""
        binding.tvContactName.text = userName
        binding.tvContactPhone.text = PhoneUtils.formatPhoneNumberForDisplay(userPhone)
        loadUserProfileForPrefill()
    }

    private fun loadUserProfileForPrefill() {
        lifecycleScope.launch {
            val result = userRepository.getMyProfile()
            if (result is NetworkResult.Success) {
                val profile = result.data
                val firstName = profile.firstName ?: ""
                val lastName = profile.lastName ?: ""
                val fullName = "$firstName $lastName".trim()
                binding.tvContactName.text = fullName
                prefilledLevel = profile.level
                if (!profile.team.isNullOrEmpty()) binding.etTeam.setText(profile.team)
                profile.homeCity?.let {
                    selectedCityId = it.id
                    selectedCityName = it.name ?: ""
                    binding.etCity.setText(selectedCityName)
                    loadRinks(it.id)
                }
            }
        }
    }

    private fun loadAdForEdit() {
        lifecycleScope.launch {
            val result = adRepository.getAdById(adId!!)
            if (result is NetworkResult.Success) {
                fillFormWithAd(result.data)
            } else {
                handleError(result)
            }
        }
    }

    private fun fillFormWithAd(ad: Ad) {
        isRestoring = true
        val categoryPos = ad.type
        val typePos = ad.subType
        /*binding.spinnerCategory.onItemSelectedListener = null
        binding.spinnerType.onItemSelectedListener = null*/

        /*updateTypeSpinner(categoryPos)
        binding.spinnerCategory.setSelection(categoryPos)
        binding.spinnerType.setSelection(typePos)*/

        // --- Установка категории и подтипа ---


        binding.layoutTypeGroup.visibility = if (categoryPos > 0) View.VISIBLE else View.GONE
        /*binding.spinnerCategory.onItemSelectedListener = binding.spinnerCategory.onItemSelectedListener
        binding.spinnerType.onItemSelectedListener = binding.spinnerType.onItemSelectedListener*/
        selectedType = ad.type
        selectedSubType = ad.subType
        updateFieldsByTypeAndSubType(selectedType, selectedSubType)

        // Город
        if (ad.city != null) {
            selectedCityId = ad.city!!.id
            selectedCityName = ad.city!!.name ?: ""
            binding.etCity.setText(selectedCityName)
        } else if (ad.cityId != null) {
            selectedCityId = ad.cityId
            selectedCityName = cityList.find { it.id == selectedCityId }?.name ?: "Город $selectedCityId"
            binding.etCity.setText(selectedCityName)
        }

        // Дата и время
        ad.startTime?.let { startTime ->
            val parts = startTime.split("T")
            if (parts.size == 2) {
                val ymd = parts[0].split("-")
                if (ymd.size == 3) binding.etDate.setText("${ymd[2]}.${ymd[1]}.${ymd[0]}")
                val time = parts[1].substring(0, 5)

                // Определяем, нужно ли показывать диапазон (только для типа 2 или для типа 3 с подтипом 1)
                val showRange = (selectedType == 2) || (selectedType == 3 && selectedSubType == 1)

                if (showRange && ad.endTime != null) {
                    binding.layoutTimeRange.visibility = View.VISIBLE
                    binding.layoutTimeSingle.visibility = View.GONE
                    var endTimePart = ad.endTime!!
                    if (endTimePart.contains("T")) endTimePart = endTimePart.split("T")[1]
                    if (endTimePart.length > 5) endTimePart = endTimePart.substring(0, 5)
                    binding.etTimeFrom.setText(time)
                    binding.etTimeTo.setText(endTimePart)
                } else {
                    binding.layoutTimeSingle.visibility = View.VISIBLE
                    binding.layoutTimeRange.visibility = View.GONE
                    binding.etTimeStart.setText(time)
                }
            }
        }

        // Любое время
        if (ad.startTime != null && ad.startTime!!.endsWith("T00:00:00") &&
            ad.endTime != null && ad.endTime!!.endsWith("T23:59:00")) {
            anyTimeSelected = true
            binding.cbAnyTime.isChecked = true
            binding.layoutTimeSingle.visibility = View.GONE
            binding.layoutTimeRange.visibility = View.GONE
        }

        // Уровни
        ad.level?.forEach { level ->
            when (level) {
                "A" -> binding.cbLevelA.isChecked = true
                "B" -> binding.cbLevelB.isChecked = true
                "C" -> binding.cbLevelC.isChecked = true
                "D" -> binding.cbLevelD.isChecked = true
                "E" -> binding.cbLevelE.isChecked = true
                "F" -> binding.cbLevelF.isChecked = true
                "G" -> binding.cbLevelG.isChecked = true
                "H" -> binding.cbLevelH.isChecked = true
            }
        }

        // Команда
        if (!ad.team.isNullOrEmpty()) binding.etTeam.setText(ad.team)
        binding.cbShowTeam.isChecked = ad.showTeam ?: true
        if (selectedType == 3) binding.cbShowTeam.visibility = View.GONE

        // Оплата
        ad.details?.payment?.let { binding.etPayment.setText(it) }

        // Количество игроков
        if (selectedType == 1 && selectedSubType == 1) {
            ad.goaliesCount?.let { count -> binding.spinnerGoalieCount.setSelection(count - 1) }
        } else if (selectedType == 1 && selectedSubType == 2) {
            ad.defendersCount?.let { binding.etDefenders.setText(it.toString()) }
            ad.forwardsCount?.let { binding.etForwards.setText(it.toString()) }
        }

        // Доставка
        ad.details?.delivery?.let { binding.cbDelivery.isChecked = it == "true" }

        // ЛДС
        selectedCityId?.let { cityId ->
            ad.rinkIds?.takeIf { it.isNotEmpty() }?.let { rinkIdsToRestore ->
                lifecycleScope.launch {
                    val result = adRepository.getRinksByCity(cityId)
                    if (result is NetworkResult.Success) {
                        rinksList.clear()
                        rinksList.addAll(result.data)
                        binding.layoutRinkGroup.visibility = View.VISIBLE
                        selectedRinks.clear()
                        for (rinkId in rinkIdsToRestore) {
                            rinksList.find { it.id == rinkId }?.let { selectedRinks.add(it) }
                        }
                        updateSelectedRinksDisplay()
                    }
                }
            }
        }

        updateFieldsByTypeAndSubType(selectedType, selectedSubType)
        if (anyTimeSelected) {
            binding.layoutTimeSingle.visibility = View.GONE
            binding.layoutTimeRange.visibility = View.GONE
        }

        /*view?.postDelayed({
            if (isAdded) isRestoring = false
        }, 300)*/

        // Обновляем спиннеры (isRestoring всё ещё true)
        updateTypeSpinner(categoryPos)
        binding.spinnerCategory.setSelection(categoryPos)

// Используем Handler с задержкой для гарантии завершения отрисовки
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("CreateAd", "Delayed selection: categoryPos=$categoryPos, typePos=$typePos, adapter size=${binding.spinnerType.adapter?.count}")
            if (typePos >= 0 && typePos < (binding.spinnerType.adapter?.count ?: 0)) {
                binding.spinnerType.setSelection(typePos, false)
                binding.spinnerType.invalidate()
                binding.spinnerType.requestLayout()
                // Дополнительно можно обновить текст, если setSelection не сработал
                val selectedView = binding.spinnerType.selectedView as? TextView
                selectedView?.text = binding.spinnerType.adapter?.getItem(typePos)?.toString()
            } else {
                Log.e("CreateAd", "Invalid typePos=$typePos")
            }
            isRestoring = false
        }, 100)
    }

    private fun collectFormData(): Ad? {
        val categoryPos = binding.spinnerCategory.selectedItemPosition
        val typePos = binding.spinnerType.selectedItemPosition
        if (categoryPos == 0 || typePos == 0) return null
        val ad = Ad()
        ad.type = categoryPos
        ad.subType = typePos
        if (selectedCityId != null && selectedCityId!! > 0) ad.cityId = selectedCityId
        ad.contactName = binding.tvContactName.text.toString().takeIf { it.isNotEmpty() }
        ad.contactPhone = binding.tvContactPhone.text.toString().takeIf { it.isNotEmpty() }

        val date = binding.etDate.text.toString()
        if (date.isNotEmpty()) {
            val parts = date.split(".")
            if (parts.size == 3) {
                val yyyy = parts[2]
                val mm = parts[1]
                val dd = parts[0]
                val datePart = "$yyyy-$mm-$dd"

                if (anyTimeSelected) {
                    ad.startTime = "${datePart}T00:00:00"
                    ad.endTime = "${datePart}T23:59:00"
                } else {
                    if (binding.layoutTimeSingle.visibility == View.VISIBLE) {
                        val time = binding.etTimeStart.text.toString()
                        if (time.isNotEmpty()) {
                            ad.startTime = "${datePart}T${time}:00"
                        } else {
                            ad.startTime = datePart // если время не указано – только дата
                        }
                    } else if (binding.layoutTimeRange.visibility == View.VISIBLE) {
                        val timeFrom = binding.etTimeFrom.text.toString()
                        val timeTo = binding.etTimeTo.text.toString()
                        if (timeFrom.isNotEmpty()) {
                            ad.startTime = "${datePart}T${timeFrom}:00"
                        } else {
                            ad.startTime = datePart
                        }
                        if (timeTo.isNotEmpty()) {
                            ad.endTime = "${datePart}T${timeTo}:00"
                        }
                    }
                }
            }
        }

        if (selectedType != 4) {
            val levels = mutableListOf<String>()
            if (binding.cbLevelA.isChecked) levels.add("A")
            if (binding.cbLevelB.isChecked) levels.add("B")
            if (binding.cbLevelC.isChecked) levels.add("C")
            if (binding.cbLevelD.isChecked) levels.add("D")
            if (binding.cbLevelE.isChecked) levels.add("E")
            if (binding.cbLevelF.isChecked) levels.add("F")
            if (binding.cbLevelG.isChecked) levels.add("G")
            if (binding.cbLevelH.isChecked) levels.add("H")
            if (levels.isNotEmpty()) ad.level = levels
        }

        val team = binding.etTeam.text.toString()
        if (team.isNotEmpty()) ad.team = team
        ad.showTeam = if (selectedType == 3) true else binding.cbShowTeam.isChecked

        val payment = binding.etPayment.text.toString()
        if (payment.isNotEmpty() && !(selectedType == 2 && selectedSubType == 2)) {
            if (ad.details == null) ad.details = AdDetails()
            ad.details!!.payment = payment
        }

        if (selectedType == 1 && selectedSubType == 1 && binding.layoutGoalieCount.visibility == View.VISIBLE) {
            ad.goaliesCount = binding.spinnerGoalieCount.selectedItemPosition + 1
        } else if (selectedType == 1 && selectedSubType == 2 && binding.layoutFieldPlayers.visibility == View.VISIBLE) {
            ad.defendersCount = binding.etDefenders.text.toString().toIntOrNull()
            ad.forwardsCount = binding.etForwards.text.toString().toIntOrNull()
        }

        if (binding.layoutDeliveryGroup.visibility == View.VISIBLE) {
            if (ad.details == null) ad.details = AdDetails()
            ad.details!!.delivery = if (binding.cbDelivery.isChecked) "true" else null
        }

        ad.rinkIds = selectedRinks.map { it.id }
        val gson = com.google.gson.Gson()
        Log.d("CreateAd", "Sending ad: ${gson.toJson(ad)}")
        return ad
    }

    private fun submitAd() {
        if (binding.spinnerCategory.selectedItemPosition == 0) {
            ToastHelper.showError(requireContext(), "Выберите категорию")
            return
        }
        if (binding.spinnerType.selectedItemPosition == 0) {
            ToastHelper.showError(requireContext(), "Выберите тип объявления")
            return
        }
        if (selectedCityId == null || selectedCityId == 0) {
            ToastHelper.showError(requireContext(), "Выберите город")
            return
        }
        val date = binding.etDate.text.toString()
        if (date.isEmpty()) {
            ToastHelper.showError(requireContext(), "Укажите дату")
            return
        }
        if (!anyTimeSelected) {
            if (binding.layoutTimeSingle.visibility == View.VISIBLE && binding.etTimeStart.text.toString().isEmpty()) {
                ToastHelper.showError(requireContext(), "Укажите время")
                return
            }
            if (binding.layoutTimeRange.visibility == View.VISIBLE && (binding.etTimeFrom.text.toString().isEmpty() || binding.etTimeTo.text.toString().isEmpty())) {
                ToastHelper.showError(requireContext(), "Укажите время начала и окончания")
                return
            }
        }
        if (binding.layoutLevelGroup.visibility == View.VISIBLE) {
            val levelSelected = listOf(
                binding.cbLevelA, binding.cbLevelB, binding.cbLevelC, binding.cbLevelD,
                binding.cbLevelE, binding.cbLevelF, binding.cbLevelG, binding.cbLevelH
            ).any { it.isChecked }
            if (!levelSelected) {
                ToastHelper.showError(requireContext(), "Выберите хотя бы один уровень")
                return
            }
        }
        if (!(selectedType == 2 && selectedSubType == 2) && binding.etPayment.text.toString().trim().isEmpty()) {
            ToastHelper.showError(requireContext(), "Укажите оплату")
            return
        }
        if (selectedType == 1) {
            if (selectedSubType == 1 && binding.layoutGoalieCount.visibility != View.VISIBLE) {
                ToastHelper.showError(requireContext(), "Укажите количество вратарей")
                return
            } else if (selectedSubType == 2) {
                val defenders = binding.etDefenders.text.toString().toIntOrNull() ?: 0
                val forwards = binding.etForwards.text.toString().toIntOrNull() ?: 0
                if (defenders == 0 && forwards == 0) {
                    ToastHelper.showError(requireContext(), "Укажите количество защитников или нападающих")
                    return
                }
            }
        }
        if (selectedRinks.isEmpty()) {
            ToastHelper.showError(requireContext(), "Выберите хотя бы один ЛДС")
            return
        }

        val ad = collectFormData() ?: return

        // ===== НОВАЯ ЛОГИКА: проверка на дубликаты перед отправкой =====
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            // 1. Проверяем дубликаты
            val duplicateResult = adRepository.checkDuplicate(ad)
            binding.progressBar.visibility = View.GONE

            when (duplicateResult) {
                is NetworkResult.Success -> {
                    val duplicates = duplicateResult.data
                    if (duplicates.isNotEmpty()) {
                        // Найдены дубликаты – показываем предупреждение
                        showDuplicateWarning(duplicates, ad)
                        return@launch
                    }
                    // Дубликатов нет – отправляем
                    sendAdToServer(ad)
                }
                is NetworkResult.Error -> {
                    // Ошибка при проверке дубликатов – показываем ошибку и прерываем
                    handleError(duplicateResult)
                }
                else -> {
                    // Loading или другие состояния – ничего не делаем
                }
            }
        }
    }

    private fun resetForm() {
        selectedCityId = null
        selectedCityName = ""
        selectedRinks.clear()
        selectedType = 0
        selectedSubType = 0
        anyTimeSelected = false
        prefilledLevel = null
        binding.spinnerCategory.setSelection(0)
        binding.layoutTypeGroup.visibility = View.GONE
        binding.etCity.setText("")
        binding.etRink.setText(null)
        binding.layoutSelectedRinks.visibility = View.GONE
        binding.etDate.setText(displayFormat.format(Date()))
        binding.etTimeStart.setText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
        binding.etTimeFrom.setText(binding.etTimeStart.text)
        val cal = Calendar.getInstance()
        cal.add(Calendar.HOUR_OF_DAY, 1)
        binding.etTimeTo.setText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time))
        binding.cbAnyTime.isChecked = false
        listOf(binding.cbLevelA, binding.cbLevelB, binding.cbLevelC, binding.cbLevelD,
            binding.cbLevelE, binding.cbLevelF, binding.cbLevelG, binding.cbLevelH).forEach { it.isChecked = false }
        binding.etTeam.setText("")
        binding.cbShowTeam.isChecked = true
        binding.spinnerGoalieCount.setSelection(0)
        binding.etDefenders.setText("")
        binding.etForwards.setText("")
        binding.cbDelivery.isChecked = false
        binding.etPayment.setText("")
        binding.layoutLevelGroup.visibility = View.GONE
        binding.layoutTeamGroup.visibility = View.GONE
        binding.layoutPlayersGroup.visibility = View.GONE
        binding.layoutDeliveryGroup.visibility = View.GONE
        binding.layoutTimeRange.visibility = View.GONE
        binding.layoutTimeSingle.visibility = View.VISIBLE
        binding.layoutGoalieCount.visibility = View.GONE
        binding.layoutFieldPlayers.visibility = View.GONE
    }

    private suspend fun restoreFormIfNeeded() {
        if (adId != null || formRestored) return
        val cached = formPersistence.load()
        if (cached != null && cached.type != 0 && cached.subType != 0) {
            fillFormWithAd(cached)
            formRestored = true
        }
    }

    private fun observeRealtimeEvents() {
        RealtimeEventBus.getInstance().getEvents().observe(viewLifecycleOwner) { event ->
            if (event == null || adId == null) return@observe
            if (event.type == RealtimeEvent.Type.AD_UPDATED && adId == event.entityId) {
                loadAdForEdit()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adId?.let {
            val service = WebSocketForegroundService.getInstance()
            webSocketManager = service?.getWebSocketManager()
            webSocketManager?.let { ws ->
                if (ws.isConnected()) ws.subscribeToAd(it)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        adId?.let { webSocketManager?.unsubscribeFromAd(it) }
        if (adId == null) {
            val current = collectFormData()
            if (current != null && current.type != 0 && current.subType != 0) {
                lifecycleScope.launch {
                    formPersistence.save(current)
                }
            } else {
                lifecycleScope.launch {
                    formPersistence.clear()
                }
            }
        }
    }

    override fun onDestroyView() {
        adId?.let { webSocketManager?.unsubscribeFromAd(it) }
        super.onDestroyView()
        _binding = null
    }

    private fun sendAdToServer(ad: Ad) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val result = if (adId == null) {
                adRepository.createAd(ad)
            } else {
                adRepository.updateAd(adId!!, ad)
            }
            binding.progressBar.visibility = View.GONE

            when (result) {
                is NetworkResult.Success -> {
                    val createdAd = result.data
                    val newAdId = createdAd.id.toString()
                    ToastHelper.showSuccess(requireContext(), if (adId == null) "Объявление создано" else "Объявление обновлено")
                    resetForm()
                    if (adId == null) {
                        formPersistence.clear()
                        formRestored = false
                    }
                    // Подписываемся на созданное объявление
                    webSocketManager?.subscribeToAd(newAdId)
                    adId = newAdId
                    val navController = NavHostFragment.findNavController(this@CreateAdFragment)
                    navController.popBackStack()
                    navController.navigate(R.id.navigation_my_ads)
                }
                is NetworkResult.Error -> handleError(result)
                else -> {}
            }
        }
    }

    /**
     * Показать диалог с предупреждением о найденных дубликатах
     */
    private fun showDuplicateWarning(duplicates: List<DuplicateAd>, currentAd: Ad) {
        val message = buildString {
            append("Найдены похожие объявления:\n\n")
            duplicates.forEachIndexed { index, dup ->
                append("${index + 1}. ${dup.cityName ?: "Город не указан"}, ")
                append("ЛДС: ${dup.rinkName ?: "не указан"}\n")
                dup.startTime?.let {
                    append("   Дата: ${formatDateTimeShort(it)}\n")
                }
                dup.status?.let {
                    append("   Статус: ${translateStatus(it)}\n")
                }
                append("\n")
            }
            append("Вы уверены, что хотите создать ещё одно такое объявление?")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Обнаружены дубликаты")
            .setMessage(message)
            .setPositiveButton("Создать всё равно") { _, _ ->
                sendAdToServer(currentAd)
            }
            .setNegativeButton("Отмена") { _, _ ->
                ToastHelper.showInfo(requireContext(), "Публикация отменена")
            }
            .setNeutralButton("Редактировать") { _, _ ->
                ToastHelper.showInfo(requireContext(), "Отредактируйте объявление")
            }
            .show()
    }

    /**
     * Форматирование даты и времени для отображения в диалоге
     */
    private fun formatDateTimeShort(dateTime: String): String {
        return try {
            val parts = dateTime.split("T")
            val date = parts[0].split("-")
            "${date[2]}.${date[1]}.${date[0]} ${parts[1].substring(0, 5)}"
        } catch (e: Exception) {
            dateTime
        }
    }

    /**
     * Перевод статуса на русский язык
     */
    private fun translateStatus(status: String?): String {
        return when (status) {
            "ACTIVE" -> "Активно"
            "FILLED" -> "Набрано"
            "ARCHIVED" -> "В архиве"
            "MODERATION" -> "На модерации"
            else -> status ?: "Неизвестно"
        }
    }

}