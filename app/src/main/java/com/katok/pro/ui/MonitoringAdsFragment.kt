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
import com.katok.pro.databinding.FragmentMonitoringAdsBinding
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
class MonitoringAdsFragment : BaseFragment(R.layout.fragment_monitoring_ads) {

    private var _binding: FragmentMonitoringAdsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var locationRepository: LocationRepository

    private lateinit var viewModel: MonitoringAdsViewModel
    private val allCities = mutableListOf<City>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonitoringAdsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[MonitoringAdsViewModel::class.java]

        setupObservers()
        setupListeners()
        loadCities()
        viewModel.loadStatistics()
    }

    private fun setupObservers() {
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                binding.tvCurrentAds.text = it.currentAds.toString()
                binding.tvCurrentResponses.text = it.currentResponses.toString()
                binding.tvCurrentAccepted.text = it.currentAccepted.toString()
                binding.tvCumulativeAds.text = it.cumulativeAds.toString()
                binding.tvCumulativeResponses.text = it.cumulativeResponses.toString()
                binding.tvCumulativeAccepted.text = it.cumulativeAccepted.toString()
            } ?: run {
                binding.tvCurrentAds.text = "0"
                binding.tvCurrentResponses.text = "0"
                binding.tvCurrentAccepted.text = "0"
                binding.tvCumulativeAds.text = "0"
                binding.tvCumulativeResponses.text = "0"
                binding.tvCumulativeAccepted.text = "0"
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
        // Кнопка фильтра
        binding.btnFilter.setOnClickListener {
            val visible = binding.filterPanel.visibility != View.VISIBLE
            binding.filterPanel.visibility = if (visible) View.VISIBLE else View.GONE
        }

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

        binding.tvDateFrom.setOnClickListener { showDatePicker(binding.tvDateFrom, true) }
        binding.tvDateTo.setOnClickListener { showDatePicker(binding.tvDateTo, false) }

        binding.tvCityFilter.setOnClickListener {
            showCityMultiChoiceDialog()
        }
        binding.btnClearCity.setOnClickListener {
            viewModel.setCityIds(emptyList())
            binding.tvCityFilter.text = "Все города"
            binding.btnClearCity.visibility = View.GONE
        }

        binding.tvStatusFilter.setOnClickListener {
            showStatusMultiChoiceDialog()
        }
        binding.btnClearStatus.setOnClickListener {
            viewModel.setStatuses(emptyList())
            binding.tvStatusFilter.text = "Все статусы"
            binding.btnClearStatus.visibility = View.GONE
        }

        binding.btnClearAll.setOnClickListener {
            viewModel.setCityIds(emptyList())
            viewModel.setStatuses(emptyList())
            viewModel.setDateRange(null, null)
            binding.tvCityFilter.text = "Все города"
            binding.btnClearCity.visibility = View.GONE
            binding.tvStatusFilter.text = "Все статусы"
            binding.btnClearStatus.visibility = View.GONE
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

    private fun showStatusMultiChoiceDialog() {
        val statuses = arrayOf("ACTIVE", "FILLED")
        val current = viewModel.selectedStatuses.value?.toMutableList() ?: mutableListOf()
        val checked = BooleanArray(statuses.size) { statuses[it] in current }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Выберите статусы (можно несколько)")
            .setMultiChoiceItems(statuses, checked) { _, which, isChecked ->
                if (isChecked) {
                    if (!current.contains(statuses[which])) current.add(statuses[which])
                } else {
                    current.remove(statuses[which])
                }
            }
            .setPositiveButton("OK") { _, _ ->
                viewModel.setStatuses(current)
                val display = if (current.isEmpty()) "Все статусы" else current.joinToString(", ")
                binding.tvStatusFilter.text = display
                binding.btnClearStatus.visibility = if (current.isEmpty()) View.GONE else View.VISIBLE
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}