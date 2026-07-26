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
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.katok.pro.R
import com.katok.pro.adapter.AdminUserTableAdapter
import com.katok.pro.databinding.FragmentAdminUsersBinding
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.User
import com.katok.pro.repository.AdminRepository
import com.katok.pro.repository.LocationRepository
import com.katok.pro.util.ToastHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AdminUserFragment : BaseFragment(R.layout.fragment_admin_users) {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var adminRepository: AdminRepository
    @Inject lateinit var locationRepository: LocationRepository

    private lateinit var adapter: AdminUserTableAdapter
    private lateinit var headerViews: List<TextView>

    // Состояния фильтров (множественный выбор)
    private var selectedRoles = setOf<String>()
    private var selectedStatuses = setOf<String>()
    private var selectedCityIds = setOf<Int>()
    private var selectedTeams = setOf<String>()
    private var searchQuery = ""

    // Данные для фильтров (списки)
    private val allCities = mutableListOf<City>()
    private val allTeams = mutableListOf<String>()

    // Пагинация
    private var currentPage = 0
    private var totalPages = 0
    private var isLoading = false

    // Поиск с debounce
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilters()
        setupSearch()
        loadFilterData()
        loadUsers()
    }

    private fun setupRecyclerView() {
        adapter = AdminUserTableAdapter(
            requireContext(),
            onItemClick = { user ->
                val args = Bundle().apply { putString("userId", user.id) }
                NavHostFragment.findNavController(this)
                    .navigate(R.id.adminUserDetailFragment, args)
            },
            onLongClick = { user -> showUserContextMenu(user) }
        )
        headerViews = listOf(
            binding.tvHeaderPhone,
            binding.tvHeaderName,
            binding.tvHeaderRole,
            binding.tvHeaderStatus,
            binding.tvHeaderEmail,
            binding.tvHeaderTeam
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // Бесконечная прокрутка
        binding.recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if (!isLoading && visibleItemCount + firstVisibleItemPosition >= totalItemCount - 5
                    && currentPage < totalPages - 1) {
                    loadUsers(nextPage = true)
                }
            }
        })

        binding.swipeRefresh.setOnRefreshListener {
            resetPagination()
            loadUsers()
        }
    }

    private fun setupFilters() {
        // --- Роль ---
        val tvRole = binding.tvRoleFilter
        val btnClearRole = binding.btnClearRole
        tvRole.setOnClickListener {
            showMultiChoiceDialog(
                title = "Роли",
                items = listOf("USER", "ADMIN", "MODERATOR"),
                selected = selectedRoles,
                onResult = { selected ->
                    selectedRoles = selected
                    updateFilterDisplay(tvRole, btnClearRole, selected, "Все роли")
                    resetPagination()
                    loadUsers()
                }
            )
        }
        btnClearRole.setOnClickListener {
            selectedRoles = emptySet()
            updateFilterDisplay(tvRole, btnClearRole, emptySet<String>(), "Все роли")
            resetPagination()
            loadUsers()
        }

        // --- Статус ---
        val tvStatus = binding.tvStatusFilter
        val btnClearStatus = binding.btnClearStatus
        tvStatus.setOnClickListener {
            showMultiChoiceDialog(
                title = "Статусы",
                items = listOf("ACTIVE", "BLOCKED"),
                selected = selectedStatuses,
                onResult = { selected ->
                    selectedStatuses = selected
                    updateFilterDisplay(tvStatus, btnClearStatus, selected, "Все статусы")
                    resetPagination()
                    loadUsers()
                }
            )
        }
        btnClearStatus.setOnClickListener {
            selectedStatuses = emptySet()
            updateFilterDisplay(tvStatus, btnClearStatus, emptySet<String>(), "Все статусы")
            resetPagination()
            loadUsers()
        }

        // --- Город ---
        val tvCity = binding.tvCityFilter
        val btnClearCity = binding.btnClearCity
        tvCity.setOnClickListener {
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
                updateFilterDisplay(tvCity, btnClearCity, selectedNames, "Все города")
                resetPagination()
                loadUsers()
            }.show()
        }
        btnClearCity.setOnClickListener {
            selectedCityIds = emptySet()
            updateFilterDisplay(tvCity, btnClearCity, emptySet<String>(), "Все города")
            resetPagination()
            loadUsers()
        }

        // --- Команда ---
        val tvTeam = binding.tvTeamFilter
        val btnClearTeam = binding.btnClearTeam
        tvTeam.setOnClickListener {
            if (allTeams.isEmpty()) {
                ToastHelper.showInfo(requireContext(), "Загрузка команд...")
                return@setOnClickListener
            }
            showMultiChoiceDialog(
                title = "Команды",
                items = allTeams,
                selected = selectedTeams,
                onResult = { selected ->
                    selectedTeams = selected
                    updateFilterDisplay(tvTeam, btnClearTeam, selected, "Все команды")
                    resetPagination()
                    loadUsers()
                }
            )
        }
        btnClearTeam.setOnClickListener {
            selectedTeams = emptySet()
            updateFilterDisplay(tvTeam, btnClearTeam, emptySet<String>(), "Все команды")
            resetPagination()
            loadUsers()
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
                    loadUsers()
                }
            }
        })
    }

    private fun loadFilterData() {
        lifecycleScope.launch {
            // Города
            val citiesResult = locationRepository.getAllCitiesByCountry(1)
            if (citiesResult is NetworkResult.Success) {
                allCities.clear()
                allCities.addAll(citiesResult.data)
            }
            // Команды
            val teamsResult = adminRepository.getTeams()
            if (teamsResult is NetworkResult.Success) {
                allTeams.clear()
                allTeams.addAll(teamsResult.data)
            } else {
                // fallback: извлечь из пользователей (но пока нет данных)
            }
        }
    }

    private fun resetPagination() {
        currentPage = 0
        totalPages = 0
        adapter.submitList(emptyList())
    }

    private fun loadUsers(nextPage: Boolean = false) {
        if (isLoading) return
        val page = if (nextPage) currentPage + 1 else 0
        if (!nextPage) {
            binding.progressBar.visibility = View.VISIBLE
            currentPage = 0
        }

        isLoading = true
        binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            val result = adminRepository.getAdminUsers(
                role = selectedRoles.toList().takeIf { it.isNotEmpty() },
                status = selectedStatuses.toList().takeIf { it.isNotEmpty() },
                cityId = selectedCityIds.toList().takeIf { it.isNotEmpty() },
                team = selectedTeams.toList().takeIf { it.isNotEmpty() },
                search = searchQuery.takeIf { it.isNotEmpty() },
                page = page,
                size = 20,
                sort = "registeredAt,desc"
            )
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            isLoading = false

            when (result) {
                is NetworkResult.Success -> {
                    val users = result.data.content ?: emptyList()
                    totalPages = result.data.totalPages
                    currentPage = page
                    if (nextPage) {
                        adapter.addItems(users)
                    } else {
                        adapter.submitList(users)
                    }
                    adapter.updateHeaderWidths(headerViews)
                    binding.tvEmpty.visibility = if (users.isEmpty() && page == 0) View.VISIBLE else View.GONE
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

    private fun showMultiChoiceDialogWithSearch(
        title: String,
        items: List<String>,
        selected: Set<String>,
        onResult: (Set<String>) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_multi_choice_search, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearch)
        val listView = dialogView.findViewById<ListView>(R.id.listView)

        // Массив состояний (галочек) для каждого элемента исходного списка
        val checkedStates = BooleanArray(items.size) { items[it] in selected }

        // Адаптер для отображения списка с фильтром
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_multiple_choice, items)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        // Функция для синхронизации состояний с текущим отображаемым списком
        fun syncCheckedStates() {
            for (i in 0 until listView.count) {
                val item = adapter.getItem(i) as String
                val originalIndex = items.indexOf(item)
                if (originalIndex >= 0) {
                    listView.setItemChecked(i, checkedStates[originalIndex])
                }
            }
        }

        // Первоначальная установка состояний
        syncCheckedStates()

        // Слушатель изменения поиска
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                adapter.filter.filter(query)
                // После фильтрации синхронизируем состояния
                listView.post { syncCheckedStates() }
            }
        })

        // Слушатель клика по элементу – переключаем состояние в массиве
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) as String
            val originalIndex = items.indexOf(item)
            if (originalIndex >= 0) {
                checkedStates[originalIndex] = !checkedStates[originalIndex]
                // Обновляем отображение для этой позиции
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

    private fun showUserContextMenu(user: User) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // Блокировка/Разблокировка
        val isActive = user.status == "ACTIVE"
        options.add(if (isActive) "Заблокировать" else "Разблокировать")
        actions.add {
            AlertDialog.Builder(requireContext())
                .setTitle(if (isActive) "Блокировка пользователя" else "Разблокировка пользователя")
                .setMessage("Вы уверены, что хотите ${if (isActive) "заблокировать" else "разблокировать"} пользователя?")
                .setPositiveButton("Да") { _, _ ->
                    val newStatus = if (isActive) "BLOCKED" else "ACTIVE"
                    changeUserStatus(user.id ?: "", newStatus)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        // Редактировать
        options.add("Редактировать")
        actions.add {
            val args = Bundle().apply { putString("userId", user.id) }
            NavHostFragment.findNavController(this)
                .navigate(R.id.adminUserDetailFragment, args)
        }

        // Удалить
        options.add("Удалить")
        actions.add {
            AlertDialog.Builder(requireContext())
                .setTitle("Удаление пользователя")
                .setMessage("Вы уверены, что хотите удалить пользователя?")
                .setPositiveButton("Удалить") { _, _ ->
                    deleteUser(user.id ?: "")
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Действия")
            .setItems(options.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    private fun changeUserStatus(userId: String, newStatus: String) {
        lifecycleScope.launch {
            val result = adminRepository.changeUserStatus(userId, newStatus)
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Статус изменён")
                    loadUsers()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {} // Loading – ничего не делаем
            }
        }
    }

    private fun deleteUser(userId: String) {
        lifecycleScope.launch {
            val result = adminRepository.deleteUser(userId)
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Пользователь удалён")
                    loadUsers()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }


}