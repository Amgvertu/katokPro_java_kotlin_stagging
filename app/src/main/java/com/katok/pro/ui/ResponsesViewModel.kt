package com.katok.pro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katok.pro.model.*
import com.katok.pro.repository.AdRepository
import com.katok.pro.repository.NotificationRepository
import com.katok.pro.util.AdUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResponsesViewModel @Inject constructor(
    private val adRepository: AdRepository
) : ViewModel() {

    private val _ads = MutableStateFlow<List<Ad>>(emptyList())
    val ads: StateFlow<List<Ad>> = _ads.asStateFlow()

    private val _rinkList = MutableStateFlow<List<Rink>>(emptyList())
    val rinkList: StateFlow<List<Rink>> = _rinkList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _emptyMessage = MutableStateFlow<String?>(null)
    val emptyMessage: StateFlow<String?> = _emptyMessage.asStateFlow()

    private val _selectedStatuses = MutableStateFlow<Set<String>>(emptySet())
    val selectedStatuses: StateFlow<Set<String>> = _selectedStatuses.asStateFlow()

    private val _filterPanelVisible = MutableStateFlow(false)
    val filterPanelVisible: StateFlow<Boolean> = _filterPanelVisible.asStateFlow()

    fun setSelectedStatuses(statuses: Set<String>) {
        _selectedStatuses.value = statuses
        filterAds()
    }

    fun setFilterPanelVisible(visible: Boolean) {
        _filterPanelVisible.value = visible
    }

    fun loadAds() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = adRepository.getMyResponses(0, 100)
            when (result) {
                is NetworkResult.Success -> {
                    val wrappers = result.data.content ?: emptyList()
                    val allAds = wrappers.mapNotNull { it.ad }
                    _allWrappers = wrappers
                    filterAds()
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                    _emptyMessage.value = result.message
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    private var _allWrappers: List<MyResponseAdWrapper> = emptyList()

    private fun filterAds() {
        val filteredAds = _allWrappers
            .filter { wrapper ->
                val status = wrapper.myResponse?.status
                _selectedStatuses.value.isEmpty() || _selectedStatuses.value.contains(status)
            }
            .mapNotNull { it.ad }

        _ads.value = filteredAds
        loadRinksForAds(filteredAds)
    }

    private fun loadRinksForAds(ads: List<Ad>) {
        val cityIds = ads.mapNotNull { it.city?.id }.distinct()
        if (cityIds.isEmpty()) {
            _rinkList.value = emptyList()
            return
        }
        viewModelScope.launch {
            val allRinks = mutableListOf<Rink>()
            for (cityId in cityIds) {
                val result = adRepository.getRinksByCity(cityId)
                if (result is NetworkResult.Success) {
                    allRinks.addAll(result.data)
                }
            }
            _rinkList.value = allRinks
        }
    }

    fun cancelResponse(responseId: String, adId: String, authorId: String) {
        viewModelScope.launch {
            val result = adRepository.deleteResponse(responseId)
            when (result) {
                is NetworkResult.Success -> {
                    //NotificationRepository().notifyResponseCancelled(adId, authorId)
                    loadAds()
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun cancelApproval(responseId: String, adId: String, userId: String) {
        viewModelScope.launch {
            val result = adRepository.updateResponseStatus(responseId, "PENDING")
            when (result) {
                is NetworkResult.Success -> {
                    //NotificationRepository().notifyApprovalCancelled(adId, userId)
                    loadAds()
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun updateAdLocally(updatedAd: Ad) {
        // В ResponsesViewModel список ads формируется из myResponses.
        // Нужно найти соответствующий wrapper и обновить ad внутри него.
        val currentWrappers = _allWrappers.toMutableList()
        val index = currentWrappers.indexOfFirst { it.ad?.id == updatedAd.id }
        if (index != -1) {
            val oldWrapper = currentWrappers[index]
            val newWrapper = oldWrapper.copy(ad = updatedAd)
            currentWrappers[index] = newWrapper
            _allWrappers = currentWrappers
            filterAds()  // переприменяем фильтры
        }
    }

    fun refreshAdById(adId: String) {
        viewModelScope.launch {
            val result = adRepository.getAdById(adId)
            if (result is NetworkResult.Success) {
                updateAdLocally(result.data)
            }
        }
    }

    fun addResponseToAd(adId: String, newResponse: Response) {
        val currentList = _ads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.toString() == adId }
        if (index != -1) {
            val ad = currentList[index]
            val newResponses = (ad.responses ?: emptyList()).toMutableList()
            // Удаляем старый отклик этого пользователя, если был
            newResponses.removeAll { it.userId == newResponse.userId }
            newResponses.add(newResponse)
            val updatedAd = ad.copy(responses = newResponses)
            // Пересчитываем счётчики
            val adWithCounts = AdUtils.recalculateAcceptedCounts(updatedAd)
            // Пересчитываем статус (ACTIVE / FILLED)
            val recalculatedAd = AdUtils.recalculateAdStatus(adWithCounts)
            currentList[index] = recalculatedAd
            _ads.value = currentList
            loadRinksForAds(currentList)
        }
    }

    /**
     * Обновить существующий отклик (статус, роль)
     */
    fun updateResponseInAd(adId: String, updatedResponse: Response) {
        val currentList = _ads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.toString() == adId }
        if (index != -1) {
            val ad = currentList[index]
            val newResponses = ad.responses?.map { response ->
                if (response.id == updatedResponse.id) updatedResponse else response
            } ?: listOf(updatedResponse)
            val updatedAd = ad.copy(responses = newResponses)
            val adWithCounts = AdUtils.recalculateAcceptedCounts(updatedAd)
            val recalculatedAd = AdUtils.recalculateAdStatus(adWithCounts)
            currentList[index] = recalculatedAd
            _ads.value = currentList
        }
    }

    /**
     * Удалить отклик из объявления
     */
    fun removeResponseFromAd(adId: String, responseId: String) {
        val currentList = _ads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.toString() == adId }
        if (index != -1) {
            val ad = currentList[index]
            val newResponses = ad.responses?.filterNot { it.id == responseId }
            val updatedAd = ad.copy(responses = newResponses)
            val adWithCounts = AdUtils.recalculateAcceptedCounts(updatedAd)
            val recalculatedAd = AdUtils.recalculateAdStatus(adWithCounts)
            currentList[index] = recalculatedAd
            _ads.value = currentList
        }
    }
}