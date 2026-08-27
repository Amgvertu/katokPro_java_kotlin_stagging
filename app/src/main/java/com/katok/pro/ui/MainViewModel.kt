package com.katok.pro.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katok.pro.model.*
import com.katok.pro.repository.AdRepository
import com.katok.pro.repository.AdvertisingRepository
import com.katok.pro.repository.LocationRepository
import com.katok.pro.repository.NotificationRepository
import com.katok.pro.util.AdUtils
import com.katok.pro.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val adRepository: AdRepository,
    private val locationRepository: LocationRepository,
    private val sessionManager: SessionManager,
    private val advertisingRepository: AdvertisingRepository   // ← добавить
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
    private val _selectedCityId = MutableStateFlow(0)
    val selectedCityId: StateFlow<Int> = _selectedCityId.asStateFlow()

    private val _selectedCityName = MutableStateFlow("Все города")
    val selectedCityName: StateFlow<String> = _selectedCityName.asStateFlow()

    private val _selectedType = MutableStateFlow(0)
    val selectedType: StateFlow<Int> = _selectedType.asStateFlow()

    private val _selectedSubtype = MutableStateFlow(0)
    val selectedSubtype: StateFlow<Int> = _selectedSubtype.asStateFlow()

    private val _selectedRole = MutableStateFlow<String?>(null)
    val selectedRole: StateFlow<String?> = _selectedRole.asStateFlow()

    private val _selectedLevels = MutableStateFlow<List<String>>(emptyList())
    val selectedLevels: StateFlow<List<String>> = _selectedLevels.asStateFlow()

    private val _startDate = MutableStateFlow<String?>(null)
    val startDate: StateFlow<String?> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<String?>(null)
    val endDate: StateFlow<String?> = _endDate.asStateFlow()

    private val _startTime = MutableStateFlow<String?>(null)
    val startTime: StateFlow<String?> = _startTime.asStateFlow()

    private val _endTime = MutableStateFlow<String?>(null)
    val endTime: StateFlow<String?> = _endTime.asStateFlow()

    private val _selectedRinkIds = MutableStateFlow<List<Int>>(emptyList())
    val selectedRinkIds: StateFlow<List<Int>> = _selectedRinkIds.asStateFlow()

    private val _filterPanelVisible = MutableStateFlow(false)
    val filterPanelVisible: StateFlow<Boolean> = _filterPanelVisible.asStateFlow()

    private val _showToast = MutableSharedFlow<String>()
    val showToast: SharedFlow<String> = _showToast.asSharedFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _advertisements = MutableStateFlow<List<Advertising>>(emptyList())
    val advertisements: StateFlow<List<Advertising>> = _advertisements.asStateFlow()

    private val _mergedItems = MutableStateFlow<List<Any>>(emptyList())
    val mergedItems: StateFlow<List<Any>> = _mergedItems.asStateFlow()

    private var currentPage = 0
    private var hasMoreData = true
    private var isLoadingMoreInProgress = false

    // Методы для изменения фильтров (с автоматической перезагрузкой)
    fun setSelectedCityId(id: Int) {
        _selectedCityId.value = id
        loadAds(isRefresh = true)
    }

    fun setSelectedCityName(name: String) {
        _selectedCityName.value = name
    }

    fun setSelectedType(type: Int) {
        _selectedType.value = type
        _selectedSubtype.value = 0 // при смене категории сбрасываем подтип
        _selectedRole.value = null
        loadAds(isRefresh = true)
    }

    fun setSelectedSubtype(subtype: Int) {
        _selectedSubtype.value = subtype
        loadAds(isRefresh = true)
    }

    fun setSelectedRole(role: String?) {
        _selectedRole.value = role
        loadAds(isRefresh = true)
    }

    fun setSelectedLevels(levels: List<String>) {
        _selectedLevels.value = levels
        loadAds(isRefresh = true)
    }

    fun setStartDate(date: String?) {
        _startDate.value = date
        loadAds(isRefresh = true)
    }

    fun setEndDate(date: String?) {
        _endDate.value = date
        loadAds(isRefresh = true)
    }

    fun setStartTime(time: String?) {
        _startTime.value = time
        loadAds(isRefresh = true)
    }

    fun setEndTime(time: String?) {

        _endTime.value = time
        loadAds(isRefresh = true)
    }

    fun setSelectedRinkIds(ids: List<Int>) {
        Log.d("MainViewModel", "setSelectedRinkIds: $ids")
        _selectedRinkIds.value = ids
        loadAds(isRefresh = true)
    }

    fun setFilterPanelVisible(visible: Boolean) {
        _filterPanelVisible.value = visible
    }

    fun loadAds(isRefresh: Boolean = true) {
        viewModelScope.launch {
            if (isRefresh) {
                // Сброс пагинации при обновлении
                currentPage = 0
                hasMoreData = true
                _isLoading.value = true
                _error.value = null
            } else {
                // Загрузка следующей страницы
                if (isLoadingMoreInProgress || !hasMoreData) return@launch
                isLoadingMoreInProgress = true
                _isLoadingMore.value = true
            }

            val cityId = if (_selectedCityId.value > 0) _selectedCityId.value else null
            val type = if (_selectedType.value > 0) _selectedType.value else null
            val subType = if (_selectedSubtype.value > 0) _selectedSubtype.value else null
            val role = _selectedRole.value
            val levels = _selectedLevels.value.takeIf { it.isNotEmpty() }
            val dateFrom = _startDate.value
            val dateTo = _endDate.value
            val timeFrom = _startTime.value
            val timeTo = _endTime.value
            val rinkIds = _selectedRinkIds.value.takeIf { it.isNotEmpty() }

            val result = adRepository.getFilteredAds(
                cityId = cityId,
                type = type,
                subType = subType,
                role = role,
                levels = levels,
                dateFrom = dateFrom,
                dateTo = dateTo,
                timeFrom = timeFrom,
                timeTo = timeTo,
                rinkIds = rinkIds,
                page = currentPage,
                size = 20
            )

            when (result) {
                is NetworkResult.Success -> {
                    val loadedAds = result.data.content ?: emptyList()
                    Log.d("DEBUG_RINKS", "loadAds: got ${loadedAds.size} ads")
                    loadedAds.forEach { ad ->
                        Log.d("DEBUG_RINKS", "Ad ${ad.id}: rinkIds=${ad.rinkIds}, cityId=${ad.cityId}")
                    }
                    val totalPages = result.data.totalPages

                    if (isRefresh) {
                        _ads.value = loadedAds
                    } else {
                        val currentList = _ads.value.toMutableList()
                        currentList.addAll(loadedAds)
                        _ads.value = currentList
                    }
                    loadRinksForAds(_ads.value)
                    loadAdvertisements(_selectedCityId.value)
                    mergeItems()
                    hasMoreData = currentPage + 1 < totalPages
                    if (loadedAds.isNotEmpty()) currentPage++

                    loadAdvertisements(_selectedCityId.value)
                }
                is NetworkResult.Error -> {
                    if (isRefresh) {
                        _error.value = result.message
                        _emptyMessage.value = result.message
                    } else {
                        // При ошибке загрузки следующей страницы показываем тост
                        _showToast.emit("Ошибка загрузки: ${result.message}")
                    }
                }
                else -> Unit
            }

            if (isRefresh) {
                _isLoading.value = false
            } else {
                isLoadingMoreInProgress = false
                _isLoadingMore.value = false
            }
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

    fun updateAds(newAds: List<Ad>) {
        Log.d("DEBUG_UPDATE", "updateAds called with ${newAds.size} ads, first status: ${newAds.firstOrNull()?.status}")
        _ads.value = newAds
        loadRinksForAds(newAds)
        mergeItems()
    }

    fun updateRinks(newRinks: List<Rink>) {
        _rinkList.value = newRinks
    }

    // Действия с объявлениями
    fun respondToAd(adId: String, role: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = adRepository.createResponse(adId, "Хочу участвовать", role)
            _isLoading.value = false
            when (result) {
                is NetworkResult.Success -> {
                    result.data?.let { response ->
                        addResponseToAd(adId, response)
                    }
                    //refreshAd(adId) // синхронизация с сервером
                    loadAds(isRefresh = true)
                    _error.value = null
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun cancelResponse(responseId: String, adId: String, authorId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = adRepository.deleteResponse(responseId)
            _isLoading.value = false
            when (result) {
                is NetworkResult.Success -> {
                    removeResponseFromAd(adId, responseId)
                    refreshAd(adId)
                    //NotificationRepository().notifyResponseCancelled(adId, authorId)
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun confirmResponse(responseId: String, adId: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = adRepository.updateResponseStatus(responseId, "APPROVED")
            _isLoading.value = false
            when (result) {
                is NetworkResult.Success -> {
                    val updatedResponse = result.data
                    updateResponseStatusLocally(adId, responseId, "APPROVED", updatedResponse?.responseRole)
                    refreshAd(adId)
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun rejectResponse(responseId: String, adId: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = adRepository.updateResponseStatus(responseId, "REJECTED")
            _isLoading.value = false
            when (result) {
                is NetworkResult.Success -> {
                    removeResponseFromAdLocally(adId, responseId)
                    refreshAd(adId)
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun cancelApproval(responseId: String, adId: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = adRepository.updateResponseStatus(responseId, "PENDING")
            _isLoading.value = false
            when (result) {
                is NetworkResult.Success -> {
                    updateResponseStatusLocally(adId, responseId, "PENDING")
                    refreshAd(adId)
                   //NotificationRepository().notifyApprovalCancelled(adId, userId)
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun archiveAd(adId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val ad = Ad().apply { status = "ARCHIVED" }
            val result = adRepository.updateAd(adId, ad)
            _isLoading.value = false
            when (result) {
                is NetworkResult.Success -> refreshAd(adId)
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun deleteAd(adId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = adRepository.deleteAd(adId)
            _isLoading.value = false
            when (result) {
                is NetworkResult.Success -> {
                    // Удаляем из текущего списка
                    val currentList = _ads.value.toMutableList()
                    currentList.removeAll { it.id.toString() == adId }
                    _ads.value = currentList
                    loadRinksForAds(_ads.value)
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                    refreshAd(adId) // на случай, если удаление не удалось, но локально убрали
                }
                else -> {}
            }
        }
    }

    fun editAd(adId: String) {
        // Навигация не должна быть во ViewModel, поэтому отправляем событие во фрагмент через отдельный канал
        _navigateToEditAd.value = adId
    }

    // Для навигации добавим отдельный StateFlow
    private val _navigateToEditAd = MutableStateFlow<String?>(null)
    val navigateToEditAd: StateFlow<String?> = _navigateToEditAd.asStateFlow()

    fun clearNavigateToEditAd() {
        _navigateToEditAd.value = null
    }

    // Обновление одного объявления
    private fun refreshAd(adId: String) {
        viewModelScope.launch {
            Log.d("DEBUG_RINKS", "refreshAd for adId=$adId")
            val result = adRepository.getAdById(adId)
            when (result) {
                is NetworkResult.Success -> {
                    val updatedAd = result.data
                    Log.d("DEBUG_RINKS", "Refreshed ad: id=${updatedAd.id}, rinkIds=${updatedAd.rinkIds}, cityId=${updatedAd.cityId}")
                    val currentList = _ads.value.toMutableList()
                    Log.d("DEBUG_REF", "Current list size: ${currentList.size}")
                    val index = currentList.indexOfFirst { it.id.toString() == adId }
                    Log.d("DEBUG_REF", "Found index: $index")
                    if (index != -1) {
                        currentList[index] = updatedAd
                        _ads.value = currentList
                        loadRinksForAds(_ads.value)
                        Log.d("DEBUG_REF", "List updated, new size: ${_ads.value.size}")
                    } else {
                        Log.e("DEBUG_REF", "Ad not found in current list!")
                    }
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                }
                else -> {}
            }
        }
    }

    private fun showToast(message: String) {
        viewModelScope.launch {
            _showToast.emit(message)
        }
    }

    fun refreshAdById(adId: String) {
        refreshAd(adId)
    }

    fun clearAllFilters() {
        setSelectedType(0)
        setSelectedSubtype(0)
        setSelectedRole(null)
        setSelectedLevels(emptyList())
        setStartDate(null)
        setEndDate(null)
        setStartTime(null)
        setEndTime(null)
        setSelectedRinkIds(emptyList())
        // Город не сбрасываем
    }

    fun loadNextPage() {
        if (!isLoadingMoreInProgress && hasMoreData && !_isLoading.value) {
            loadAds(isRefresh = false)
        }
    }

    // Локальное добавление отклика
    private fun addResponseToAdLocally(adId: String, newResponse: Response) {
        val currentList = _ads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.toString() == adId }
        if (index != -1) {
            val ad = currentList[index]
            val updatedResponses = ad.responses?.toMutableList() ?: mutableListOf()
            updatedResponses.add(newResponse)
            val updatedAd = ad.copy(responses = updatedResponses)
            currentList[index] = updatedAd
            _ads.value = currentList
        }
    }

    // Локальное удаление отклика
    private fun removeResponseFromAdLocally(adId: String, responseId: String) {
        val currentList = _ads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.toString() == adId }
        if (index != -1) {
            val ad = currentList[index]
            val updatedResponses = ad.responses?.filterNot { it.id == responseId }
            val updatedAd = ad.copy(responses = updatedResponses)
            currentList[index] = updatedAd
            _ads.value = currentList
        }
    }

    // Локальное обновление статуса отклика
    private fun updateResponseStatusLocally(adId: String, responseId: String, newStatus: String, role: String? = null) {
        val currentList = _ads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.toString() == adId }
        if (index != -1) {
            val ad = currentList[index]
            val updatedResponses = ad.responses?.map { response ->
                if (response.id == responseId) {
                    response.status = newStatus
                    if (role != null) response.responseRole = role
                }
                response
            }
            val updatedAd = ad.copy(responses = updatedResponses)
            currentList[index] = updatedAd
            _ads.value = currentList
        }
    }

    fun addResponseToAd(adId: String, newResponse: Response) {
        val currentList = _ads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.toString() == adId }
        if (index != -1) {
            val ad = currentList[index]
            val newResponses = (ad.responses ?: emptyList()).toMutableList()
            newResponses.removeAll { it.userId == newResponse.userId }
            newResponses.add(newResponse)
            val updatedAd = ad.copy(responses = newResponses)
            // Пересчитываем счётчики
            val adWithCounts = AdUtils.recalculateAcceptedCounts(updatedAd)
            val recalculatedAd = AdUtils.recalculateAdStatus(adWithCounts)
            currentList[index] = recalculatedAd
            _ads.value = currentList
            loadRinksForAds(currentList)
            mergeItems()
        }
    }

    fun updateResponseInAd(adId: String, updatedResponse: Response) {
        val currentList = _ads.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.toString() == adId }
        if (index != -1) {
            val ad = currentList[index]
            val newResponses = ad.responses?.map { response ->
                if (response.id == updatedResponse.id) updatedResponse else response
            } ?: listOf(updatedResponse)
            val updatedAd = ad.copy(responses = newResponses)
            // Пересчитываем счётчики
            val adWithCounts = AdUtils.recalculateAcceptedCounts(updatedAd)
            val recalculatedAd = AdUtils.recalculateAdStatus(adWithCounts)
            currentList[index] = recalculatedAd
            _ads.value = currentList
            loadRinksForAds(_ads.value)
            mergeItems()
        }
    }

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
            // 👇 ДОБАВЛЯЕМ ЭТИ ДВЕ СТРОКИ
            loadRinksForAds(currentList)
            mergeItems()
        }
    }

    fun optimisticRespondToAd(adId: String, role: String?) {
        // 1. Создаём временный отклик со статусом PENDING
        val tempResponse = Response().apply {
            id = UUID.randomUUID().toString()
            this.adId = adId
            // Получаем ID текущего пользователя из sessionManager
            userId = runBlocking { sessionManager.getUserId() }
            status = "PENDING"
            responseRole = role
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
        }

        // 2. Добавляем временный отклик в список (мгновенно!)
        addResponseToAd(adId, tempResponse)

        // 3. Отправляем реальный запрос на сервер
        viewModelScope.launch {
            val result = adRepository.createResponse(adId, "Хочу участвовать", role)
            when (result) {
                is NetworkResult.Success -> {
                    // 4. Успех – заменяем временный отклик на настоящий
                    result.data?.let { realResponse ->
                        updateResponseInAd(adId, realResponse)
                    }
                }
                is NetworkResult.Error -> {
                    // 5. Ошибка – удаляем временный отклик и показываем ошибку
                    removeResponseFromAd(adId, tempResponse.id!!)
                    _error.value = result.message
                }
                else -> {}
            }
        }
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

    private fun loadAdvertisements(cityId: Int) {
        viewModelScope.launch {
            if (cityId == 0) return@launch // без города не грузим
            val result = advertisingRepository.getActiveAdvertisements(type = 1, cityId = cityId)
            if (result is NetworkResult.Success) {
                _advertisements.value = result.data
                mergeItems()
            }
        }
    }

    private fun mergeItems() {
        val ads = _ads.value
        val adverts = _advertisements.value
        if (ads.isEmpty()) {
            _mergedItems.value = adverts
            return
        }
        val interval = adverts.firstOrNull()?.interval ?: 5
        val merged = mutableListOf<Any>()
        var advertIndex = 0
        var adIndex = 0

        while (adIndex < ads.size) {
            if (ads.size - adIndex < interval) {
                // Добавляем все оставшиеся объявления
                while (adIndex < ads.size) {
                    merged.add(ads[adIndex])
                    adIndex++
                }
                // Добавляем все рекламы
                if (advertIndex < adverts.size) {
                    merged.addAll(adverts.subList(advertIndex, adverts.size))
                    advertIndex = adverts.size // <--- ВАЖНО: помечаем, что рекламы добавлены
                }
                break
            } else {
                // Добавляем interval объявлений
                for (i in 0 until interval) {
                    if (adIndex < ads.size) {
                        merged.add(ads[adIndex])
                        adIndex++
                    }
                }
                // Добавляем одну рекламу
                if (advertIndex < adverts.size) {
                    merged.add(adverts[advertIndex])
                    advertIndex++
                }
            }
        }

        // Если остались ещё рекламы, добавляем их в конец
        if (advertIndex < adverts.size) {
            merged.addAll(adverts.subList(advertIndex, adverts.size))
        }

        _mergedItems.value = merged
    }
}