package com.katok.pro.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.katok.pro.R
import com.katok.pro.adapter.AdCardAdapter
import com.katok.pro.databinding.FragmentMyAdsBinding
import com.katok.pro.model.Ad
import com.katok.pro.model.RealtimeEvent
import com.katok.pro.model.Response
import com.katok.pro.network.RealtimeEventBus
import com.katok.pro.network.WebSocketManager
import com.katok.pro.network.WebSocketSubscriptionManager
import com.katok.pro.services.WebSocketForegroundService
import com.katok.pro.util.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class MyAdsFragment : BaseAdsListFragment() {

    private lateinit var filterDialogHelper: FilterDialogHelper
    private var _binding: FragmentMyAdsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var myAdsViewModel: MyAdsViewModel
    private var webSocketManager: WebSocketManager? = null

    override val adsFlow get() = myAdsViewModel.ads
    override val isLoadingFlow get() = myAdsViewModel.isLoading
    override val errorFlow get() = myAdsViewModel.error
    override val emptyMessageFlow get() = myAdsViewModel.emptyMessage
    override val rinkListFlow get() = myAdsViewModel.rinkList

    override fun onRefresh() {
        myAdsViewModel.loadAds()
    }

    override fun createAdActionListener() = object : AdCardAdapter.OnAdActionListener {
        override fun onRespondClick(ad: Ad) {}
        override fun onCancelResponseClick(responseId: String, adId: String, authorId: String) {
            showCancelResponseConfirmDialog(responseId, adId, authorId)
        }
        override fun onArchiveClick(adId: String) { showArchiveConfirmDialog(adId) }
        override fun onEditClick(adId: String) { navigateToCreateAd(adId) }
        override fun onDeleteClick(adId: String) { showDeleteConfirmDialog(adId) }
        override fun onUnarchiveClick(adId: String) { myAdsViewModel.unarchiveAd(adId) }
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyAdsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        myAdsViewModel = androidx.lifecycle.ViewModelProvider(requireActivity())[MyAdsViewModel::class.java]
        sessionManager = SessionManager(requireContext())

        super.onViewCreated(view, savedInstanceState)

        filterDialogHelper = FilterDialogHelper(this)

        viewLifecycleOwner.lifecycleScope.launch {
            adapter.setCurrentUserId(sessionManager.getUserId())
            adapter.setCurrentUserPhone(sessionManager.getUserPhone())
        }

        RealtimeEventBus.getInstance().getEvents().observe(viewLifecycleOwner) { event ->
            Log.d("MyAdsFragment", "Event received from bus: ${event?.type}")
            event?.let { handleRealtimeEvent(it) }
        }

        setupObservers()
        setupFilters()
        myAdsViewModel.loadAds()

        binding.btnFilter.setOnClickListener {
            val visible = binding.filterPanel.visibility != View.VISIBLE
            binding.filterPanel.visibility = if (visible) View.VISIBLE else View.GONE
            myAdsViewModel.setFilterPanelVisible(visible)
        }

        val service = WebSocketForegroundService.getInstance()
        webSocketManager = service?.getWebSocketManager()
        WebSocketSubscriptionManager.setWebSocketManager(webSocketManager)
    }

    private fun setupObservers() {
        myAdsViewModel.selectedType.observe(viewLifecycleOwner) { updateTypeFilterDisplay() }
        myAdsViewModel.selectedSubtype.observe(viewLifecycleOwner) { updateTypeFilterDisplay() }
        myAdsViewModel.selectedRole.observe(viewLifecycleOwner) { updateTypeFilterDisplay() }
        myAdsViewModel.selectedDateFilter.observe(viewLifecycleOwner) { updateDateFilterDisplay() }
        myAdsViewModel.selectedStatuses.observe(viewLifecycleOwner) { updateStatusFilterDisplay() }
        myAdsViewModel.selectedResponses.observe(viewLifecycleOwner) { updateResponsesFilterDisplay() }
        myAdsViewModel.filterPanelVisible.observe(viewLifecycleOwner) { visible ->
            binding.filterPanel.visibility = if (visible == true) View.VISIBLE else View.GONE
        }
        viewLifecycleOwner.lifecycleScope.launch {
            myAdsViewModel.ads.collect { ads ->
                val adIds = ads.mapNotNull { it.id?.toString() }
                WebSocketSubscriptionManager.subscribeToAdIds(adIds)
            }
        }
        // Удалён блок наблюдения за ошибками – он есть в родителе
    }

    private fun setupFilters() {
        binding.tvTypeValue.setOnClickListener { showTypeDialog() }
        binding.btnClearType.setOnClickListener { clearTypeFilter() }
        binding.tvDateValue.setOnClickListener { showDatePickerDialog() }
        binding.btnClearDate.setOnClickListener { clearDateFilter() }
        binding.tvStatusValue.setOnClickListener { showStatusDialog() }
        binding.btnClearStatus.setOnClickListener { clearStatusFilter() }
        binding.tvResponsesValue.setOnClickListener { showResponsesDialog() }
        binding.btnClearResponses.setOnClickListener { clearResponsesFilter() }
        binding.btnClearAllFilters.setOnClickListener {
            myAdsViewModel.clearAllFilters()
        }
        updateTypeFilterDisplay()
        updateDateFilterDisplay()
        updateStatusFilterDisplay()
        updateResponsesFilterDisplay()
    }

    private fun updateTypeFilterDisplay() {
        val type = myAdsViewModel.selectedType.value ?: 0
        val subtype = myAdsViewModel.selectedSubtype.value ?: 0
        val role = myAdsViewModel.selectedRole.value
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

    private fun updateDateFilterDisplay() {
        val date = myAdsViewModel.selectedDateFilter.value
        if (date == null) {
            binding.tvDateValue.text = "Любая"
            binding.btnClearDate.visibility = View.GONE
        } else {
            val parts = date.split("-")
            binding.tvDateValue.text = if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else date
            binding.btnClearDate.visibility = View.VISIBLE
        }
    }

    private fun updateStatusFilterDisplay() {
        val statuses = myAdsViewModel.selectedStatuses.value ?: emptyList()
        if (statuses.isEmpty()) {
            binding.tvStatusValue.text = "Все"
            binding.btnClearStatus.visibility = View.GONE
        } else {
            val display = statuses.joinToString { status ->
                when (status) {
                    "ACTIVE" -> "Актуальные"
                    "MODERATION" -> "На модерации"
                    "FILLED" -> "Набрано"
                    "ARCHIVED" -> "Архивные"
                    else -> status
                }
            }
            binding.tvStatusValue.text = display
            binding.btnClearStatus.visibility = View.VISIBLE
        }
    }

    private fun updateResponsesFilterDisplay() {
        val responses = myAdsViewModel.selectedResponses.value
        when (responses) {
            null -> {
                binding.tvResponsesValue.text = "Все"
                binding.btnClearResponses.visibility = View.GONE
            }
            "with" -> {
                binding.tvResponsesValue.text = "С откликами"
                binding.btnClearResponses.visibility = View.VISIBLE
            }
            "without" -> {
                binding.tvResponsesValue.text = "Без откликов"
                binding.btnClearResponses.visibility = View.VISIBLE
            }
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

    private fun showTypeDialog() {
        filterDialogHelper.showTypeDialog { category, subtype, role ->
            myAdsViewModel.setSelectedType(category)
            myAdsViewModel.setSelectedSubtype(subtype)
            myAdsViewModel.setSelectedRole(role)
        }
    }

    private fun clearTypeFilter() {
        myAdsViewModel.setSelectedType(0)
        myAdsViewModel.setSelectedSubtype(0)
        myAdsViewModel.setSelectedRole(null)
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val date = String.format("%04d-%02d-%02d", year, month + 1, day)
                myAdsViewModel.setSelectedDateFilter(date)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).also { dialog ->
            dialog.setButton(DatePickerDialog.BUTTON_NEGATIVE, "Сбросить") { _, _ -> clearDateFilter() }
        }.show()
    }

    private fun clearDateFilter() {
        myAdsViewModel.setSelectedDateFilter(null)
    }

    private fun showStatusDialog() {
        val statuses = arrayOf("Актуальные", "На модерации", "Набрано", "Архивные")
        val current = myAdsViewModel.selectedStatuses.value?.toMutableList() ?: mutableListOf()
        val checked = BooleanArray(4) { i ->
            when (i) {
                0 -> current.contains("ACTIVE")
                1 -> current.contains("MODERATION")
                2 -> current.contains("FILLED")
                3 -> current.contains("ARCHIVED")
                else -> false
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Выберите статусы (можно несколько)")
            .setMultiChoiceItems(statuses, checked) { _, which, isChecked ->
                val key = when (which) {
                    0 -> "ACTIVE"
                    1 -> "MODERATION"
                    2 -> "FILLED"
                    3 -> "ARCHIVED"
                    else -> ""
                }
                if (isChecked) {
                    if (!current.contains(key)) current.add(key)
                } else {
                    current.remove(key)
                }
            }
            .setPositiveButton("OK") { _, _ ->
                myAdsViewModel.setSelectedStatuses(current)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearStatusFilter() {
        myAdsViewModel.setSelectedStatuses(emptyList())
    }

    private fun showResponsesDialog() {
        val options = arrayOf("Все", "С откликами", "Без откликов")
        var checkedItem = 0
        when (myAdsViewModel.selectedResponses.value) {
            "with" -> checkedItem = 1
            "without" -> checkedItem = 2
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Выберите фильтр откликов")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val newValue = when (which) {
                    1 -> "with"
                    2 -> "without"
                    else -> null
                }
                myAdsViewModel.setSelectedResponses(newValue)
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearResponsesFilter() {
        myAdsViewModel.setSelectedResponses(null)
    }

    // Действия с откликами и объявлениями
    private fun showConfirmResponseDialog(responseId: String, adId: String, userId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Подтверждение отклика")
            .setMessage("Вы уверены, что хотите принять этот отклик?")
            .setPositiveButton("Да") { _, _ -> myAdsViewModel.confirmResponse(responseId, adId, userId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRejectResponseDialog(responseId: String, adId: String, userId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отклонение отклика")
            .setMessage("Вы уверены, что хотите отклонить этот отклик?")
            .setPositiveButton("Да") { _, _ -> myAdsViewModel.rejectResponse(responseId, adId, userId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCancelApprovalResponseDialog(responseId: String, adId: String, userId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отмена подтверждения")
            .setMessage("Вы уверены, что хотите отменить подтверждение этого отклика?")
            .setPositiveButton("Да") { _, _ -> myAdsViewModel.cancelApproval(responseId, adId, userId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCancelResponseConfirmDialog(responseId: String, adId: String, authorId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отмена отклика")
            .setMessage("Вы уверены, что хотите отменить свой отклик?")
            .setPositiveButton("Да") { _, _ -> myAdsViewModel.cancelResponse(responseId, adId, authorId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showArchiveConfirmDialog(adId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Архивировать")
            .setMessage("Переместить объявление в архив?")
            .setPositiveButton("Да") { _, _ -> myAdsViewModel.archiveAd(adId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteConfirmDialog(adId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить")
            .setMessage("Удалить объявление? Это действие нельзя отменить.")
            .setPositiveButton("Да") { _, _ -> myAdsViewModel.deleteAd(adId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun navigateToCreateAd(adId: String) {
        val args = Bundle().apply { putString("adId", adId) }
        NavHostFragment.findNavController(this).navigate(R.id.navigation_create, args)
    }

    private fun navigateToProfile(userId: String, canShowPhone: Boolean, phone: String?) {
        val args = Bundle().apply {
            putString("userId", userId)
            putBoolean("canShowPhone", canShowPhone)
            phone?.let { putString("phone", it) }
        }
        NavHostFragment.findNavController(this).navigate(R.id.viewProfileFragment, args)
    }

    override fun onResume() {
        super.onResume()
        val service = WebSocketForegroundService.getInstance()
        webSocketManager = service?.getWebSocketManager()
    }

    override fun onDestroyView() {
        WebSocketSubscriptionManager.subscribeToAdIds(emptyList())
        super.onDestroyView()
        _binding = null
    }

    private fun handleRealtimeEvent(event: RealtimeEvent) {
        Log.d("MyAdsFragment", "handleRealtimeEvent called with event: ${event.type}, entityId=${event.entityId}")
        when (event.type) {
            RealtimeEvent.Type.AD_CREATED -> {
                val newAd = event.payload as? Ad ?: return
                if (newAd.status == "ACTIVE" || newAd.status == "FILLED") {
                    val current = myAdsViewModel.ads.value.toMutableList()
                    if (current.none { it.id == newAd.id }) {
                        current.add(newAd)
                        myAdsViewModel.updateAds(current)
                    }
                }
            }
            RealtimeEvent.Type.AD_UPDATED -> {
                val updatedAd = event.payload as? Ad
                if (updatedAd != null) {
                    val current = myAdsViewModel.ads.value.toMutableList()
                    val index = current.indexOfFirst { it.id == updatedAd.id }
                    if (index != -1) {
                        current[index] = updatedAd
                    } else if (updatedAd.status == "ACTIVE" || updatedAd.status == "FILLED") {
                        current.add(updatedAd)
                    } else {
                        return
                    }
                    myAdsViewModel.updateAds(current)
                }
            }
            RealtimeEvent.Type.AD_DELETED -> {
                val adId = event.entityId
                if (adId != null) {
                    val current = myAdsViewModel.ads.value.toMutableList()
                    current.removeAll { it.id.toString() == adId }
                    myAdsViewModel.updateAds(current)
                }
            }
            RealtimeEvent.Type.RESPONSE_ADDED -> {
                val response = event.payload as? Response ?: return
                val adId = event.entityId ?: response.adId ?: return
                myAdsViewModel.addResponseToAd(adId, response)
            }
            RealtimeEvent.Type.RESPONSE_REMOVED -> {
                val response = event.payload as? Response ?: return
                val adId = event.entityId ?: response.adId ?: return
                val responseId = response.id ?: return
                myAdsViewModel.removeResponseFromAd(adId, responseId)
            }
            RealtimeEvent.Type.RESPONSE_APPROVED,
            RealtimeEvent.Type.RESPONSE_REJECTED,
            RealtimeEvent.Type.APPROVAL_CANCELLED,
            RealtimeEvent.Type.RESPONSE_WITHDRAWN -> {
                val response = event.payload as? Response ?: return
                val adId = event.entityId ?: response.adId ?: return
                myAdsViewModel.updateResponseInAd(adId, response)
            }
            else -> {}
        }
    }
}