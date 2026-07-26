package com.katok.pro.ui.monitoring

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katok.pro.model.AdStatsResponse
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.MonitoringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonitoringAdsViewModel @Inject constructor(
    private val repository: MonitoringRepository
) : ViewModel() {

    private val _stats = MutableLiveData<AdStatsResponse?>(null)
    val stats: LiveData<AdStatsResponse?> = _stats

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    // Фильтры
    private val _selectedCityIds = MutableLiveData<List<Int>>(emptyList())
    val selectedCityIds: LiveData<List<Int>> = _selectedCityIds

    private val _selectedStatuses = MutableLiveData<List<String>>(emptyList())
    val selectedStatuses: LiveData<List<String>> = _selectedStatuses

    private val _dateFrom = MutableLiveData<String?>(null)
    val dateFrom: LiveData<String?> = _dateFrom

    private val _dateTo = MutableLiveData<String?>(null)
    val dateTo: LiveData<String?> = _dateTo

    fun setPeriod(period: String?) {
        when (period) {
            "today" -> {
                val today = java.time.LocalDate.now().toString()
                _dateFrom.value = today
                _dateTo.value = today
            }
            "month" -> {
                val now = java.time.LocalDate.now()
                val firstDay = now.withDayOfMonth(1).toString()
                val lastDay = now.withDayOfMonth(now.lengthOfMonth()).toString()
                _dateFrom.value = firstDay
                _dateTo.value = lastDay
            }
            "year" -> {
                val now = java.time.LocalDate.now()
                val firstDay = now.withDayOfYear(1).toString()
                val lastDay = now.withDayOfYear(now.lengthOfYear()).toString()
                _dateFrom.value = firstDay
                _dateTo.value = lastDay
            }
            else -> {}
        }
        loadStatistics()
    }

    fun setDateRange(from: String?, to: String?) {
        _dateFrom.value = from
        _dateTo.value = to
        loadStatistics()
    }

    fun setCityIds(ids: List<Int>) {
        _selectedCityIds.value = ids
        loadStatistics()
    }

    fun setStatuses(statuses: List<String>) {
        _selectedStatuses.value = statuses
        loadStatistics()
    }

    fun loadStatistics() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = repository.getAdsStatistics(
                cityIds = _selectedCityIds.value?.takeIf { it.isNotEmpty() },
                dateFrom = _dateFrom.value,
                dateTo = _dateTo.value,
                statuses = _selectedStatuses.value?.takeIf { it.isNotEmpty() }
            )
            when (result) {
                is NetworkResult.Success -> {
                    _stats.value = result.data
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                    _stats.value = null
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }
}