package com.katok.pro.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.katok.pro.R
import com.katok.pro.adapter.AdminAdTableAdapter
import com.katok.pro.databinding.FragmentAdminAdsBinding
import com.katok.pro.model.Ad
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.Rink
import com.katok.pro.repository.AdRepository
import com.katok.pro.repository.AdminRepository
import com.katok.pro.repository.LocationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AdminAdsFragment : BaseFragment(R.layout.fragment_admin_ads) {

    private var _binding: FragmentAdminAdsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var adminRepository: AdminRepository
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var adRepository: AdRepository

    private lateinit var adapter: AdminAdTableAdapter
    private lateinit var headerViews: List<TextView>

    // Фильтры
    private var selectedStatuses = setOf<String>()
    private var selectedTypes = setOf<Int>()
    private var selectedSubTypes = setOf<Int>()
    private var selectedCityIds = setOf<Int>()
    private var selectedRinkIds = setOf<Int>()
    private var searchQuery = ""

    // Данные для фильтров
    private val allCities = mutableListOf<City>()
    private val allRinks = mutableListOf<Rink>()

    // Пагинация
    private var currentPage = 0
    private var totalPages = 0
    private var isLoading = false
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminAdsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilters()
        setupSearch()
        loadFilterData()
        loadAds()
    }

    private fun setupRecyclerView() {
        adapter = AdminAdTableAdapter(
            requireContext(),
            onItemClick = { ad ->
                val args = Bundle().apply { putString("adId", ad.id.toString()) }
                NavHostFragment.findNavController(this)
                    .navigate(R.id.adminAdDetailFragment, args)
            },
            onLongClick = { ad -> showAdContextMenu(ad) },
            rinkCache = allRinks
        )
        headerViews = listOf(
            binding.tvHeaderStatus,
            binding.tvHeaderType,
            binding.tvHeaderCity,
            binding.tvHeaderRink,
            binding.tvHeaderDateTime,
            binding.tvHeaderAuthor,
            binding.tvHeaderPhone,
            binding.tvHeaderResponses,
            binding.tvHeaderAccepted
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if (!isLoading && visibleItemCount + firstVisibleItemPosition >= totalItemCount - 5
                    && currentPage < totalPages - 1) {
                    loadAds(nextPage = true)
                }
            }
        })

        binding.swipeRefresh.setOnRefreshListener {
            resetPagination()
            loadAds()
        }
    }

    private fun setupFilters() {
        // Статус
        val tvStatus = binding.tvStatusFilter
        val btnClearStatus = binding.btnClearStatus
        tvStatus.setOnClickListener {
            showMultiChoiceDialog(
                title = "Статусы",
                items = listOf("ACTIVE", "FILLED", "ARCHIVED", "MODERATION"),
                selected = selectedStatuses,
                onResult = { selected ->
                    selectedStatuses = selected
                    updateFilterDisplay(tvStatus, btnClearStatus, selected, "Все статусы")
                    resetPagination()
                    loadAds()
                }
            )
        }
        btnClearStatus.setOnClickListener {
            selectedStatuses = emptySet()
            updateFilterDisplay(tvStatus, btnClearStatus, emptySet<String>(), "Все статусы")
            resetPagination()
            loadAds()
        }

        // Тип
        val typeOptions = listOf(
            "Нужен вратарь" to (1 to 1),
            "Нужен полевой" to (1 to 2),
            "Ищу лёд (вратарь)" to (2 to 1),
            "Ищу лёд (полевой)" to (2 to 2),
            "Ищу товарищеский матч" to (3 to 1),
            "Предлагаю товарищеский матч" to (3 to 2),
            "Нужен судья" to (4 to 1),
            "Нужен фотограф" to (4 to 2),
            "Нужен медик" to (4 to 3),
            "Нужен тренер" to (4 to 4)
        )
        val typeNames = typeOptions.map { it.first }
        val tvType = binding.tvTypeFilter
        val btnClearType = binding.btnClearType
        tvType.setOnClickListener {
            showMultiChoiceDialog(
                title = "Типы объявлений",
                items = typeNames,
                selected = selectedTypes.map { type: Int ->
                    typeOptions.find { it.second.first == type }?.first ?: ""
                }.toSet(),
                onResult = { selectedNames ->
                    val selected = typeOptions.filter { it.first in selectedNames }.map { it.second }
                    selectedTypes = selected.map { it.first }.toSet()
                    selectedSubTypes = selected.map { it.second }.toSet()
                    updateFilterDisplay(tvType, btnClearType, selectedNames, "Все типы")
                    resetPagination()
                    loadAds()
                }
            )
        }
        btnClearType.setOnClickListener {
            selectedTypes = emptySet()
            selectedSubTypes = emptySet()
            updateFilterDisplay(tvType, btnClearType, emptySet<String>(), "Все типы")
            resetPagination()
            loadAds()
        }

        // Город
        val tvCity = binding.tvCityFilter
        val btnClearCity = binding.btnClearCity
        tvCity.setOnClickListener {
            if (allCities.isEmpty()) {
                Toast.makeText(requireContext(), "Загрузка городов...", Toast.LENGTH_SHORT).show()
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
                updateFilterDisplay(tvCity, btnClearCity, selectedNames, "Все города")
                resetPagination()
                loadAds()
            }.show()
        }
        btnClearCity.setOnClickListener {
            selectedCityIds = emptySet()
            updateFilterDisplay(tvCity, btnClearCity, emptySet<String>(), "Все города")
            resetPagination()
            loadAds()
        }

        // Стадион
        val tvRink = binding.tvRinkFilter
        val btnClearRink = binding.btnClearRink
        tvRink.setOnClickListener {
            if (allRinks.isEmpty()) {
                Toast.makeText(requireContext(), "Загрузка стадионов...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val rinkNames = allRinks.map { it.name ?: "" }
            showMultiChoiceDialog(
                title = "Стадионы",
                items = rinkNames,
                selected = selectedRinkIds.map { id: Int -> allRinks.find { it.id == id }?.name ?: "" }.toSet(),
                onResult = { selectedNames ->
                    selectedRinkIds = allRinks.filter { it.name in selectedNames }.map { it.id }.toSet()
                    updateFilterDisplay(tvRink, btnClearRink, selectedNames, "Все стадионы")
                    resetPagination()
                    loadAds()
                }
            )
        }
        btnClearRink.setOnClickListener {
            selectedRinkIds = emptySet()
            updateFilterDisplay(tvRink, btnClearRink, emptySet<String>(), "Все стадионы")
            resetPagination()
            loadAds()
        }
    }

    private fun updateFilterDisplay(tv: TextView, btnClear: ImageView, selected: Set<*>, defaultText: String) {
        tv.text = if (selected.isEmpty()) defaultText else selected.joinToString(", ")
        btnClear.isVisible = selected.isNotEmpty()
    }

    private fun showMultiChoiceDialog(
        title: String,
        items: List<String>,
        selected: Set<String>,
        onResult: (Set<String>) -> Unit
    ) {
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

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchQuery = s.toString().trim()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    resetPagination()
                    loadAds()
                }
            }
        })
    }

    private fun loadFilterData() {
        lifecycleScope.launch {
            val citiesResult = locationRepository.getAllCitiesByCountry(1)
            if (citiesResult is NetworkResult.Success) {
                allCities.clear()
                allCities.addAll(citiesResult.data)
            } else if (citiesResult is NetworkResult.Error) {
                handleError(citiesResult)
            }
            val rinksResult = adminRepository.getAdminRinks()
            if (rinksResult is NetworkResult.Success) {
                allRinks.clear()
                allRinks.addAll(rinksResult.data)
                adapter.updateRinks(allRinks)
                adapter.recalculateWidths()
                if (::headerViews.isInitialized) {
                    adapter.updateHeaderWidths(headerViews)
                }
            } else if (rinksResult is NetworkResult.Error) {
                handleError(rinksResult)
            }
        }
    }

    private fun resetPagination() {
        currentPage = 0
        totalPages = 0
        adapter.submitList(emptyList())
    }

    private fun loadAds(nextPage: Boolean = false) {
        if (isLoading) return
        val page = if (nextPage) currentPage + 1 else 0
        if (!nextPage) {
            binding.progressBar.visibility = View.VISIBLE
            currentPage = 0
        }

        isLoading = true
        binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            val result = adminRepository.getAdminAds(
                status = selectedStatuses.toList().takeIf { it.isNotEmpty() },
                type = selectedTypes.toList().takeIf { it.isNotEmpty() },
                subType = selectedSubTypes.toList().takeIf { it.isNotEmpty() },
                cityId = selectedCityIds.toList().takeIf { it.isNotEmpty() },
                rinkId = selectedRinkIds.toList().takeIf { it.isNotEmpty() },
                search = searchQuery.takeIf { it.isNotEmpty() },
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
                        adapter.addItems(ads)
                    } else {
                        adapter.submitList(ads)
                    }
                    adapter.updateHeaderWidths(headerViews)
                    binding.tvEmpty.visibility = if (ads.isEmpty() && page == 0) View.VISIBLE else View.GONE
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Вспомогательный диалог с поиском (используется в фильтрах)
    private fun showMultiChoiceDialogWithSearch(
        title: String,
        items: List<String>,
        selected: Set<String>,
        onResult: (Set<String>) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_multi_choice_search, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearch)
        val listView = dialogView.findViewById<ListView>(R.id.listView)

        val checkedStates = BooleanArray(items.size) { items[it] in selected }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_multiple_choice, items)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        fun syncCheckedStates() {
            for (i in 0 until listView.count) {
                val item = adapter.getItem(i) as String
                val originalIndex = items.indexOf(item)
                if (originalIndex >= 0) {
                    listView.setItemChecked(i, checkedStates[originalIndex])
                }
            }
        }

        syncCheckedStates()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                adapter.filter.filter(query)
                listView.post { syncCheckedStates() }
            }
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) as String
            val originalIndex = items.indexOf(item)
            if (originalIndex >= 0) {
                checkedStates[originalIndex] = !checkedStates[originalIndex]
                listView.setItemChecked(position, checkedStates[originalIndex])
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val result = items.filterIndexed { index, _ -> checkedStates[index] }.toSet()
                onResult(result)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAdContextMenu(ad: Ad) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        val adId = ad.id.toString()

        val isArchived = ad.status == "ARCHIVED"
        options.add(if (isArchived) "Поднять из архива" else "В архив")
        actions.add {
            AlertDialog.Builder(requireContext())
                .setTitle(if (isArchived) "Поднятие объявления" else "Архивация объявления")
                .setMessage("Вы уверены, что хотите ${if (isArchived) "поднять" else "архивировать"} это объявление?")
                .setPositiveButton("Да") { _, _ ->
                    archiveOrUnarchiveAd(adId, isArchived)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        options.add("Редактировать")
        actions.add {
            val args = Bundle().apply { putString("adId", adId) }
            NavHostFragment.findNavController(this)
                .navigate(R.id.navigation_create, args)
        }

        options.add("Удалить")
        actions.add {
            AlertDialog.Builder(requireContext())
                .setTitle("Удаление объявления")
                .setMessage("Вы уверены, что хотите удалить объявление?")
                .setPositiveButton("Удалить") { _, _ ->
                    deleteAd(adId)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Действия")
            .setItems(options.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    private fun archiveOrUnarchiveAd(adId: String, isArchived: Boolean) {
        val newStatus = if (isArchived) "ACTIVE" else "ARCHIVED"
        lifecycleScope.launch {
            val ad = Ad().apply { status = newStatus }
            val result = adRepository.updateAd(adId, ad)
            when (result) {
                is NetworkResult.Success -> {
                    Toast.makeText(requireContext(), if (isArchived) "Объявление поднято" else "Объявление архивировано", Toast.LENGTH_SHORT).show()
                    loadAds()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun deleteAd(adId: String) {
        lifecycleScope.launch {
            val result = adminRepository.deleteAd(adId)
            when (result) {
                is NetworkResult.Success -> {
                    Toast.makeText(requireContext(), "Объявление удалено", Toast.LENGTH_SHORT).show()
                    loadAds()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }
}