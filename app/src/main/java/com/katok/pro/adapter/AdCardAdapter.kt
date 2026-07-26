package com.katok.pro.adapter

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.katok.pro.R
import com.katok.pro.model.Ad
import com.katok.pro.model.Response
import com.katok.pro.model.Rink
import com.katok.pro.util.PhoneUtils
import com.katok.pro.util.SessionManager
import java.util.ArrayList
import java.util.Objects

class AdCardAdapter(
    rinks: List<Rink>?,
    private val listener: OnAdActionListener,
    private val context: Context
) : ListAdapter<Ad, AdCardAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var currentUserId: String? = null
    private var currentUserPhone: String? = null

    fun setCurrentUserId(userId: String?) {
        currentUserId = userId
        notifyDataSetChanged()
    }

    fun setCurrentUserPhone(phone: String?) {
        currentUserPhone = phone
        notifyDataSetChanged()
    }

    fun getCurrentUserId(): String? = currentUserId
    fun getCurrentUserPhone(): String? = currentUserPhone

    interface OnAdActionListener {
        fun onRespondClick(ad: Ad)
        fun onCancelResponseClick(responseId: String, adId: String, authorId: String)
        fun onArchiveClick(adId: String)
        fun onEditClick(adId: String)
        fun onDeleteClick(adId: String)
        fun onUnarchiveClick(adId: String)
        fun onProfileClick(userId: String?, canShowPhone: Boolean, phone: String?)
        fun onConfirmResponseClick(responseId: String, adId: String, userId: String)
        fun onRejectResponseClick(responseId: String, adId: String, userId: String)
        fun onCancelApprovalResponseClick(responseId: String, adId: String, userId: String)

    }

    companion object {
        private const val TAG = "AdCardAdapter"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Ad>() {
            override fun areItemsTheSame(oldItem: Ad, newItem: Ad): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Ad, newItem: Ad): Boolean {
                if (oldItem.status != newItem.status) return false
                if (oldItem.startTime != newItem.startTime) return false
                if (oldItem.endTime != newItem.endTime) return false
                if (oldItem.level != newItem.level) return false
                if (oldItem.rinkIds != newItem.rinkIds) return false
                if (oldItem.goaliesCount != newItem.goaliesCount) return false
                if (oldItem.defendersCount != newItem.defendersCount) return false
                if (oldItem.forwardsCount != newItem.forwardsCount) return false
                if (oldItem.acceptedGoaliesCount != newItem.acceptedGoaliesCount) return false
                if (oldItem.acceptedDefendersCount != newItem.acceptedDefendersCount) return false
                if (oldItem.acceptedForwardsCount != newItem.acceptedForwardsCount) return false
                if (oldItem.team != newItem.team) return false
                if (oldItem.showTeam != newItem.showTeam) return false
                if (oldItem.contactPhone != newItem.contactPhone) return false
                if (oldItem.details != newItem.details) return false

                // Сравнение откликов по содержимому
                val oldResponses = oldItem.responses ?: emptyList()
                val newResponses = newItem.responses ?: emptyList()
                if (oldResponses.size != newResponses.size) return false

                // Сортируем по ID для стабильного сравнения
                val oldSorted = oldResponses.sortedBy { it.id }
                val newSorted = newResponses.sortedBy { it.id }
                for (i in oldSorted.indices) {
                    if (oldSorted[i].id != newSorted[i].id) return false
                    if (oldSorted[i].status != newSorted[i].status) return false
                    if (oldSorted[i].responseRole != newSorted[i].responseRole) return false
                }
                return true
            }
        }
    }

    private var rinks: List<Rink> = rinks ?: ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ad_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val ad = getItem(position)
        val isOwner = currentUserId != null && ad.authorId != null && currentUserId == ad.authorId
        val isArchived = "ARCHIVED" == ad.status
        val isModeration = "MODERATION" == ad.status
        val isFilled = "FILLED" == ad.status
        Log.d("DEBUG_BADGE", "Ad ${ad.id} status: ${ad.status}, isFilled=$isFilled")

