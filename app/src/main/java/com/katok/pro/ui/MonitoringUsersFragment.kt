package com.katok.pro.ui.monitoring

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.katok.pro.R
import com.katok.pro.databinding.FragmentMonitoringUsersBinding
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.LocationRepository
import com.katok.pro.ui.BaseFragment
import com.katok.pro.ui.MultiChoiceWithSearchDialog
import com.katok.pro.util.ToastHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class MonitoringUsersFragment : BaseFragment(R.layout.fragment_monitoring_users) {

    private var _binding: FragmentMonitoringUsersBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var locationRepository: LocationRepository

    private lateinit var viewModel: MonitoringUsersViewModel
    private val allCities = mutableListOf<City>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonitoringUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[MonitoringUsersViewModel::class.java]

        setupObservers()
        setupListeners()
        loadCities()
        viewModel.loadStatistics()
    }

    private fun setupObservers() {
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                binding.tvCurrentUsers.text = it.currentUsers.toString()
                binding.tvCumulativeUsers.text = it.cumulativeUsers.toString()
            } ?: run {
                binding.tvCurrentUsers.text = "0"
                binding.tvCumulativeUsers.text = "0"
            }
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = loading
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.tvError.text = error
                binding.tvError.visibility = View.VISIBLE
            } else {
                binding.tvError.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        // Кнопка фильтра – открывает/закрывает панель
        binding.btnFilter.setOnClickListener {
            val visible = binding.filterPanel.visibility != View.VISIBLE
            binding.filterPanel.visibility = if (visible) View.VISIBLE else View.GONE
        }

        // Обновление по свайпу
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadStatistics()
        }

        // Настройка выпадающего списка для периода
        val periodAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.period_options)
        )
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPeriod.adapter = periodAdapter

        binding.spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { // Сегодня
                        viewModel.setPeriod("today")
                        binding.tvDateFrom.text = "Дата от"
                        binding.tvDateTo.text = "Дата до"
                    }
                    1 -> { // Месяц
                        viewModel.setPeriod("month")
                        binding.tvDateFrom.text = "Дата от"
                        binding.tvDateTo.text = "Дата до"
                    }
                    2 -> { // Год
                        viewModel.setPeriod("year")
                        binding.tvDateFrom.text = "Дата от"
                        binding.tvDateTo.text = "Дата до"
                    }
                    3 -> { // Всё время
                        viewModel.setDateRange(null, null)
                        binding.tvDateFrom.text = "Дата от"
                        binding.tvDateTo.text = "Дата до"
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Выбор даты от/до
        binding.tvDateFrom.setOnClickListener { showDatePicker(binding.tvDateFrom, true) }
        binding.tvDateTo.setOnClickListener { showDatePicker(binding.tvDateTo, false) }

        // Города
        binding.tvCityFilter.setOnClickListener {
            showCityMultiChoiceDialog()
        }
        binding.btnClearCity.setOnClickListener {
            viewModel.setCityIds(emptyList())
            binding.tvCityFilter.text = "Все города"
            binding.btnClearCity.visibility = View.GONE
        }

        // Амплуа
        binding.tvPositionFilter.setOnClickListener {
            showPositionMultiChoiceDialog()
        }
        binding.btnClearPosition.setOnClickListener {
            viewModel.setPositions(emptyList())
            binding.tvPositionFilter.text = "Все амплуа"
            binding.btnClearPosition.visibility = View.GONE
        }

        // Сброс всех фильтров
        binding.btnClearAll.setOnClickListener {
            viewModel.setCityIds(emptyList())
            viewModel.setPositions(emptyList())
            viewModel.setDateRange(null, null)
            binding.tvCityFilter.text = "Все города"
            binding.btnClearCity.visibility = View.GONE
            binding.tvPositionFilter.text = "Все амплуа"
            binding.btnClearPosition.visibility = View.GONE
            binding.tvDateFrom.text = "Дата от"
            binding.tvDateTo.text = "Дата до"
            viewModel.loadStatistics()
        }
    }

    private fun loadCities() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = locationRepository.getAllCitiesByCountry(1)
            if (result is NetworkResult.Success) {
                allCities.clear()
                allCities.addAll(result.data)
            }
        }
    }

    private fun showDatePicker(textView: TextView, isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val date = String.format("%04d-%02d-%02d", year, month + 1, day)
                textView.text = date
                val from = if (isStart) date else viewModel.dateFrom.value
                val to = if (!isStart) date else viewModel.dateTo.value
                viewModel.setDateRange(from, to)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showCityMultiChoiceDialog() {
        if (allCities.isEmpty()) {
            ToastHelper.showInfo(requireContext(), "Загрузка городов...")
            return
        }
        val cityNames = allCities.map { it.name ?: "" }
        val currentSelected = viewModel.selectedCityIds.value?.mapNotNull { id ->
            allCities.find { it.id == id }?.name
        }?.toSet() ?: emptySet()

        MultiChoiceWithSearchDialog(
            requireContext(),
            "Выберите города",
            cityNames,
            currentSelected
        ) { selectedNames ->
            val ids = allCities.filter { it.name in selectedNames }.map { it.id }
            viewModel.setCityIds(ids)
            val display = if (ids.isEmpty()) "Все города" else selectedNames.joinToString(", ")
            binding.tvCityFilter.text = display
            binding.btnClearCity.visibility = if (ids.isEmpty()) View.GONE else View.VISIBLE
        }.show()
    }

    private fun showPositionMultiChoiceDialog() {
        val positions = arrayOf("вратарь", "защитник", "нападающий", "судья", "медик", "тренер")
        val current = viewModel.selectedPositions.value?.toMutableList() ?: mutableListOf()
        val checked = BooleanArray(positions.size) { positions[it] in current }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Выберите амплуа (можно несколько)")
            .setMultiChoiceItems(positions, checked) { _, which, isChecked ->
                if (isChecked) {
                    if (!current.contains(positions[which])) current.add(positions[which])
                } else {
                    current.remove(positions[which])
                }
            }
            .setPositiveButton("OK") { _, _ ->
                viewModel.setPositions(current)
                val display = if (current.isEmpty()) "Все амплуа" else current.joinToString(", ")
                binding.tvPositionFilter.text = display
                binding.btnClearPosition.visibility = if (current.isEmpty()) View.GONE else View.VISIBLE
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}