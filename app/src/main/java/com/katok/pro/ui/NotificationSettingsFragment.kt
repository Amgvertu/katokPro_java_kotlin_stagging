package com.katok.pro.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.katok.pro.R
import com.katok.pro.databinding.FragmentNotificationSettingsBinding
import com.katok.pro.model.ApiResponse
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.NotificationSettings
import com.katok.pro.repository.NotificationRepository
import com.katok.pro.util.ApiUtils
import com.katok.pro.util.SessionManager
import com.katok.pro.util.ToastHelper
import kotlinx.coroutines.launch

class NotificationSettingsFragment : BaseFragment(R.layout.fragment_notification_settings) {

    private var _binding: FragmentNotificationSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: NotificationSettingsViewModel
    private lateinit var subscriptionsAdapter: SubscriptionsAdapter
    private val subscriptionsList = mutableListOf<NotificationSettings.Subscription>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notificationRepository = NotificationRepository()
        sessionManager = SessionManager(requireContext())

        binding.rvSubscriptions.layoutManager = LinearLayoutManager(requireContext())
        subscriptionsAdapter = SubscriptionsAdapter(subscriptionsList) { sub ->
            removeSubscription(sub)
        }
        binding.rvSubscriptions.adapter = subscriptionsAdapter

        viewModel = ViewModelProvider(requireActivity())[NotificationSettingsViewModel::class.java]
        observeViewModel()

        binding.switchNewAdsInCity.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutSubscriptions.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnAddSubscription.setOnClickListener { showAddSubscriptionDialog() }
        binding.btnSave.setOnClickListener { saveAllSettings() }

