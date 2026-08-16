package com.katok.pro.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.katok.pro.R
import com.katok.pro.adapter.AdCardAdapter
import com.katok.pro.adapter.FeedAdapter
import com.katok.pro.databinding.FragmentMainBinding
import com.katok.pro.model.*
import com.katok.pro.network.RealtimeEventBus
import com.katok.pro.network.WebSocketManager
import com.katok.pro.network.WebSocketSubscriptionManager
import com.katok.pro.repository.AdvertisingRepository
import com.katok.pro.repository.LocationRepository
import com.katok.pro.repository.UserRepository
import com.katok.pro.services.WebSocketForegroundService
import com.katok.pro.util.ProfileHelper
import com.katok.pro.util.SessionManager
import com.katok.pro.util.ToastHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : BaseFragment(R.layout.fragment_main) {

    // Поля для вью (добавлены явно)
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    private lateinit var filterDialogHelper: FilterDialogHelper
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    private lateinit var sessionManager: SessionManager
    @Inject lateinit var advertisingRepository: AdvertisingRepository
    @Inject lateinit var locationRepository: LocationRepository
    private val cityList = mutableListOf<City>()
    private val allRinksForFilter = mutableListOf<Rink>()
    private var webSocketManager: WebSocketManager? = null
    private var currentUserId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        viewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]

        // Инициализация View-элементов
        recyclerView = view.findViewById(R.id.recyclerView)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        filterDialogHelper = FilterDialogHelper(this)

        // Создаём FeedAdapter
        val feedAdapter = FeedAdapter(
            context = requireContext(),
            adListener = createAdActionListener(),
            onAdvertClick = { advert -> onAdvertClick(advert) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = feedAdapter

        // Загружаем userId для адаптера
        viewLifecycleOwner.lifecycleScope.launch {
            val userId = sessionManager.getUserId()
            feedAdapter.setCurrentUserId(userId)
            feedAdapter.setCurrentUserPhone(sessionManager.getUserPhone())
        }

        // SwipeRefresh
        swipeRefresh.setOnRefreshListener {
            viewModel.loadAds(isRefresh = true)
        }

        // Пагинация
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!isAdded) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!viewModel.isLoading.value && !viewModel.isLoadingMore.value) {
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 5) {
                        viewModel.loadNextPage()
                    }
                }
            }
        })

        setupListeners()
        setupObservers(feedAdapter)
        loadCities()
        viewLifecycleOwner.lifecycleScope.launch {
            determineCityByPriority()
        }
        viewModel.loadAds(isRefresh = true)

        // Подписка на события реального времени
        RealtimeEventBus.getInstance().getEvents().observe(viewLifecycleOwner) { event ->
            Log.d("MainFragment", "Event received: ${event?.type}")
            event?.let { handleRealtimeEvent(it) }
        }
    }

    // --- Вспомогательный метод создания слушателя ---
    private fun createAdActionListener() = object : AdCardAdapter.OnAdActionListener {
        override fun onRespondClick(ad: Ad) { showRespondDialog(ad) }
        override fun onCancelResponseClick(responseId: String, adId: String, authorId: String) {
            showCancelResponseDialog(responseId, adId, authorId)
        }
        override fun onArchiveClick(adId: String) { archiveAd(adId) }
        override fun onEditClick(adId: String) { editAd(adId) }
        override fun onDeleteClick(adId: String) { showDeleteConfirmDialog(adId) }
        override fun onUnarchiveClick(adId: String) { viewModel.unarchiveAd(adId) }
        override fun onProfileClick(userId: String?, canShowPhone: Boolean, phone: String?) {
            navigateToProfile(userId ?: "", canShowPhone, phone)
        }
        override fun onConfirmResponseClick(responseId: String, adId: String, userId: String) {
            showConfirmResponseDialog(responseId, adId, userId)
        }
        override fun onRejectResponseClick(responseId: String, adId: String, userId: String) {
            showRejectResponseDialog(responseId, adId, userId)
        }
        override fun onCancelApprovalResponseClick(responseId: String, adId: String, userId: String) {
            showCancelApprovalResponseDialog(responseId, adId, userId)
        }
    }

    private fun setupListeners() {
        binding.btnChangeCity.setOnClickListener { showCitySelectorDialog() }
        binding.btnFilter.setOnClickListener {
            val visible = binding.filterPanel.visibility != View.VISIBLE
            binding.filterPanel.visibility = if (visible) View.VISIBLE else View.GONE
            viewModel.setFilterPanelVisible(visible)
        }
        binding.btnClearAllFilters.setOnClickListener {
            viewModel.clearAllFilters()
        }
        setupFilterClickListeners()
    }

    private fun setupObservers(feedAdapter: FeedAdapter) {
        // Наблюдаем за объединённым списком
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mergedItems.collect { items ->
                if (!isAdded || _binding == null) return@collect
                feedAdapter.submitList(items)
                updateEmptyView(items.isEmpty())
                val adIds = items.filterIsInstance<Ad>().mapNotNull { it.id?.toString() }
                val intent = Intent(requireContext(), WebSocketForegroundService::class.java)
                intent.action = "SUBSCRIBE_TO_ADS"
                intent.putStringArrayListExtra("ad_ids", ArrayList(adIds))
                requireContext().startService(intent)
            }
        }

        // Загрузка
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                if (!isAdded || _binding == null) return@collect
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                swipeRefresh.isRefreshing = isLoading
            }
        }

        // Ошибки – используем handleError из BaseFragment
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { error ->
                if (!isAdded || _binding == null) return@collect
                if (error != null) {
                    // Если ошибка содержит ключевые слова авторизации – переходим на логин
                    if (error.contains("авторизация") || error.contains("401") || error == "error_unauthorized") {
                        findNavController().navigate(R.id.loginFragment)
                    } else {
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Стадионы
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rinkList.collect { rinks ->
                if (!isAdded || _binding == null) return@collect
                feedAdapter.updateRinks(rinks)
                feedAdapter.notifyDataSetChanged()
            }
        }

        // Название города
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedCityName.collect { name ->
                if (!isAdded || _binding == null) return@collect
                binding.tvCityName.text = name
            }
        }

        // Видимость панели фильтров
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filterPanelVisible.collect { visible ->
                if (!isAdded || _binding == null) return@collect
                binding.filterPanel.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }

        // Фильтры (тип, уровень, дата, время, ЛДС)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedType.collect { updateTypeFilterDisplay() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedSubtype.collect { updateTypeFilterDisplay() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedRole.collect { updateTypeFilterDisplay() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedLevels.collect { updateLevelFilterDisplay() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.startDate.collect { updateDateFilterDisplay() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.endDate.collect { updateDateFilterDisplay() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.startTime.collect { updateTimeFilterDisplay() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.endTime.collect { updateTimeFilterDisplay() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedRinkIds.collect { updateRinkFilterDisplay() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoadingMore.collect { isLoadingMore ->
                if (!isAdded || _binding == null) return@collect
                binding.progressBarMore.visibility = if (isLoadingMore) View.VISIBLE else View.GONE
            }
        }
    }

    // -------------------- Методы обновления фильтров (без изменений) --------------------
    private fun setupFilterClickListeners() {
        binding.tvTypeValue.setOnClickListener { showTypeDialog() }
        binding.btnClearType.setOnClickListener { clearTypeFilter() }
        binding.tvLevelValue.setOnClickListener { showLevelDialog() }
        binding.btnClearLevel.setOnClickListener { clearLevelFilter() }
        binding.tvDateValue.setOnClickListener { showDateRangeDialog() }
        binding.btnClearDate.setOnClickListener { clearDateFilter() }
        binding.tvTimeValue.setOnClickListener { showTimeDialog() }
        binding.btnClearTime.setOnClickListener { clearTimeFilter() }
        binding.tvRinkValue.setOnClickListener { showRinkDialog() }
        binding.btnClearRink.setOnClickListener { clearRinkFilter() }
    }

    private fun updateTypeFilterDisplay() {
        val type = viewModel.selectedType.value
        val subtype = viewModel.selectedSubtype.value
        val role = viewModel.selectedRole.value
        if (type == 0 && subtype == 0 && role == null) {
            binding.tvTypeValue.text = "Все"
            binding.btnClearType.visibility = View.GONE
        } else {
            var display = getTypeDisplay(type, subtype)
            if (role != null) {
                display += if (role == "DEFENDER") " (Защитники)" else " (Нападающие)"
            }
            binding.tvTypeValue.text = display
            binding.btnClearType.visibility = View.VISIBLE
        }
    }

    private fun updateLevelFilterDisplay() {
        val levels = viewModel.selectedLevels.value
        if (levels.isEmpty()) {
            binding.tvLevelValue.text = "Любой"
            binding.btnClearLevel.visibility = View.GONE
        } else {
            binding.tvLevelValue.text = TextUtils.join(", ", levels)
            binding.btnClearLevel.visibility = View.VISIBLE
        }
    }

    private fun updateDateFilterDisplay() {
        val start = viewModel.startDate.value
        val end = viewModel.endDate.value
        if (start == null && end == null) {
            binding.tvDateValue.text = "Любая"
            binding.btnClearDate.visibility = View.GONE
        } else {
            var display = ""
            when {
                start != null && end != null -> display = "${formatDisplayDate(start)} – ${formatDisplayDate(end)}"
                start != null -> display = "с ${formatDisplayDate(start)}"
                end != null -> display = "до ${formatDisplayDate(end)}"
            }
            binding.tvDateValue.text = display
            binding.btnClearDate.visibility = View.VISIBLE
        }
    }

    private fun updateTimeFilterDisplay() {
        val start = viewModel.startTime.value
        val end = viewModel.endTime.value
        if (start.isNullOrEmpty() && end.isNullOrEmpty()) {
            binding.tvTimeValue.text = "Любое"
            binding.btnClearTime.visibility = View.GONE
        } else {
            var display = ""
            when {
                !start.isNullOrEmpty() && !end.isNullOrEmpty() -> display = "$start – $end"
                !start.isNullOrEmpty() -> display = "с $start"
                !end.isNullOrEmpty() -> display = "до $end"
            }
            binding.tvTimeValue.text = display
            binding.btnClearTime.visibility = View.VISIBLE
        }
    }

    private fun updateRinkFilterDisplay() {
        val ids = viewModel.selectedRinkIds.value
        if (ids.isEmpty()) {
            binding.tvRinkValue.text = "Все ЛДС"
            binding.btnClearRink.visibility = View.GONE
        } else {
            binding.tvRinkValue.text = "Выбрано: ${ids.size}"
            binding.btnClearRink.visibility = View.VISIBLE
        }
    }

    private fun getTypeDisplay(category: Int, subtype: Int): String {
        if (category == 0) return "Все"
        if (subtype == 0) {
            return when (category) {
                1 -> "Ищу игрока"
                2 -> "Ищу лёд"
                3 -> "Товарищеский матч"
                4 -> "Ищу специалиста"
                else -> ""
            }
        }
        if (category == 1) return if (subtype == 1) "Нужен вратарь" else "Нужен полевой"
        if (category == 2) return if (subtype == 1) "Ищу лёд (вратарь)" else "Ищу лёд (полевой)"
        if (category == 3) return if (subtype == 1) "Ищу товарищеский матч" else "Предлагаю товарищеский матч"
        val spec = arrayOf("Судья", "Фотограф", "Медик", "Тренер")
        return "Нужен " + spec[subtype - 1]
    }

    private fun formatDisplayDate(yyyyMMdd: String): String {
        val parts = yyyyMMdd.split("-")
        return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else yyyyMMdd
    }

    // -------------------- Диалоги фильтров (без изменений) --------------------
    private fun showTypeDialog() {
        filterDialogHelper.showTypeDialog { category, subtype, role ->
            viewModel.setSelectedType(category)
            viewModel.setSelectedSubtype(subtype)
            viewModel.setSelectedRole(role)
        }
    }

    private fun clearTypeFilter() {
        viewModel.setSelectedType(0)
        viewModel.setSelectedSubtype(0)
        viewModel.setSelectedRole(null)
    }

    private fun showLevelDialog() {
        val levels = arrayOf("A", "B", "C", "D", "E", "F", "G", "H")
        val current = viewModel.selectedLevels.value.toMutableList()
        val checked = BooleanArray(8) { i -> current.contains(levels[i]) }
        AlertDialog.Builder(requireContext())
            .setTitle("Выберите уровень (можно несколько)")
            .setMultiChoiceItems(levels, checked) { _, which, isChecked ->
                if (isChecked) {
                    if (!current.contains(levels[which])) current.add(levels[which])
                } else {
                    current.remove(levels[which])
                }
            }
            .setPositiveButton("OK") { _, _ -> viewModel.setSelectedLevels(current) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearLevelFilter() {
        viewModel.setSelectedLevels(emptyList())
    }

    private fun showDateRangeDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_date_range, null)
        val tvStart = view.findViewById<TextView>(R.id.tvStartDate)
        val tvEnd = view.findViewById<TextView>(R.id.tvEndDate)

        tvStart.setOnClickListener { showDatePicker(tvStart, true) }
        tvEnd.setOnClickListener { showDatePicker(tvEnd, false) }

        viewModel.startDate.value?.let { tvStart.text = formatDisplayDate(it) }
        viewModel.endDate.value?.let { tvEnd.text = formatDisplayDate(it) }

        AlertDialog.Builder(requireContext())
            .setTitle("Выберите диапазон дат")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val start = parseDate(tvStart.text.toString())
                val end = parseDate(tvEnd.text.toString())
                viewModel.setStartDate(start)
                viewModel.setEndDate(end)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDatePicker(textView: TextView, isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val date = String.format("%04d-%02d-%02d", year, month + 1, day)
                textView.text = formatDisplayDate(date)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun parseDate(display: String?): String? {
        if (display.isNullOrEmpty()) return null
        val parts = display.split("\\.".toRegex())
        return if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else null
    }

    private fun clearDateFilter() {
        viewModel.setStartDate(null)
        viewModel.setEndDate(null)
    }

    private fun showTimeDialog() {
        val container = LinearLayout(requireContext())
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(40, 20, 40, 20)

        val tvFromLabel = TextView(requireContext())
        tvFromLabel.text = "Время от:"
        tvFromLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        container.addView(tvFromLabel)

        val etTimeFrom = EditText(requireContext())
        etTimeFrom.hint = "HH:MM"
        etTimeFrom.inputType = android.text.InputType.TYPE_CLASS_DATETIME
        etTimeFrom.isFocusable = false
        etTimeFrom.isClickable = true
        etTimeFrom.setOnClickListener { showTimePicker(etTimeFrom) }
        viewModel.startTime.value?.let { etTimeFrom.setText(it) }
        container.addView(etTimeFrom)

        val tvToLabel = TextView(requireContext())
        tvToLabel.text = "Время до:"
        tvToLabel.setPadding(0, 16, 0, 0)
        tvToLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        container.addView(tvToLabel)

        val etTimeTo = EditText(requireContext())
        etTimeTo.hint = "HH:MM"
        etTimeTo.inputType = android.text.InputType.TYPE_CLASS_DATETIME
        etTimeTo.isFocusable = false
        etTimeTo.isClickable = true
        etTimeTo.setOnClickListener { showTimePicker(etTimeTo) }
        viewModel.endTime.value?.let { etTimeTo.setText(it) }
        container.addView(etTimeTo)

        AlertDialog.Builder(requireContext())
            .setTitle("Выберите интервал времени")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val start = etTimeFrom.text.toString().trim().takeIf { it.isNotEmpty() }
                val end = etTimeTo.text.toString().trim().takeIf { it.isNotEmpty() }
                viewModel.setStartTime(start)
                viewModel.setEndTime(end)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showTimePicker(editText: EditText) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                editText.setText(String.format("%02d:%02d", hour, minute))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun clearTimeFilter() {
        viewModel.setStartTime(null)
        viewModel.setEndTime(null)
    }

    private fun showRinkDialog() {
        if (allRinksForFilter.isEmpty()) {
            Toast.makeText(requireContext(), "Сначала выберите город", Toast.LENGTH_SHORT).show()
            return
        }

        val rinkDisplayItems = Array<CharSequence>(allRinksForFilter.size) { "" }
        for (i in allRinksForFilter.indices) {
            val rink = allRinksForFilter[i]
            val builder = SpannableStringBuilder()
            builder.append(rink.name ?: "")
            builder.setSpan(AbsoluteSizeSpan(16, true), 0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(StyleSpan(Typeface.BOLD), 0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (!rink.address.isNullOrEmpty()) {
                builder.append("\n    ")
                val start = builder.length
                builder.append(rink.address)
                builder.setSpan(AbsoluteSizeSpan(10, true), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.NORMAL), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            rinkDisplayItems[i] = builder
        }

        val checked = BooleanArray(allRinksForFilter.size)
        val selectedIds = viewModel.selectedRinkIds.value
        for (i in allRinksForFilter.indices) {
            checked[i] = selectedIds.contains(allRinksForFilter[i].id)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Выберите ЛДС (можно несколько)")
            .setMultiChoiceItems(rinkDisplayItems, checked) { _, which, isChecked ->
                val rinkId = allRinksForFilter[which].id
                val newList = selectedIds.toMutableList()
                if (isChecked) {
                    if (!newList.contains(rinkId)) newList.add(rinkId)
                } else {
                    newList.remove(rinkId)
                }
                viewModel.setSelectedRinkIds(newList)
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun clearRinkFilter() {
        viewModel.setSelectedRinkIds(emptyList())
    }

    // -------------------- Загрузка городов и определение города --------------------
    private fun loadCities() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) return@launch
            val result = locationRepository.getAllCitiesByCountry(1)
            if (result is NetworkResult.Success) {
                cityList.clear()
                cityList.addAll(result.data)
            } else if (result is NetworkResult.Error) {
                handleError(result) // используем handleError из BaseFragment
            }
        }
    }

    private suspend fun determineCityByPriority() {
        if (sessionManager.isLoggedIn()) {
            val homeCityId = sessionManager.getHomeCityId()?.toIntOrNull()
            if (homeCityId != null && homeCityId > 0) {
                viewModel.setSelectedCityId(homeCityId)
                viewModel.setSelectedCityName(sessionManager.getHomeCityName() ?: "Город")
                loadRinksForFilter(homeCityId)
                return
            }
        }
        val savedCityId = sessionManager.getSelectedCityId()
        if (savedCityId > 0) {
            viewModel.setSelectedCityId(savedCityId)
            viewModel.setSelectedCityName(sessionManager.getSelectedCityName() ?: "Город")
            loadRinksForFilter(savedCityId)
        } else {
            viewModel.setSelectedCityId(0)
            viewModel.setSelectedCityName("Все города")
        }
    }

    private fun loadRinksForFilter(cityId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) return@launch
            val result = locationRepository.getRinksByCity(cityId)
            if (result is NetworkResult.Success) {
                allRinksForFilter.clear()
                allRinksForFilter.addAll(result.data)
            } else if (result is NetworkResult.Error) {
                handleError(result)
            }
        }
    }

    private fun showCitySelectorDialog() {
        if (cityList.isEmpty()) {
            Toast.makeText(requireContext(), "Загрузка списка городов...", Toast.LENGTH_SHORT).show()
            loadCities()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_city_selector, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        val etSearch = dialogView.findViewById<EditText>(R.id.etCitySearch)
        val lvCities = dialogView.findViewById<ListView>(R.id.lvCities)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnShowAll = dialogView.findViewById<Button>(R.id.btnShowAll)
        btnShowAll.visibility = View.GONE

        etSearch.inputType = android.text.InputType.TYPE_CLASS_TEXT
        etSearch.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            val filtered = StringBuilder()
            for (c in source) {
                if (c in 'a'..'z' || c in 'A'..'Z' ||
                    c in 'а'..'я' || c in 'А'..'Я' ||
                    c == 'ё' || c == 'Ё' || c == ' ' || c == '-') {
                    filtered.append(c)
                }
            }
            filtered.toString()
        })

        val cityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, ArrayList<String>())
        lvCities.adapter = cityAdapter

        fun updateList() {
            val query = etSearch.text.toString().trim()
            val filteredNames = mutableListOf<String>().apply { add("Все города") }
            if (query.isEmpty()) {
                cityList.forEach { city ->
                    city.name?.let { filteredNames.add(it) }
                }
            } else {
                val lowerQuery = query.lowercase(Locale.getDefault())
                cityList.forEach { city ->
                    city.name?.lowercase(Locale.getDefault())?.let { nameLower ->
                        if (nameLower.contains(lowerQuery)) {
                            filteredNames.add(city.name!!)
                        }
                    }
                }
            }
            cityAdapter.clear()
            cityAdapter.addAll(filteredNames)
            cityAdapter.notifyDataSetChanged()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateList() }
            override fun afterTextChanged(s: Editable?) {}
        })

        lvCities.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                viewModel.setSelectedCityId(0)
                viewModel.setSelectedCityName("Все города")
                viewLifecycleOwner.lifecycleScope.launch {
                    sessionManager.saveSelectedCity(0, "Все города")
                }
                viewModel.loadAds(isRefresh = true)
                dialog.dismiss()
                return@setOnItemClickListener
            }
            val selectedName = cityAdapter.getItem(position)
            val city = cityList.find { it.name == selectedName }
            if (city != null) {
                viewModel.setSelectedCityId(city.id)
                viewModel.setSelectedCityName(city.name ?: "")
                viewLifecycleOwner.lifecycleScope.launch {
                    sessionManager.saveSelectedCity(city.id, city.name ?: "")
                }
                loadRinksForFilter(city.id)
                viewModel.loadAds(isRefresh = true)
                dialog.dismiss()
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
        etSearch.requestFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        updateList()
    }

    // -------------------- Обработка реальных событий --------------------
    private fun handleRealtimeEvent(event: RealtimeEvent) {
        Log.d("Realtime", "Event received: ${event.type}, entityId=${event.entityId}")

        when (event.type) {
            RealtimeEvent.Type.AD_CREATED -> {
                val newAd = event.payload as? Ad ?: return
                if (newAd.authorId == currentUserId) {
                    webSocketManager?.subscribeToAd(newAd.id.toString())
                }
                if (newAd.status == "ACTIVE" || newAd.status == "FILLED") {
                    val current = viewModel.ads.value.toMutableList()
                    if (current.none { it.id == newAd.id }) {
                        current.add(newAd)
                        viewModel.updateAds(current)
                    }
                }
            }
            RealtimeEvent.Type.AD_UPDATED -> {
                val updatedAd = event.payload as? Ad
                if (updatedAd != null) {
                    val current = viewModel.ads.value.toMutableList()
                    val index = current.indexOfFirst { it.id == updatedAd.id }
                    if (index != -1) {
                        current[index] = updatedAd
                    } else if (updatedAd.status == "ACTIVE" || updatedAd.status == "FILLED") {
                        current.add(updatedAd)
                    } else {
                        return
                    }
                    viewModel.updateAds(current)
                }
            }
            RealtimeEvent.Type.AD_DELETED -> {
                val adId = event.entityId
                if (adId != null) {
                    val current = viewModel.ads.value.toMutableList()
                    current.removeAll { it.id.toString() == adId }
                    viewModel.updateAds(current)
                }
            }
            RealtimeEvent.Type.RESPONSE_ADDED -> {
                val response = event.payload as? Response ?: return
                val adId = event.entityId ?: response.adId ?: return
                viewModel.addResponseToAd(adId, response)
            }
            RealtimeEvent.Type.RESPONSE_REMOVED -> {
                val response = event.payload as? Response ?: return
                val adId = event.entityId ?: response.adId ?: return
                val responseId = response.id ?: return
                viewModel.removeResponseFromAd(adId, responseId)
            }
            RealtimeEvent.Type.RESPONSE_APPROVED,
            RealtimeEvent.Type.RESPONSE_REJECTED,
            RealtimeEvent.Type.APPROVAL_CANCELLED,
            RealtimeEvent.Type.RESPONSE_WITHDRAWN -> {
                val response = event.payload as? Response ?: return
                val adId = event.entityId ?: response.adId ?: return
                viewModel.updateResponseInAd(adId, response)
            }
            else -> {}
        }
    }

    // -------------------- Диалоги действий с откликами --------------------
    private fun showRespondDialog(ad: Ad) {
        // Проверяем профиль
        lifecycleScope.launch {
            val userRepo = UserRepository(requireContext())
            val navController = findNavController()
            val profile = ProfileHelper.getValidProfileOrShowDialog(
                context = requireContext(),
                userRepository = userRepo,
                navController = navController
            )
            if (profile == null) {
                return@launch // профиль не заполнен – диалог уже показан
            }
            // Если профиль заполнен – продолжаем
            showRespondDialogInternal(ad)
        }
    }

    private fun showRespondDialogInternal(ad: Ad) {
        // Если объявление не "Ищу игрока, полевой" – используем старый диалог
        if (ad.type != 1 || ad.subType != 2) {
            showSimpleConfirmDialog(ad)
            return
        }

        // Определяем доступные роли
        val availableRoles = mutableListOf<Pair<String, String>>() // (role, displayName)
        val defNeeded = ad.defendersCount ?: 0
        val fwdNeeded = ad.forwardsCount ?: 0
        val defAccepted = ad.acceptedDefendersCount ?: 0
        val fwdAccepted = ad.acceptedForwardsCount ?: 0

        if (defNeeded > defAccepted) {
            availableRoles.add("DEFENDER" to "Защитник")
        }
        if (fwdNeeded > fwdAccepted) {
            availableRoles.add("FORWARD" to "Нападающий")
        }

        if (availableRoles.isEmpty()) {
            ToastHelper.showInfo(requireContext(), "Все места уже заняты")
            return
        }

        // Если доступна только одна роль – можно сразу откликнуться без диалога,
        // но для единообразия покажем диалог с одной активной кнопкой.
        // Однако по ТЗ нужно именно выбирать, если есть выбор.
        // Если только одна – сразу отправляем (без подтверждения)
        if (availableRoles.size == 1) {
            val role = availableRoles.first().first
            // Можно показать краткий Snackbar или просто отправить
            viewModel.optimisticRespondToAd(ad.id.toString(), role)
            return
        }

        // Загружаем рекламу (если есть) – как в старом коде
        lifecycleScope.launch {
            val cityId = ad.city?.id ?: ad.cityId ?: return@launch
            val result = advertisingRepository.getActiveAdvertisements(type = 2, cityId = cityId, limit = 1)
            var advert: Advertising? = null
            if (result is NetworkResult.Success && result.data.isNotEmpty()) {
                advert = result.data.first()
            }

            // Создаём диалог с кастомным списком ролей
            val dialogView = layoutInflater.inflate(R.layout.dialog_respond_with_ad, null)
            val tvMessage = dialogView.findViewById<TextView>(R.id.tvConfirmMessage)
            val ivAd = dialogView.findViewById<ImageView>(R.id.ivAd)

            if (advert != null) {
                Glide.with(this@MainFragment)
                    .load(advert.imageUrl)
                    .into(ivAd)
                ivAd.visibility = View.VISIBLE
                ivAd.setOnClickListener { onAdvertClick(advert) }
            } else {
                ivAd.visibility = View.GONE
            }

            // Текст сообщения (с учётом статуса FILLED)
            tvMessage.text = if (ad.status == "FILLED") {
                "В этом объявлении уже набрано нужное количество участников, ваш отклик будет находиться в резерве. Выберите амплуа:"
            } else {
                "Выберите амплуа, на которое вы откликаетесь:"
            }

            // Строим список ролей с пометкой доступности
            val roleNames = availableRoles.map { it.second }.toTypedArray()
            val enabledStates = BooleanArray(availableRoles.size) { true } // все доступны

            // Показываем диалог со списком (используем setItems, но он не позволяет блокировать пункты)
            // Чтобы сделать недоступные пункты серыми и некликабельными, нужно использовать кастомный адаптер.
            // Для простоты можно использовать AlertDialog с ListView и кастомным адаптером.

            // Создаём адаптер, который отображает недоступные пункты серым
            val adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, roleNames) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    // Если пункт недоступен (в нашем случае все доступны, но если бы были недоступные)
                    // Здесь можно сделать проверку, но у нас все доступны.
                    return view
                }
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Выберите амплуа")
                .setAdapter(adapter) { _, which ->
                    val role = availableRoles[which].first
                    viewModel.optimisticRespondToAd(ad.id.toString(), role)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun showCancelResponseDialog(responseId: String, adId: String, authorId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отмена отклика")
            .setMessage("Вы уверены, что хотите отменить свой отклик?")
            .setPositiveButton("Да") { _, _ -> viewModel.cancelResponse(responseId, adId, authorId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showConfirmResponseDialog(responseId: String, adId: String, userId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Подтверждение отклика")
            .setMessage("Вы уверены, что хотите принять этот отклик?")
            .setPositiveButton("Да") { _, _ -> viewModel.confirmResponse(responseId, adId, userId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRejectResponseDialog(responseId: String, adId: String, userId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отклонение отклика")
            .setMessage("Вы уверены, что хотите отклонить этот отклик?")
            .setPositiveButton("Да") { _, _ -> viewModel.rejectResponse(responseId, adId, userId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCancelApprovalResponseDialog(responseId: String, adId: String, userId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отмена подтверждения")
            .setMessage("Вы уверены, что хотите отменить подтверждение этого отклика?")
            .setPositiveButton("Да") { _, _ -> viewModel.cancelApproval(responseId, adId, userId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // -------------------- Действия с объявлениями --------------------
    private fun archiveAd(adId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Архивировать")
            .setMessage("Переместить объявление в архив?")
            .setPositiveButton("Да") { _, _ -> viewModel.archiveAd(adId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun editAd(adId: String) {
        val args = Bundle().apply { putString("adId", adId) }
        NavHostFragment.findNavController(this).navigate(R.id.navigation_create, args)
    }

    private fun showDeleteConfirmDialog(adId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить")
            .setMessage("Удалить объявление? Это действие нельзя отменить.")
            .setPositiveButton("Да") { _, _ -> deleteAd(adId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteAd(adId: String) {
        viewModel.deleteAd(adId)
    }

    private fun navigateToProfile(userId: String, canShowPhone: Boolean, phone: String?) {
        val args = Bundle().apply {
            putString("userId", userId)
            putBoolean("canShowPhone", canShowPhone)
            phone?.let { putString("phone", it) }
        }
        NavHostFragment.findNavController(this).navigate(R.id.viewProfileFragment, args)
    }

    // -------------------- Жизненный цикл --------------------
    override fun onResume() {
        super.onResume()
        val service = WebSocketForegroundService.getInstance()
        if (service != null && webSocketManager == null) {
            webSocketManager = service.getWebSocketManager()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        WebSocketSubscriptionManager.subscribeToAdIds(emptyList())
        _binding = null
    }

    // -------------------- Клик по рекламе --------------------
    private fun onAdvertClick(advert: Advertising) {
        Log.d("MainFragment", "Advert clicked: ${advert.link}")
        val link = advert.link
        if (link.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Ссылка не указана", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Переход по ссылке")
            .setMessage("Вы хотите перейти по ссылке?")
            .setPositiveButton("Перейти") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // -------------------- Вспомогательный метод обновления пустого состояния --------------------
    private fun updateEmptyView(isEmpty: Boolean) {
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            tvEmpty.text = viewModel.emptyMessage.value ?: "Нет объявлений"
        }
    }

    private fun showSimpleConfirmDialog(ad: Ad) {
        lifecycleScope.launch {
            val cityId = ad.city?.id ?: ad.cityId ?: return@launch
            val result = advertisingRepository.getActiveAdvertisements(type = 2, cityId = cityId, limit = 1)
            var advert: Advertising? = null
            if (result is NetworkResult.Success && result.data.isNotEmpty()) {
                advert = result.data.first()
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_respond_with_ad, null)
            val tvMessage = dialogView.findViewById<TextView>(R.id.tvConfirmMessage)
            val ivAd = dialogView.findViewById<ImageView>(R.id.ivAd)

            if (advert != null) {
                Glide.with(this@MainFragment)
                    .load(advert.imageUrl)
                    .into(ivAd)
                ivAd.visibility = View.VISIBLE
                ivAd.setOnClickListener { onAdvertClick(advert) }
            } else {
                ivAd.visibility = View.GONE
            }

            tvMessage.text = if (ad.status == "FILLED") {
                "В этом объявлении уже набрано нужное количество участников, ваш отклик будет находиться в резерве. Вы уверены, что хотите откликнуться?"
            } else {
                "Вы уверены, что хотите откликнуться на это объявление?"
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Подтверждение")
                .setView(dialogView)
                .setPositiveButton("Да") { _, _ ->
                    val role = if (ad.type == 1 && ad.subType == 1) "GOALIE" else null
                    viewModel.optimisticRespondToAd(ad.id.toString(), role)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }
}