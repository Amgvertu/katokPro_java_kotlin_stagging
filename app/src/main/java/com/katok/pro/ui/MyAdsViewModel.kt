package com.katok.pro.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katok.pro.model.*
import com.katok.pro.repository.AdRepository
import com.katok.pro.repository.NotificationRepository
import com.katok.pro.util.AdUtils
import com.katok.pro.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyAdsViewModel @Inject constructor(
    private val adRepository: AdRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // Состояния UI
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

    // Фильтры
    private val _selectedType = MutableLiveData(0)
    val selectedType: LiveData<Int> = _selectedType

    private val _selectedSubtype = MutableLiveData(0)
    val selectedSubtype: LiveData<Int> = _selectedSubtype

    private val _selectedRole = MutableLiveData<String?>(null)
    val selectedRole: LiveData<String?> = _selectedRole

    private val _selectedDateFilter = MutableLiveData<String?>(null)
    val selectedDateFilter: LiveData<String?> = _selectedDateFilter

    private val _selectedStatuses = MutableLiveData<List<String>>(ArrayList())
    val selectedStatuses: LiveData<List<String>> = _selectedStatuses

    private val _selectedResponses = MutableLiveData<String?>(null)
    val selectedResponses: LiveData<String?> = _selectedResponses

    private val _filterPanelVisible = MutableLiveData(false)
    val filterPanelVisible: LiveData<Boolean> = _filterPanelVisible

    private var cachedRinks: List<Rink> = emptyList()

    // Методы для изменения фильтров
    fun setSelectedType(type: Int) { _selectedType.value = type; loadAds() }
    fun setSelectedSubtype(subtype: Int) { _selectedSubtype.value = subtype; loadAds() }
    fun setSelectedRole(role: String?) { _selectedRole.value = role; loadAds() }
    fun setSelectedDateFilter(date: String?) { _selectedDateFilter.value = date; loadAds() }
    fun setSelectedStatuses(statuses: List<String>) { _selectedStatuses.value = statuses; loadAds() }
    fun setSelectedResponses(responses: String?) { _selectedResponses.value = responses; loadAds() }
    fun setFilterPanelVisible(visible: Boolean) { _filterPanelVisible.value = visible }

    fun loadAds() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val allAdsResult = adRepository.getMyAds(0, 100)
            when (allAdsResult) {
                is NetworkResult.Success -> {
                    val allAds = allAdsResult.data.content ?: emptyList()
                    Log.d("DEBUG_RINKS", "MyAdsViewModel loadAds: got ${allAds.size} ads")
                    allAds.forEach { ad ->
                        Log.d("DEBUG_RINKS", "Ad ${ad.id}: rinkIds=${ad.rinkIds}, cityId=${ad.cityId}")
                    }
                    val filtered = filterAds(allAds)
                    _ads.value = filtered
                    Log.d("MyAdsViewModel", "ads updated, size=${filtered.size}")
                    loadRinksForAds(filtered)
                }
                is NetworkResult.Error -> {
                    _error.value = allAdsResult.message
                    _emptyMessage.value = allAdsResult.message
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    private fun filterAds(ads: List<Ad>): List<Ad> {
        return ads.filter { ad ->
            if (_selectedType.value != 0 && ad.type != _selectedType.value) return@filter false
            if (_selectedSubtype.value != 0 && ad.subType != _selectedSubtype.value) return@filter false
            if (_selectedRole.value != null && _selectedType.value == 1 && _selectedSubtype.value == 2) {
                val total = if (_selectedRole.value == "DEFENDER") ad.defendersCount ?: 0 else ad.forwardsCount ?: 0
                val accepted = if (_selectedRole.value == "DEFENDER") ad.acceptedDefendersCount ?: 0 else ad.acceptedForwardsCount ?: 0
                if (accepted >= total) return@filter false
            }
            if (_selectedDateFilter.value != null) {
                val startTime = ad.startTime ?: return@filter false
                val datePart = startTime.split("T")[0]
                if (datePart != _selectedDateFilter.value) return@filter false
            }
            if (_selectedStatuses.value?.isNotEmpty() == true && !_selectedStatuses.value!!.contains(ad.status)) return@filter false
            if (_selectedResponses.value != null) {
                val hasResponses = ad.responses?.isNotEmpty() == true
                if ((_selectedResponses.value == "with" && !hasResponses) || (_selectedResponses.value == "without" && hasResponses)) return@filter false
            }
            true
        }
    }

    internal fun loadRinksForAds(ads: List<Ad>) {
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

    // Действия с откликами и объявлениями
    fun confirmResponse(responseId: String, adId: String, userId: String) {
        viewModelScope.launch {
            val result = adRepository.updateResponseStatus(responseId, "APPROVED")
            when (result) {
                is NetworkResult.Success -> {
                    // Ждём 500 мс, чтобы сервер точно сохранил изменения
                    kotlinx.coroutines.delay(500)
                    loadAds()
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun rejectResponse(responseId: String, adId: String, userId: String) {
        viewModelScope.launch {
            val result = adRepository.updateResponseStatus(responseId, "REJECTED")
            when (result) {
                is NetworkResult.Success -> {
                    // Ждём 500 мс, чтобы сервер точно сохранил изменения
                    kotlinx.coroutines.delay(500)
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
                /*is NetworkResult.Success -> {
                    NotificationRepository().notifyApprovalCancelled(adId, userId)
                    loadAds()
                }*/
                is NetworkResult.Success -> {
                    // Ждём 500 мс, чтобы сервер точно сохранил изменения
                    kotlinx.coroutines.delay(500)
                    loadAds()
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun cancelResponse(responseId: String, adId: String, authorId: String) {
        viewModelScope.launch {
            val result = adRepository.deleteResponse(responseId)
            when (result) {
                /*is NetworkResult.Success -> {
                    NotificationRepository().notifyResponseCancelled(adId, authorId)
                    loadAds()
                }*/
                is NetworkResult.Success -> {
                    // Ждём 500 мс, чтобы сервер точно сохранил изменения
                    kotlinx.coroutines.delay(500)
                    loadAds()
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun archiveAd(adId: String) {
        viewModelScope.launch {
            val ad = Ad().apply { status = "ARCHIVED" }
            val result = adRepository.updateAd(adId, ad)
            when (result) {
                is NetworkResult.Success -> loadAds()
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun deleteAd(adId: String) {
        viewModelScope.launch {
            val result = adRepository.deleteAd(adId)
            when (result) {
                is NetworkResult.Success -> {
                    val current = _ads.value.toMutableList()
                    current.removeAll { it.id.toString() == adId }
                    _ads.value = current
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun clearAllFilters() {
        setSelectedType(0)
        setSelectedSubtype(0)
        setSelectedRole(null)
        setSelectedDateFilter(null)
        setSelectedStatuses(emptyList())
        setSelectedResponses(null)
    }

    fun updateAdLocally(updatedAd: Ad) {
        val currentList = _ads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedAd.id }
        if (index != -1) {
            currentList[index] = updatedAd
            _ads.value = currentList
            loadRinksForAds(_ads.value)
            Log.d("MyAdsViewModel", "✅ _ads.value updated, new size=${_ads.value.size}")
        } else {
            Log.e("MyAdsViewModel", "❌ Ad not found in list")
        }
    }

    fun removeAdLocally(adId: String) {
        val currentList = _ads.value.toMutableList()
        currentList.removeAll { it.id.toString() == adId }
        _ads.value = currentList
    }

    fun refreshAdById(adId: String) {
        Log.d("DEBUG_RINKS", "refreshAdById called for adId=$adId")
        viewModelScope.launch {
            val result = adRepository.getAdById(adId)
            if (result is NetworkResult.Success) {
                Log.d("DEBUG_RINKS", "Fetched ad: id=${result.data.id}, rinkIds=${result.data.rinkIds}, cityId=${result.data.cityId}")
                //updateAdLocally(result.data)
                loadAds()
            } else {
                Log.e("DEBUG_RINKS", "Failed to fetch ad: ${result}")
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

    fun updateAds(newAds: List<Ad>) {
        _ads.value = newAds
        loadRinksForAds(newAds)
    }

    fun unarchiveAd(adId: String) {
        viewModelScope.launch {
            val ad = Ad().apply { status = "ACTIVE" }
            val result = adRepository.updateAd(adId, ad)
            when (result) {
                is NetworkResult.Success -> {
                    // Обновляем список объявлений
                    loadAds()
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                }
                else -> {}
            }
        }
    }
}