        loadSettings()
    }

    private fun observeViewModel() {
        viewModel.getSettings().observe(viewLifecycleOwner) { settings ->
            settings?.let {
                binding.switchResponseToMyAd.isChecked = it.isNotifyOnResponseToMyAd
                binding.switchMyResponseAccepted.isChecked = it.isNotifyOnMyResponseAccepted
                binding.switchNewAdsInCity.isChecked = it.isNotifyNewAdsInCity
                binding.layoutSubscriptions.visibility = if (it.isNotifyNewAdsInCity) View.VISIBLE else View.GONE

                it.subscriptions?.let { subs ->
                    subscriptionsList.clear()
                    subscriptionsList.addAll(subs)
                    subscriptionsAdapter.notifyDataSetChanged()
                }
            }
        }
        viewModel.getIsLoading().observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSave.isEnabled = !loading
        }
    }

    private fun showAddSubscriptionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_subscription, null)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinnerType)
        val spinnerSubType = dialogView.findViewById<Spinner>(R.id.spinnerSubType)

        val types = arrayOf("Ищу игрока", "Ищу лёд", "Товарищеский матч", "Ищу специалиста")
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = typeAdapter

        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val type = position + 1
                val subTypes = when (type) {
                    1 -> listOf("Вратаря", "Полевого")
                    2 -> listOf("Вратарь", "Полевой")
                    3 -> listOf("Ищу", "Предлагаю")
                    else -> listOf("Судья", "Фотограф", "Медик", "Тренер")
                }
                val subAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, subTypes)
                subAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerSubType.adapter = subAdapter
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val initialSubAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("Вратаря", "Полевого"))
        initialSubAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSubType.adapter = initialSubAdapter

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Добавить") { _, _ ->
                val type = spinnerType.selectedItemPosition + 1
                val subType = spinnerSubType.selectedItemPosition + 1
                addSubscription(type, subType)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun addSubscription(type: Int, subType: Int) {
        val newSub = NotificationSettings.Subscription(type, subType)
        if (subscriptionsList.contains(newSub)) {
            ToastHelper.showInfo(requireContext(), "Такая подписка уже есть")
            return
        }

        // Мгновенно добавляем в список
        subscriptionsList.add(newSub)
        subscriptionsAdapter.notifyItemInserted(subscriptionsList.size - 1)

        lifecycleScope.launch {
            val result = notificationRepository.addSubscription(type, subType)
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Подписка добавлена")
                }
                is NetworkResult.Error -> {
                    // При ошибке откатываем локальное изменение
                    subscriptionsList.remove(newSub)
                    subscriptionsAdapter.notifyDataSetChanged()
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun removeSubscription(sub: NotificationSettings.Subscription) {
        val index = subscriptionsList.indexOf(sub)
        if (index == -1) return

        // Мгновенно удаляем из списка
        subscriptionsList.removeAt(index)
        subscriptionsAdapter.notifyItemRemoved(index)

        lifecycleScope.launch {
            val result = notificationRepository.removeSubscription(sub.type, sub.subType)
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Подписка удалена")
                }
                is NetworkResult.Error -> {
                    // При ошибке возвращаем обратно
                    subscriptionsList.add(index, sub)
                    subscriptionsAdapter.notifyItemInserted(index)
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun loadSettings() {
        viewModel.setLoading(true)
        lifecycleScope.launch {
            val result = notificationRepository.getNotificationSettings()
            viewModel.setLoading(false)
            when (result) {
                is NetworkResult.Success -> {
                    viewModel.setSettings(result.data)
                    // Обновляем локальный список подписок
                    result.data.subscriptions?.let { subs ->
                        subscriptionsList.clear()
                        subscriptionsList.addAll(subs)
                        subscriptionsAdapter.notifyDataSetChanged()
                    }
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun saveAllSettings() {
        val current = viewModel.getSettings().value
        if (current == null) {
            ToastHelper.showError(requireContext(), "Настройки не загружены")
            return
        }
        current.isNotifyOnResponseToMyAd = binding.switchResponseToMyAd.isChecked
        current.isNotifyOnMyResponseAccepted = binding.switchMyResponseAccepted.isChecked
        current.isNotifyNewAdsInCity = binding.switchNewAdsInCity.isChecked

        viewModel.setLoading(true)
        lifecycleScope.launch {
            val homeCityId = sessionManager.getHomeCityId()?.toIntOrNull()
            current.notificationCityId = homeCityId
            current.notificationCity = null

            val result = notificationRepository.updateNotificationSettings(current)
            viewModel.setLoading(false)
            when (result) {
                is NetworkResult.Success -> {
                    viewModel.setSettings(result.data)
                    ToastHelper.showSuccess(requireContext(), "Настройки сохранены")
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

    private inner class SubscriptionsAdapter(
        private val list: List<NotificationSettings.Subscription>,
        private val removeListener: (NotificationSettings.Subscription) -> Unit
    ) : RecyclerView.Adapter<SubscriptionsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subscription, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sub = list[position]
            holder.tvSubscription.text = getTypeName(sub.type, sub.subType)
            holder.ivRemove.setOnClickListener { removeListener(sub) }
        }

        override fun getItemCount(): Int = list.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvSubscription = itemView.findViewById<TextView>(R.id.tvSubscription)
            val ivRemove = itemView.findViewById<ImageView>(R.id.ivRemove)
        }

        private fun getTypeName(type: Int, subType: Int): String {
            return when (type) {
                1 -> "Ищу игрока: " + if (subType == 1) "Вратаря" else "Полевого"
                2 -> "Ищу лёд: " + if (subType == 1) "Вратарь" else "Полевой"
                3 -> "Товарищеский матч: " + if (subType == 1) "Ищу" else "Предлагаю"
                4 -> {
                    val spec = arrayOf("Судья", "Фотограф", "Медик", "Тренер")
                    "Ищу специалиста: ${spec[subType - 1]}"
                }
                else -> "Тип $type/$subType"
            }
        }
    }

    private fun updateSubscriptionsList(newList: List<NotificationSettings.Subscription>) {
        subscriptionsList.clear()
        subscriptionsList.addAll(newList)
        subscriptionsAdapter.notifyDataSetChanged()
    }
}