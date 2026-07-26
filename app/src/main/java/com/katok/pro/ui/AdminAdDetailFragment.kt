package com.katok.pro.ui

import android.os.Bundle
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.text.Spannable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.katok.pro.R
import com.katok.pro.adapter.ResponsesAdapter
import com.katok.pro.databinding.FragmentAdminAdDetailBinding
import com.katok.pro.model.Ad
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.Response
import com.katok.pro.model.Rink
import com.katok.pro.repository.AdRepository
import com.katok.pro.repository.AdminRepository
import com.katok.pro.util.PhoneUtils
import com.katok.pro.util.ToastHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AdminAdDetailFragment : BaseFragment(R.layout.fragment_admin_ad_detail) {

    private var _binding: FragmentAdminAdDetailBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var adRepository: AdRepository
    @Inject lateinit var adminRepository: AdminRepository

    private var adId: String? = null
    private var currentAd: Ad? = null
    private var rinkCache: List<Rink> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            adId = it.getString("adId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminAdDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        loadAd()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            NavHostFragment.findNavController(this).navigateUp()
        }

        binding.btnArchive.setOnClickListener {
            currentAd?.let { ad ->
                val action = if (ad.status == "ARCHIVED") "Поднять" else "Архивировать"
                AlertDialog.Builder(requireContext())
                    .setTitle("Подтверждение")
                    .setMessage("Вы уверены, что хотите $action это объявление?")
                    .setPositiveButton("Да") { _, _ ->
                        archiveOrUnarchiveAd(ad.id.toString())
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }

        binding.btnEdit.setOnClickListener {
            val args = Bundle().apply {
                putString("adId", adId)
            }
            NavHostFragment.findNavController(this)
                .navigate(R.id.navigation_create, args)
        }

        binding.btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Удаление объявления")
                .setMessage("Вы уверены, что хотите удалить это объявление? Действие необратимо.")
                .setPositiveButton("Удалить") { _, _ ->
                    deleteAd()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun loadAd() {
        if (adId == null) {
            ToastHelper.showError(requireContext(), "ID объявления не указан")
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = adRepository.getAdById(adId!!)
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    currentAd = result.data
                    // Загружаем стадионы для отображения названия и адреса
                    loadRinks(currentAd!!)
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun loadRinks(ad: Ad) {
        val cityId = ad.city?.id ?: ad.cityId
        if (cityId == null || cityId == 0) {
            displayAd(ad, emptyList())
            return
        }
        lifecycleScope.launch {
            val result = adRepository.getRinksByCity(cityId)
            if (result is NetworkResult.Success) {
                rinkCache = result.data
                displayAd(ad, rinkCache)
            } else {
                displayAd(ad, emptyList())
            }
        }
    }

    private fun displayAd(ad: Ad, rinks: List<Rink>) {
        // Цветная полоса (показываем, если есть отклик пользователя – но в админке можно убрать)
        // Можно показать цвет по статусу, например
        val stripColor = when (ad.status) {
            "ACTIVE" -> R.color.success
            "FILLED" -> R.color.warning
            "ARCHIVED" -> R.color.gray_medium
            "MODERATION" -> R.color.warning
            else -> android.R.color.transparent
        }
        binding.viewStrip.setBackgroundColor(ContextCompat.getColor(requireContext(), stripColor))

        // Тег
        binding.tvTag.text = ad.getTagText()

        // Бейджи
        binding.llBadges.removeAllViews()
        addBadge(ad.status == "FILLED", "  ✅ Набрано  ", R.color.success, R.color.white)
        addBadge(ad.status == "ARCHIVED", "  📦 Архив  ", R.color.gray_light, R.color.text_secondary)
        if (ad.status != "ARCHIVED") {
            addBadge(ad.status == "MODERATION", "  ⏳ На модерации  ", R.color.warning, R.color.warning_text)
        }
        addBadge(ad.isNew, "  🆕 Новое  ", R.color.accent, R.color.white)

        // ЛДС
        val firstRinkId = ad.rinkIds?.firstOrNull()
        val rink = rinks.find { it.id == firstRinkId }
        if (rink != null) {
            binding.tvRink.text = rink.name ?: "ЛДС не указан"
            val address = rink.address
            if (!address.isNullOrEmpty()) {
                binding.tvRinkAddress.text = "📍 $address"
                binding.tvRinkAddress.visibility = View.VISIBLE
            } else {
                binding.tvRinkAddress.visibility = View.GONE
            }
        } else {
            binding.tvRink.text = "ЛДС не указан"
            binding.tvRinkAddress.visibility = View.GONE
        }

        // Дата и время (жирным)
        binding.tvDateTime.text = getFormattedDateTime(ad)

        // Уровень (жирным значение)
        binding.tvLevel.text = getFormattedLevel(ad)

        // Доставка
        val details = ad.details
        if (details?.delivery == "true") {
            val deliveryText = "🚗 Доставка есть"
            val deliverySpan = SpannableString(deliveryText)
            val wordIndex = deliveryText.indexOf("есть")
            if (wordIndex >= 0) {
                deliverySpan.setSpan(
                    StyleSpan(Typeface.BOLD), wordIndex, wordIndex + 4,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.tvDelivery.text = deliverySpan
            binding.tvDelivery.visibility = View.VISIBLE
        } else {
            binding.tvDelivery.visibility = View.GONE
        }

        // Оплата
        if (!details?.payment.isNullOrEmpty()) {
            val paymentText = "💰 Оплата: ${details!!.payment}"
            val paymentSpan = SpannableString(paymentText)
            val payColon = paymentText.indexOf(":") + 1
            if (payColon > 0 && payColon < paymentText.length) {
                paymentSpan.setSpan(
                    StyleSpan(Typeface.BOLD), payColon, paymentText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.tvPayment.text = paymentSpan
            binding.tvPayment.visibility = View.VISIBLE
        } else {
            binding.tvPayment.visibility = View.GONE
        }

        // Количество игроков (только тип 1)
        updatePlayersUI(ad)

        // Автор
        val authorName = getAuthorName(ad)
        var teamPart = ""
        if (ad.showTeam == true && !ad.team.isNullOrEmpty()) {
            teamPart = " (ХК ${ad.team})"
        }
        val fullAuthorText = authorName + teamPart
        if (TextUtils.isEmpty(fullAuthorText)) {
            binding.tvAuthor.text = "Пользователь"
        } else {
            val spannable = SpannableString(fullAuthorText)
            if (!TextUtils.isEmpty(authorName)) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD), 0, authorName.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.tvAuthor.text = spannable
        }

        // Телефон (показываем всегда в админке)
        val phoneToShow = ad.contactPhone?.takeIf { it.isNotEmpty() }
            ?: ad.author?.phone?.takeIf { it.isNotEmpty() }
        if (!phoneToShow.isNullOrEmpty()) {
            binding.tvPhone.visibility = View.VISIBLE
            binding.tvPhone.text = "📞 ${PhoneUtils.formatPhoneNumberForDisplay(phoneToShow)}"
        } else {
            binding.tvPhone.visibility = View.GONE
        }

        // Отклики
        val responses = ad.responses ?: emptyList()
        if (responses.isNotEmpty()) {
            binding.tvResponsesHeader.visibility = View.VISIBLE
            binding.rvResponses.visibility = View.VISIBLE
            // Для админа показываем все отклики, передаём authorId как currentUserId, чтобы адаптер думал, что это владелец
            val adapter = ResponsesAdapter(
                currentUserId = ad.authorId, // делаем вид, что это владелец
                listener = object : ResponsesAdapter.OnResponseActionListener {
                    // Для админа кнопки действий не нужны, реализуем заглушками
                    override fun onConfirmClick(responseId: String, adId: String, userId: String?) {}
                    override fun onCancelApprovalClick(responseId: String, adId: String, userId: String?) {}
                    override fun onCancelResponseClick(responseId: String, adId: String, authorId: String?) {}
                    override fun onProfileClick(userId: String?, canShowPhone: Boolean, phone: String?) {
                        // Можно перейти к просмотру профиля, если нужно
                    }
                }
            )
            adapter.setData(responses, ad)
            binding.rvResponses.layoutManager = LinearLayoutManager(requireContext())
            binding.rvResponses.adapter = adapter
        } else {
            binding.tvResponsesHeader.visibility = View.GONE
            binding.rvResponses.visibility = View.GONE
        }

        // Кнопка архива
        binding.btnArchive.text = if (ad.status == "ARCHIVED") "Поднять" else "В архив"
    }

    private fun addBadge(condition: Boolean, text: String, bgColor: Int, textColor: Int) {
        if (!condition) return
        val badge = TextView(requireContext()).apply {
            this.text = text
            setTextSize(10f)
            setBackgroundColor(ContextCompat.getColor(requireContext(), bgColor))
            setTextColor(ContextCompat.getColor(requireContext(), textColor))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMarginEnd(dpToPx(4))
            layoutParams = lp
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
        }
        binding.llBadges.addView(badge)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * requireContext().resources.displayMetrics.density).toInt()
    }

    private fun getFormattedDateTime(ad: Ad): SpannableString {
        val text = formatDateTime(ad)
        val spannable = SpannableString(text)
        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    private fun formatDateTime(ad: Ad): String {
        val startTime = ad.startTime
        if (startTime == null) return "Дата не указана"
        return try {
            val parts = startTime.split("T")
            val datePart = parts[0]
            val ymd = datePart.split("-")
            val formattedDate = "${ymd[2]}.${ymd[1]}.${ymd[0]}"
            val startTimePart = parts[1].substring(0, 5)

            val type = ad.type
            val subType = ad.subType
            val showRange = type == 2 || (type == 3 && subType == 1)

            val endTimeRaw = ad.endTime
            if (showRange && endTimeRaw != null) {
                var endTimePart = endTimeRaw
                if (endTimePart.contains("T")) endTimePart = endTimePart.split("T")[1]
                if (endTimePart.startsWith("23:59")) {
                    return "$formattedDate Время: любое"
                } else {
                    val endTime = if (endTimePart.length > 5) endTimePart.substring(0, 5) else endTimePart
                    return "$formattedDate Время: $startTimePart - $endTime"
                }
            } else {
                "$formattedDate Время: $startTimePart"
            }
        } catch (e: Exception) {
            startTime
        }
    }

    private fun getFormattedLevel(ad: Ad): SpannableString {
        val levelDisplay = ad.level?.let { if (it.size == 1) it[0] else TextUtils.join(", ", it) } ?: "любой"
        val levelText = "Уровень: $levelDisplay"
        val spannable = SpannableString(levelText)
        val colonIndex = levelText.indexOf(":") + 1
        if (colonIndex > 0 && colonIndex < levelText.length) {
            spannable.setSpan(StyleSpan(Typeface.BOLD), colonIndex, levelText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun getAuthorName(ad: Ad): String {
        val author = ad.author
        val profile = author?.profile
        if (profile != null) {
            val firstName = profile.firstName
            val lastName = profile.lastName
            if (!firstName.isNullOrEmpty()) {
                return firstName + (if (!lastName.isNullOrEmpty()) " $lastName" else "")
            }
            if (!lastName.isNullOrEmpty()) {
                return lastName
            }
        }
        return ""
    }

    private fun updatePlayersUI(ad: Ad) {
        if (ad.type != 1) {
            binding.layoutPlayersNeeded.visibility = View.GONE
            return
        }
        binding.layoutPlayersNeeded.visibility = View.VISIBLE
        when {
            ad.subType == 1 && ad.goaliesCount != null -> {
                val accepted = ad.acceptedGoaliesCount ?: 0
                val text = "Вратари: $accepted/${ad.goaliesCount}"
                val spannable = SpannableString(text)
                val start = text.indexOf(":") + 2
                spannable.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                binding.tvGoalies.text = spannable
                binding.tvGoalies.visibility = View.VISIBLE
                binding.tvDefenders.visibility = View.GONE
                binding.tvForwards.visibility = View.GONE
            }
            ad.subType == 2 -> {
                binding.tvGoalies.visibility = View.GONE
                if (ad.defendersCount != null) {
                    val accepted = ad.acceptedDefendersCount ?: 0
                    val text = "Защитники: $accepted/${ad.defendersCount}"
                    val spannable = SpannableString(text)
                    val start = text.indexOf(":") + 2
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    binding.tvDefenders.text = spannable
                    binding.tvDefenders.visibility = View.VISIBLE
                } else {
                    binding.tvDefenders.visibility = View.GONE
                }
                if (ad.forwardsCount != null) {
                    val accepted = ad.acceptedForwardsCount ?: 0
                    val text = "Нападающие: $accepted/${ad.forwardsCount}"
                    val spannable = SpannableString(text)
                    val start = text.indexOf(":") + 2
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    binding.tvForwards.text = spannable
                    binding.tvForwards.visibility = View.VISIBLE
                } else {
                    binding.tvForwards.visibility = View.GONE
                }
            }
            else -> {
                binding.layoutPlayersNeeded.visibility = View.GONE
            }
        }
    }

    private fun archiveOrUnarchiveAd(adId: String) {
        val newStatus = if (currentAd?.status == "ARCHIVED") "ACTIVE" else "ARCHIVED"
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val ad = Ad().apply { status = newStatus }
            val result = adRepository.updateAd(adId, ad)
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Статус изменён")
                    loadAd() // перезагружаем
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun deleteAd() {
        if (adId == null) return
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = adminRepository.deleteAd(adId!!)
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), "Объявление удалено")
                    // Переход назад к списку
                    NavHostFragment.findNavController(this@AdminAdDetailFragment).navigateUp()
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
}