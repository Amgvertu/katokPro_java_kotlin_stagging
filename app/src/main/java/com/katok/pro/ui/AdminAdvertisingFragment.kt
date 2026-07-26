package com.katok.pro.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.katok.pro.R
import com.katok.pro.adapter.AdminAdvertisingTableAdapter
import com.katok.pro.databinding.FragmentAdminAdvertisingBinding
import com.katok.pro.model.Advertising
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.AdvertisingRepository
import com.katok.pro.repository.LocationRepository
import com.katok.pro.util.ToastHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.navigation.fragment.findNavController
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class AdminAdvertisingFragment : BaseFragment(R.layout.fragment_admin_advertising) {

    private var _binding: FragmentAdminAdvertisingBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var advertisingRepository: AdvertisingRepository
    @Inject lateinit var locationRepository: LocationRepository

    private lateinit var adapter: AdminAdvertisingTableAdapter
    private val allCities = mutableListOf<City>()

    // Фильтры
    private var selectedStatuses = setOf<String>()
    private var selectedCityIds = setOf<Int>()
    private var advertiserSearch = ""
    private var dateFrom: String? = null
    private var dateTo: String? = null
    private var endDateFrom: String? = null
    private var endDateTo: String? = null

    // Пагинация
    private var currentPage = 0
    private var totalPages = 0
    private var isLoading = false
    private var searchJob: Job? = null
    private lateinit var headerViews: List<TextView>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminAdvertisingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilters()
        setupSearch()
        loadCities()
        loadAdvertisements()
    }

    private fun setupRecyclerView() {
        adapter = AdminAdvertisingTableAdapter(
            context = requireContext(),
            onItemClick = { ad -> showAdDetail(ad) },
            onLongClick = { ad -> showContextMenu(ad) }
        )

        headerViews = listOf(
            binding.tvHeaderStatus,
            binding.tvHeaderAdvertiser,
            binding.tvHeaderType,
            binding.tvHeaderInterval,
            binding.tvHeaderCities,
            binding.tvHeaderPeriod,
            binding.tvHeaderStart,
            binding.tvHeaderEnd
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // Бесконечная прокрутка
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if (!isLoading && visibleItemCount + firstVisibleItemPosition >= totalItemCount - 5
                    && currentPage < totalPages - 1) {
                    loadAdvertisements(nextPage = true)
                }
            }
        })

        binding.swipeRefresh.setOnRefreshListener {
            resetPagination()
            loadAdvertisements()
        }
    }

    private fun setupFilters() {
        // Статус
        binding.tvStatusFilter.setOnClickListener {
            showMultiChoiceDialog(
                title = "Статусы",
                items = listOf("ACTIVE", "PAUSED", "EXPIRED", "DELETED"),
                selected = selectedStatuses,
                onResult = { selected ->
                    selectedStatuses = selected
                    updateFilterDisplay(binding.tvStatusFilter, binding.btnClearStatus, selected, "Все статусы")
                    resetPagination()
                    loadAdvertisements()
                }
            )
        }
        binding.btnClearStatus.setOnClickListener {
            selectedStatuses = emptySet()
            updateFilterDisplay(binding.tvStatusFilter, binding.btnClearStatus, emptySet<String>(), "Все статусы")
            resetPagination()
            loadAdvertisements()
        }

        // Город
        binding.tvCityFilter.setOnClickListener {
            if (allCities.isEmpty()) {
                ToastHelper.showInfo(requireContext(), "Загрузка городов...")
                return@setOnClickListener
            }
            val cityNames = allCities.map { it.name ?: "" }
            val currentSelected = selectedCityIds.mapNotNull { id ->
                allCities.find { it.id == id }?.name
            }.toSet()

            MultiChoiceWithSearchDialog(
                requireContext(),
                "Выберите города",
                cityNames,
                currentSelected
            ) { selectedNames ->
                selectedCityIds = allCities.filter { it.name in selectedNames }.map { it.id }.toSet()
                updateFilterDisplay(binding.tvCityFilter, binding.btnClearCity, selectedNames, "Все города")
                resetPagination()
                loadAdvertisements()
            }.show()
        }
        binding.btnClearCity.setOnClickListener {
            selectedCityIds = emptySet()
            updateFilterDisplay(binding.tvCityFilter, binding.btnClearCity, emptySet<String>(), "Все города")
            resetPagination()
            loadAdvertisements()
        }

        // Дата размещения (диапазон)
        binding.tvDateFrom.setOnClickListener { showDatePicker(binding.tvDateFrom, true) }
        binding.tvDateTo.setOnClickListener { showDatePicker(binding.tvDateTo, false) }
        binding.btnClearDate.setOnClickListener {
            dateFrom = null
            dateTo = null
            binding.tvDateFrom.text = "Дата от"
            binding.tvDateTo.text = "Дата до"
            binding.btnClearDate.isVisible = false
            resetPagination()
            loadAdvertisements()
        }

        // Дата окончания (диапазон)
        binding.tvEndDateFrom.setOnClickListener { showDatePicker(binding.tvEndDateFrom, true, isEndDate = true) }
        binding.tvEndDateTo.setOnClickListener { showDatePicker(binding.tvEndDateTo, false, isEndDate = true) }
        binding.btnClearEndDate.setOnClickListener {
            endDateFrom = null
            endDateTo = null
            binding.tvEndDateFrom.text = "Окончание от"
            binding.tvEndDateTo.text = "Окончание до"
            binding.btnClearEndDate.isVisible = false
            resetPagination()
            loadAdvertisements()
        }

        // Кнопка "Добавить рекламу"
        binding.btnAdd.setOnClickListener {
            showCreateAdvertisingDialog()
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                advertiserSearch = s.toString().trim()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    resetPagination()
                    loadAdvertisements()
                }
            }
        })
    }

    private fun loadCities() {
        lifecycleScope.launch {
            val result = locationRepository.getAllCitiesByCountry(1)
            if (result is NetworkResult.Success) {
                allCities.clear()
                allCities.addAll(result.data)
                adapter.updateCities(allCities)
                adapter.updateHeaderWidths(headerViews) // <-- добавить
            }
        }
    }

    private fun resetPagination() {
        currentPage = 0
        totalPages = 0
        adapter.submitList(emptyList())
    }

    private fun loadAdvertisements(nextPage: Boolean = false) {
        if (isLoading) return
        val page = if (nextPage) currentPage + 1 else 0
        if (!nextPage) {
            binding.progressBar.visibility = View.VISIBLE
            currentPage = 0
        }

        isLoading = true
        binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            val result = advertisingRepository.getAdminAdvertisements(
                status = selectedStatuses.toList().takeIf { it.isNotEmpty() },
                advertiser = advertiserSearch.takeIf { it.isNotEmpty() },
                cityIds = selectedCityIds.toList().takeIf { it.isNotEmpty() },
                dateFrom = dateFrom,
                dateTo = dateTo,
                endDateFrom = endDateFrom,
                endDateTo = endDateTo,
                page = page,
                size = 20,
                sort = "createdAt,desc"
            )
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            isLoading = false

            when (result) {
                is NetworkResult.Success -> {
                    val ads = result.data.content ?: emptyList()
                    totalPages = result.data.totalPages
                    currentPage = page
                    if (nextPage) {
                        adapter.addItems(ads as List<Advertising>)
                        adapter.updateHeaderWidths(headerViews)
                    } else {
                        adapter.submitList(ads)
                        adapter.updateHeaderWidths(headerViews)
                    }
                    binding.tvEmpty.visibility = if (ads.isEmpty() && page == 0) View.VISIBLE else View.GONE
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    // Вспомогательные функции для фильтров
    private fun showMultiChoiceDialog(title: String, items: List<String>, selected: Set<String>, onResult: (Set<String>) -> Unit) {
        val checkedItems = BooleanArray(items.size) { items[it] in selected }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMultiChoiceItems(items.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                val result = items.filterIndexed { index, _ -> checkedItems[index] }.toSet()
                onResult(result)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateFilterDisplay(tv: TextView, btnClear: View, selected: Set<*>, defaultText: String) {
        tv.text = if (selected.isEmpty()) defaultText else selected.joinToString(", ")
        btnClear.visibility = if (selected.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDatePicker(textView: TextView, isStart: Boolean, isEndDate: Boolean = false) {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH)
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Выберите дату")
            .setPositiveButton("OK") { _, _ ->
                // Здесь нужно использовать DatePickerDialog, но для простоты используем готовый
            }
            .show()

        // Используем стандартный DatePickerDialog
        android.app.DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val date = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                textView.text = date
                if (isEndDate) {
                    if (isStart) endDateFrom = date else endDateTo = date
                    binding.btnClearEndDate.isVisible = endDateFrom != null || endDateTo != null
                } else {
                    if (isStart) dateFrom = date else dateTo = date
                    binding.btnClearDate.isVisible = dateFrom != null || dateTo != null
                }
                resetPagination()
                loadAdvertisements()
            },
            year, month, day
        ).show()
    }

    // Контекстное меню
    private fun showContextMenu(ad: Advertising) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // Редактировать
        options.add("Редактировать")
        actions.add {
            val bundle = Bundle().apply { putString("adId", ad.id) }
            findNavController().navigate(R.id.createAdvertisingFragment, bundle)
        }

        // Приостановить / Возобновить
        val isPaused = ad.status == "PAUSED"
        options.add(if (isPaused) "Возобновить" else "Приостановить")
        actions.add {
            val newStatus = if (isPaused) "ACTIVE" else "PAUSED"
            updateStatus(ad.id!!, newStatus)
        }

        // Удалить
        options.add("Удалить")
        actions.add { deleteAdvertising(ad.id!!) }

        AlertDialog.Builder(requireContext())
            .setTitle("Действия")
            .setItems(options.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    private fun updateStatus(id: String, status: String) {
        lifecycleScope.launch {
            val result = advertisingRepository.updateAdvertisingStatus(id, status)
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Статус изменён")
                    loadAdvertisements()
                }
                is NetworkResult.Error -> handleError(result)
                else -> {}
            }
        }
    }

    private fun deleteAdvertising(id: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удаление рекламы")
            .setMessage("Вы уверены, что хотите удалить эту рекламу?")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    val result = advertisingRepository.deleteAdvertising(id)
                    when (result) {
                        is NetworkResult.Success -> {
                            ToastHelper.showSuccess(requireContext(), "Реклама удалена")
                            loadAdvertisements()
                        }
                        is NetworkResult.Error -> handleError(result)
                        else -> {}
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // Показать детали (можно пока просто открыть диалог с информацией)
    private fun formatDate(dateStr: String?): String {
        if (dateStr == null) return "-"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(dateStr)
            val output = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            date?.let { output.format(it) } ?: dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun showAdDetail(ad: Advertising) {
        // Города
        val cityNames = if (ad.allCities) {
            "Все"
        } else {
            ad.cityIds?.mapNotNull { id -> allCities.find { it.id == id }?.name }
                ?.joinToString(", ") ?: "Не указаны"
        }
        // Статус
        val status = when (ad.status) {
            "ACTIVE" -> "Активно"
            "PAUSED" -> "Приостановлено"
            "EXPIRED" -> "Истекла"
            "DELETED" -> "Удалена"
            else -> ad.status ?: ""
        }
        // Даты
        val start = formatDate(ad.startDate)
        val end = formatDate(ad.endDate)

        val message = """
        Рекламодатель: ${ad.advertiser}
        Тип: ${if (ad.type == 1) "В ленте" else "В диалоге"}
        Интервал: ${ad.interval ?: "-"}
        Период: ${ad.periodDays} дней
        Города: $cityNames
        Статус: $status
        Дата начала: $start
        Дата окончания: $end
    """.trimIndent()
        AlertDialog.Builder(requireContext())
            .setTitle("Информация о рекламе")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // Диалог создания/редактирования (пока упрощённый, можно вынести в отдельный фрагмент)
    private fun showCreateAdvertisingDialog(existingAd: Advertising? = null) {
        val bundle = Bundle()
        existingAd?.let { bundle.putString("adId", it.id) }
        findNavController().navigate(R.id.createAdvertisingFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}