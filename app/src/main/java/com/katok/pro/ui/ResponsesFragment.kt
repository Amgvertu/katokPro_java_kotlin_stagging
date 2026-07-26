package com.katok.pro.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.katok.pro.R
import com.katok.pro.adapter.AdCardAdapter
import com.katok.pro.databinding.FragmentResponsesBinding
import com.katok.pro.model.Ad
import com.katok.pro.model.RealtimeEvent
import com.katok.pro.model.Response
import com.katok.pro.network.RealtimeEventBus
import com.katok.pro.network.WebSocketSubscriptionManager
import com.katok.pro.services.WebSocketForegroundService
import com.katok.pro.util.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResponsesFragment : BaseAdsListFragment() {

    private var _binding: FragmentResponsesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ResponsesViewModel
    private lateinit var sessionManager: SessionManager

    override val adsFlow get() = viewModel.ads
    override val isLoadingFlow get() = viewModel.isLoading
    override val errorFlow get() = viewModel.error
    override val emptyMessageFlow get() = viewModel.emptyMessage
    override val rinkListFlow get() = viewModel.rinkList

    override fun onRefresh() {
        viewModel.loadAds()
    }

    override fun createAdActionListener() = object : AdCardAdapter.OnAdActionListener {
        override fun onRespondClick(ad: Ad) {}
        override fun onCancelResponseClick(responseId: String, adId: String, authorId: String) {
            showCancelResponseDialog(responseId, adId, authorId)
        }
        override fun onArchiveClick(adId: String) {}
        override fun onEditClick(adId: String) {}
        override fun onDeleteClick(adId: String) {}
        override fun onUnarchiveClick(adId: String) {}
        override fun onProfileClick(userId: String?, canShowPhone: Boolean, phone: String?) {
            navigateToProfile(userId, canShowPhone, phone)
        }
        override fun onConfirmResponseClick(responseId: String, adId: String, userId: String) {}
        override fun onRejectResponseClick(responseId: String, adId: String, userId: String) {}
        override fun onCancelApprovalResponseClick(responseId: String, adId: String, userId: String) {
            showCancelApprovalDialog(responseId, adId, userId)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResponsesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sessionManager = SessionManager(requireContext())
        viewModel = androidx.lifecycle.ViewModelProvider(requireActivity())[ResponsesViewModel::class.java]

        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            adapter.setCurrentUserId(sessionManager.getUserId())
            adapter.setCurrentUserPhone(sessionManager.getUserPhone())
        }

        setupCheckboxes()
        observeViewModel()
        viewModel.loadAds()

        RealtimeEventBus.getInstance().getEvents().observe(viewLifecycleOwner) { event ->
            event?.let { handleRealtimeEvent(it) }
        }

        binding.btnFilter.setOnClickListener {
            val visible = binding.filterPanel.visibility != View.VISIBLE
            binding.filterPanel.visibility = if (visible) View.VISIBLE else View.GONE
            viewModel.setFilterPanelVisible(visible)
        }

        val service = WebSocketForegroundService.getInstance()
        val webSocketManager = service?.getWebSocketManager()
        WebSocketSubscriptionManager.setWebSocketManager(webSocketManager)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedStatuses.collect { statuses ->
                updateCheckboxesFromStatuses(statuses)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filterPanelVisible.collect { visible ->
                binding.filterPanel.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ads.collect { ads ->
                val adIds = ads.mapNotNull { it.id?.toString() }
                WebSocketSubscriptionManager.subscribeToAdIds(adIds)
            }
        }
        // Блок ошибок удалён – он в родителе
    }

    private fun setupCheckboxes() {
        binding.chkAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.setSelectedStatuses(setOf("APPROVED", "PENDING"))
            } else {
                viewModel.setSelectedStatuses(emptySet())
            }
        }
        binding.chkApproved.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.selectedStatuses.value.toMutableSet()
            if (isChecked) current.add("APPROVED") else current.remove("APPROVED")
            viewModel.setSelectedStatuses(current)
        }
        binding.chkPending.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.selectedStatuses.value.toMutableSet()
            if (isChecked) current.add("PENDING") else current.remove("PENDING")
            viewModel.setSelectedStatuses(current)
        }
    }

    private fun updateCheckboxesFromStatuses(statuses: Set<String>) {
        binding.chkAll.isChecked = statuses.containsAll(setOf("APPROVED", "PENDING"))
        binding.chkApproved.isChecked = statuses.contains("APPROVED")
        binding.chkPending.isChecked = statuses.contains("PENDING")
    }

    private fun showCancelResponseDialog(responseId: String, adId: String, authorId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отмена отклика")
            .setMessage("Вы уверены, что хотите отменить свой отклик?")
            .setPositiveButton("Да") { _, _ -> viewModel.cancelResponse(responseId, adId, authorId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCancelApprovalDialog(responseId: String, adId: String, userId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Отмена подтверждения")
            .setMessage("Вы уверены, что хотите отменить подтверждение отклика?")
            .setPositiveButton("Да") { _, _ -> viewModel.cancelApproval(responseId, adId, userId) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun navigateToProfile(userId: String?, canShowPhone: Boolean, phone: String?) {
        val args = Bundle().apply {
            putString("userId", userId)
            putBoolean("canShowPhone", canShowPhone)
            if (phone != null) putString("phone", phone)
        }
        findNavController().navigate(R.id.viewProfileFragment, args)
    }

    override fun onResume() {
        super.onResume()
        // Можно оставить пустым
    }

    override fun onDestroyView() {
        WebSocketSubscriptionManager.subscribeToAdIds(emptyList())
        super.onDestroyView()
        _binding = null
    }

    private fun handleRealtimeEvent(event: RealtimeEvent) {
        when (event.type) {
            RealtimeEvent.Type.RESPONSE_ADDED -> {
                val response = event.payload as? Response ?: return
                val adId = event.entityId ?: response.adId ?: return
                viewModel.addResponseToAd(adId, response)
            }
            RealtimeEvent.Type.RESPONSE_REMOVED -> {
                val response = event.payload as? Response ?: return
                val adId = event.entityId ?: response.adId ?: return
                val responseId = response.id ?: return
                viewModel.removeResponseFromAd(adId, responseId)
            }
            RealtimeEvent.Type.RESPONSE_APPROVED,
            RealtimeEvent.Type.RESPONSE_REJECTED,
            RealtimeEvent.Type.APPROVAL_CANCELLED,
            RealtimeEvent.Type.RESPONSE_WITHDRAWN -> {
                val response = event.payload as? Response ?: return
                val adId = event.entityId ?: response.adId ?: return
                viewModel.updateResponseInAd(adId, response)
            }
            RealtimeEvent.Type.AD_UPDATED -> {
                val updatedAd = event.payload as? Ad
                if (updatedAd != null && updatedAd.id != null) {
                    viewModel.updateAdLocally(updatedAd)
                } else {
                    viewModel.loadAds()
                }
            }
            else -> viewModel.loadAds()
        }
    }
}