/*        Log.d("DEBUG_RINKS", "=== AdCardAdapter ===")
        Log.d("DEBUG_RINKS", "Ad ID: ${ad.id}")
        Log.d("DEBUG_RINKS", "rinkIds: ${ad.rinkIds}")
        Log.d("DEBUG_RINKS", "city: ${ad.city?.id} ${ad.city?.name}")
        Log.d("DEBUG_RINKS", "cityId: ${ad.cityId}")*/

        // Проверка подтверждённого отклика
        var isApprovedResponse = false
        if (currentUserId != null && ad.responses != null) {
            for (r in ad.responses) {
                var rUserId = r.userId
                if (rUserId == null) {
                    r.user?.let { rUserId = it.id }
                }
                if (currentUserId == rUserId && "APPROVED" == r.status) {
                    isApprovedResponse = true
                    break
                }
            }
        }

        val canShowPhone = isOwner || isApprovedResponse

        // Получение номера телефона
        val author = ad.author
        val phoneToShow: String? = ad.contactPhone?.takeIf { it.isNotEmpty() }
            ?: author?.phone?.takeIf { it.isNotEmpty() }
        val phoneForProfile = phoneToShow

        // Цветная полоса
        val stripColor = getUserResponseStatus(ad)
        holder.viewStrip.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, stripColor))

        // Тег
        holder.tvTag.text = getTagText(ad)

        // Бейджи
        holder.llBadges.removeAllViews()

        // Вспомогательная функция для добавления одного бейджа
        fun addBadge(condition: Boolean, text: String, bgColor: Int, textColor: Int) {
            if (!condition) return
            val badge = TextView(holder.itemView.context).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setBackgroundColor(ContextCompat.getColor(holder.itemView.context, bgColor))
                setTextColor(ContextCompat.getColor(holder.itemView.context, textColor))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMarginEnd(dpToPx(4, holder.itemView.context))
                layoutParams = lp
            }
            holder.llBadges.addView(badge)
        }

        addBadge(isOwner, "  Моё  ", R.color.accent, R.color.primary)
        addBadge(isFilled, "  ✅ Набрано  ", R.color.success, R.color.white)
        addBadge(isArchived, "  📦 Архив  ", R.color.gray_light, R.color.text_secondary)
        if (!isArchived) addBadge(isModeration, "  ⏳ На модерации  ", R.color.warning, R.color.warning_text)
        addBadge(ad.isNew, "  🆕 Новое  ", R.color.accent, R.color.white)

        // ЛДС
        val firstRinkId = ad.rinkIds?.firstOrNull()
        val rink = getRinkById(firstRinkId)
        if (rink != null) {
            holder.tvRink.text = rink.name ?: "ЛДС не указан"
            val address = rink.address
            if (!address.isNullOrEmpty()) {
                holder.tvRinkAddress.text = "📍 $address"
                holder.tvRinkAddress.visibility = View.VISIBLE
            } else {
                holder.tvRinkAddress.visibility = View.GONE
            }
        } else {
            holder.tvRink.text = "ЛДС не указан"
            holder.tvRinkAddress.visibility = View.GONE
        }

        // Дата и время – жирным весь текст
        holder.tvDateTime.text = getFormattedDateTime(ad)

        // Уровень – жирным значение
        holder.tvLevel.text = getFormattedLevel(ad)

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
            holder.tvDelivery.text = deliverySpan
            holder.tvDelivery.visibility = View.VISIBLE
        } else {
            holder.tvDelivery.visibility = View.GONE
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
            holder.tvPayment.text = paymentSpan
            holder.tvPayment.visibility = View.VISIBLE
        } else {
            holder.tvPayment.visibility = View.GONE
        }

        // Количество игроков (только тип 1)
        updatePlayersUI(holder, ad)

        // Автор и команда
        val authorName = getAuthorName(ad)
        var teamPart = ""
        if (ad.showTeam == true && !ad.team.isNullOrEmpty()) {
            teamPart = " (ХК ${ad.team})"
        }
        val fullAuthorText = authorName + teamPart
        if (TextUtils.isEmpty(fullAuthorText)) {
            holder.tvAuthor.text = "Пользователь"
        } else {
            val spannable = SpannableString(fullAuthorText)
            if (!TextUtils.isEmpty(authorName)) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD), 0, authorName.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            holder.tvAuthor.text = spannable
        }

        holder.tvAuthor.setOnClickListener {
            listener.onProfileClick(ad.authorId, canShowPhone, phoneForProfile)
        }

        // Телефон в карточке
        if (phoneForProfile != null && canShowPhone) {
            holder.tvPhone.visibility = View.VISIBLE
            val formattedPhone = PhoneUtils.formatPhoneNumberForDisplay(phoneForProfile)
            holder.tvPhone.text = "📞 $formattedPhone"
            val cleanPhone = phoneForProfile.replace("[^\\d+]".toRegex(), "")
            val currentUserPhone = this.currentUserPhone
            val isMyOwnNumber = currentUserPhone != null &&
                    cleanPhone == currentUserPhone.replace("[^\\d+]".toRegex(), "")
            if (!isMyOwnNumber) {
                holder.tvPhone.setOnClickListener {
                    AlertDialog.Builder(holder.itemView.context)
                        .setTitle("Звонок")
                        .setMessage("Позвонить по номеру $formattedPhone?")
                        .setPositiveButton("Позвонить") { _, _ ->
                            val intent = Intent(Intent.ACTION_DIAL)
                            intent.data = Uri.parse("tel:$cleanPhone")
                            holder.itemView.context.startActivity(intent)
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                }
            } else {
                holder.tvPhone.setOnClickListener(null)
                holder.tvPhone.isClickable = false
            }
        } else {
            holder.tvPhone.visibility = View.GONE
        }

        // === НОВОЕ: отображение откликов через вложенный RecyclerView ===
        val newAdapter = ResponsesAdapter(currentUserId,
            object : ResponsesAdapter.OnResponseActionListener {
                override fun onConfirmClick(responseId: String, adId: String, userId: String?) {
                    listener.onConfirmResponseClick(responseId, adId, userId ?: "")
                }
                override fun onCancelApprovalClick(responseId: String, adId: String, userId: String?) {
                    listener.onCancelApprovalResponseClick(responseId, adId, userId ?: "")
                }
                override fun onCancelResponseClick(responseId: String, adId: String, authorId: String?) {
                    listener.onCancelResponseClick(responseId, adId, authorId ?: "")
                }
                override fun onProfileClick(userId: String?, canShowPhone: Boolean, phone: String?) {
                    listener.onProfileClick(userId, canShowPhone, phone)
                }
            }
        )
        newAdapter.setData(ad.responses, ad)
        holder.rvResponses.adapter = newAdapter
        holder.responsesAdapter = newAdapter

// Обязательно сохраняем LayoutManager, если его ещё нет
        if (holder.rvResponses.layoutManager == null) {
            holder.rvResponses.layoutManager = LinearLayoutManager(holder.itemView.context)
        }

// Устанавливаем LayoutManager, если его ещё нет (или можно каждый раз, но это не обязательно)
        if (holder.rvResponses.layoutManager == null) {
            holder.rvResponses.layoutManager = LinearLayoutManager(holder.itemView.context)
        }

        // Управление видимостью блока откликов
        val responses = ad.responses
        if (responses == null || responses.isEmpty() ||
            (!isOwner && !hasVisibleResponses(ad))) {
            holder.rvResponses.visibility = View.GONE
            holder.dividerButtons?.visibility = View.GONE
            holder.tvResponsesHeader.visibility = View.GONE
        } else {
            holder.rvResponses.visibility = View.VISIBLE
            holder.dividerButtons?.visibility = View.VISIBLE
            holder.tvResponsesHeader.visibility = View.VISIBLE
        }

        // Кнопки управления
        setupButtons(ad, holder, isOwner, isArchived, isModeration, isFilled)
    }

    // Вспомогательный метод: есть ли отклики, видимые для пользователя (не владельца)
    private fun hasVisibleResponses(ad: Ad): Boolean {
        val responses = ad.responses ?: return false
        return responses.any { it.status == "APPROVED" }
    }

    private fun addBadge(container: LinearLayout, text: String, bgColor: Int, textColor: Int) {
        val ctx = container.context
        val badge = TextView(ctx)
        badge.text = text
        val padPx = dpToPx(8, ctx)
        val padVertical = dpToPx(4, ctx)
        badge.setPadding(padPx, padVertical, padPx, padVertical)
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        badge.setBackgroundColor(ContextCompat.getColor(ctx, bgColor))
        badge.setTextColor(ContextCompat.getColor(ctx, textColor))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMarginEnd(dpToPx(4, ctx))
        badge.layoutParams = lp
        container.addView(badge)
    }

    private fun getUserResponseStatus(ad: Ad): Int {
        if (ad.responses == null || currentUserId == null) return android.R.color.transparent
        for (r in ad.responses) {
            var rUserId = r.userId
            if (rUserId == null) {
                r.user?.let { rUserId = it.id }
            }
            if (currentUserId == rUserId) {
                if ("APPROVED" == r.status) {
                    return R.color.success
                } else if ("PENDING" == r.status) {
                    return R.color.warning
                }
            }
        }
        return android.R.color.transparent
    }

    private fun getRinkById(rinkId: Int?): Rink? {
        if (rinkId == null) return null
        Log.d("DEBUG_RINKS", "Looking for rinkId=$rinkId, available rinks count=${rinks.size}")
        for (rink in rinks) {
            if (rink.id == rinkId) return rink
        }
        Log.d("DEBUG_RINKS", "Rink not found for id=$rinkId")
        return null
    }

    private fun getTagText(ad: Ad): String {
        val type = ad.type
        val subType = ad.subType
        if (type == 1 && subType == 1) return "НУЖЕН ВРАТАРЬ"
        if (type == 1 && subType == 2) return "НУЖЕН ПОЛЕВОЙ"
        if (type == 2 && subType == 1) return "ИЩУ ЛЕД (ВРАТАРЬ)"
        if (type == 2 && subType == 2) return "ИЩУ ЛЕД (ПОЛЕВОЙ)"
        if (type == 3 && subType == 1) return "ИЩУ ТОВАРИЩЕСКИЙ МАТЧ"
        if (type == 3 && subType == 2) return "ПРЕДЛАГАЮ ТОВАРИЩЕСКИЙ МАТЧ"
        if (type == 4) {
            when (subType) {
                1 -> return "НУЖЕН СУДЬЯ"
                2 -> return "НУЖЕН ФОТОГРАФ"
                3 -> return "НУЖЕН МЕДИК"
                4 -> return "НУЖЕН ТРЕНЕР"
            }
        }
        return ""
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

    private fun getLevelDisplay(ad: Ad): String {
        val level = ad.level
        if (level == null || level.isEmpty()) return "любой"
        return if (level.size == 1) level[0] else TextUtils.join(", ", level)
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

    private fun setupButtons(
        ad: Ad, holder: ViewHolder, isOwner: Boolean,
        isArchived: Boolean, isModeration: Boolean, isFilled: Boolean
    ) {
        var myResponse: Response? = null
        val adResponses = ad.responses
        if (adResponses != null && currentUserId != null) {
            for (r in adResponses) {
                var rUserId = r.userId
                if (rUserId == null) {
                    r.user?.let { rUserId = it.id }
                }
                if (currentUserId == rUserId) {
                    myResponse = r
                    break
                }
            }
        }
        val finalMyResponse = myResponse
        val hasMyResponse = finalMyResponse != null
        val isResponsePending = hasMyResponse && "PENDING" == finalMyResponse.status

        val adId = ad.id?.toString() ?: ""
        val authorId = ad.authorId

        if (!isOwner && !isModeration && !isArchived && !hasMyResponse) {
            holder.btnRespond.visibility = View.VISIBLE
            holder.btnRespond.setOnClickListener {
                listener.onRespondClick(ad)
            }
        } else {
            holder.btnRespond.visibility = View.GONE
        }

        if (!isOwner && hasMyResponse) {
            // Показываем статус
            holder.tvResponseStatus.visibility = View.VISIBLE
            holder.tvResponseStatus.text = if (finalMyResponse.status == "APPROVED") "  Принят..." else "  Ожидание..."

            // Показываем кнопку отмены
            holder.btnCancelResponse.visibility = View.VISIBLE
            holder.btnCancelResponse.setOnClickListener {
                if (finalMyResponse != null) {
                    listener.onCancelResponseClick(finalMyResponse.id ?: "", adId, authorId ?: "")
                }
            }
        } else {
            holder.btnCancelResponse.visibility = View.GONE
            holder.tvResponseStatus.visibility = View.GONE
        }

        // Кнопка архивации/поднятия
        if (isOwner) {
            if (isArchived) {
                // Для архивных объявлений: показываем "Поднять" если время не истекло
                val isExpired = ad.endTime?.let { endTimeStr ->
                    try {
                        val endDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).parse(endTimeStr)
                        endDate != null && endDate.before(java.util.Date())
                    } catch (e: Exception) {
                        false
                    }
                } ?: false // если endTime null, считаем не истекло
                if (!isExpired) {
                    holder.btnArchive.visibility = View.VISIBLE
                    holder.btnArchive.text = "Разместить"
                    holder.btnArchive.setOnClickListener {
                        listener.onUnarchiveClick(adId)
                    }
                } else {
                    holder.btnArchive.visibility = View.GONE
                }
            } else if (!isModeration && !isFilled) {
                // Для активных объявлений: "В архив"
                holder.btnArchive.visibility = View.VISIBLE
                holder.btnArchive.text = "📦 В архив"
                holder.btnArchive.setOnClickListener {
                    listener.onArchiveClick(adId)
                }
            } else {
                holder.btnArchive.visibility = View.GONE
            }
        } else {
            holder.btnArchive.visibility = View.GONE
        }

        if (isOwner && !isArchived && !isFilled) {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnEdit.setOnClickListener {
                listener.onEditClick(adId)
            }
        } else {
            holder.btnEdit.visibility = View.GONE
        }

        if (isOwner) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener {
                listener.onDeleteClick(adId)
            }
        } else {
            holder.btnDelete.visibility = View.GONE
        }

        val hasAnyButton = holder.btnRespond.visibility == View.VISIBLE ||
                holder.btnCancelResponse.visibility == View.VISIBLE ||
                holder.btnArchive.visibility == View.VISIBLE ||
                holder.btnEdit.visibility == View.VISIBLE ||
                holder.btnDelete.visibility == View.VISIBLE
        holder.llButtons.visibility = if (hasAnyButton) View.VISIBLE else View.GONE
    }

    fun updateRinks(newRinks: List<Rink>?) {
        this.rinks = newRinks ?: ArrayList()
    }

    fun getRinks(): List<Rink> {
        return rinks
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val viewStrip: View = itemView.findViewById(R.id.view_strip)
        val tvTag: TextView = itemView.findViewById(R.id.tv_tag)
        val llBadges: LinearLayout = itemView.findViewById(R.id.ll_badges)
        val tvRink: TextView = itemView.findViewById(R.id.tv_rink)
        val tvRinkAddress: TextView = itemView.findViewById(R.id.tv_rink_address)
        val tvDateTime: TextView = itemView.findViewById(R.id.tv_datetime)
        val tvLevel: TextView = itemView.findViewById(R.id.tv_level)
        val tvDelivery: TextView = itemView.findViewById(R.id.tv_delivery)
        val tvPayment: TextView = itemView.findViewById(R.id.tv_payment)
        val tvAuthor: TextView = itemView.findViewById(R.id.tv_author)
        val tvTeam: TextView = itemView.findViewById(R.id.tv_team)
        val tvPhone: TextView = itemView.findViewById(R.id.tv_phone)
        val layoutPlayersNeeded: LinearLayout = itemView.findViewById(R.id.layout_players_needed)
        val tvGoalies: TextView = itemView.findViewById(R.id.tv_goalies)
        val tvDefenders: TextView = itemView.findViewById(R.id.tv_defenders)
        val tvForwards: TextView = itemView.findViewById(R.id.tv_forwards)
        val tvResponsesHeader: TextView = itemView.findViewById(R.id.tvResponsesHeader)
        val llButtons: LinearLayout = itemView.findViewById(R.id.ll_buttons)
        val btnRespond: Button = itemView.findViewById(R.id.btn_respond)
        val btnCancelResponse: Button = itemView.findViewById(R.id.btn_cancel_response)
        val btnArchive: Button = itemView.findViewById(R.id.btn_archive)
        val btnEdit: Button = itemView.findViewById(R.id.btn_edit)
        val btnDelete: Button = itemView.findViewById(R.id.btn_delete)
        val dividerButtons: View? = itemView.findViewById(R.id.divider_buttons)
        val rvResponses: RecyclerView = itemView.findViewById(R.id.rvResponses)
        var responsesAdapter: ResponsesAdapter? = null

        val tvResponseStatus: TextView = itemView.findViewById(R.id.tv_response_status)
    }

    private fun dpToPx(dp: Int, ctx: Context): Int {
        return (dp * ctx.resources.displayMetrics.density).toInt()
    }

    private fun getFormattedDateTime(ad: Ad): SpannableString {
        val text = formatDateTime(ad)
        val spannable = SpannableString(text)
        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    private fun getFormattedLevel(ad: Ad): SpannableString {
        val levelText = "Уровень: " + getLevelDisplay(ad)
        val spannable = SpannableString(levelText)
        val colonIndex = levelText.indexOf(":") + 1
        if (colonIndex > 0 && colonIndex < levelText.length) {
            spannable.setSpan(StyleSpan(Typeface.BOLD), colonIndex, levelText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun updatePlayersUI(holder: ViewHolder, ad: Ad) {
        if (ad.type != 1) {
            holder.layoutPlayersNeeded.visibility = View.GONE
            return
        }
        holder.layoutPlayersNeeded.visibility = View.VISIBLE
        when {
            ad.subType == 1 && ad.goaliesCount != null -> {
                val accepted = ad.acceptedGoaliesCount ?: 0
                val text = "Вратари: $accepted/${ad.goaliesCount}"
                val spannable = SpannableString(text)
                val start = text.indexOf(":") + 2
                spannable.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                holder.tvGoalies.text = spannable
                holder.tvGoalies.visibility = View.VISIBLE
                holder.tvDefenders.visibility = View.GONE
                holder.tvForwards.visibility = View.GONE
            }
            ad.subType == 2 -> {
                holder.tvGoalies.visibility = View.GONE
                if (ad.defendersCount != null) {
                    val accepted = ad.acceptedDefendersCount ?: 0
                    val text = "Защитники: $accepted/${ad.defendersCount}"
                    val spannable = SpannableString(text)
                    val start = text.indexOf(":") + 2
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    holder.tvDefenders.text = spannable
                    holder.tvDefenders.visibility = View.VISIBLE
                } else {
                    holder.tvDefenders.visibility = View.GONE
                }
                if (ad.forwardsCount != null) {
                    val accepted = ad.acceptedForwardsCount ?: 0
                    val text = "Нападающие: $accepted/${ad.forwardsCount}"
                    val spannable = SpannableString(text)
                    val start = text.indexOf(":") + 2
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    holder.tvForwards.text = spannable
                    holder.tvForwards.visibility = View.VISIBLE
                } else {
                    holder.tvForwards.visibility = View.GONE
                }
            }
            else -> {
                holder.layoutPlayersNeeded.visibility = View.GONE
            }
        }
    }


}