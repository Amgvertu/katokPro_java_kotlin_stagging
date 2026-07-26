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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.katok.pro.R
import com.katok.pro.model.Ad
import com.katok.pro.model.Advertising
import com.katok.pro.model.Response
import com.katok.pro.model.Rink
import com.katok.pro.network.ApiClient
import com.katok.pro.util.PhoneUtils
import java.util.*

class FeedAdapter(
    private val context: Context,
    private val adListener: AdCardAdapter.OnAdActionListener,
    private val onAdvertClick: (Advertising) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_AD = 0
        private const val TYPE_ADVERT = 1
        private const val TAG = "FeedAdapter"
    }

    private var items: List<Any> = emptyList()
    private var currentUserId: String? = null
    private var currentUserPhone: String? = null
    private var rinks: List<Rink> = emptyList()

    fun setCurrentUserId(userId: String?) {
        currentUserId = userId
        notifyDataSetChanged()
    }

    fun setCurrentUserPhone(phone: String?) {
        currentUserPhone = phone
        notifyDataSetChanged()
    }

    fun updateRinks(newRinks: List<Rink>) {
        rinks = newRinks
        Log.d("FeedAdapter", "updateRinks: received ${newRinks.size} rinks")
        notifyDataSetChanged()
    }

    fun submitList(newItems: List<Any>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is Ad) TYPE_AD else TYPE_ADVERT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_AD) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ad_card, parent, false)
            AdViewHolder(view, adListener, currentUserId, currentUserPhone, rinks)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_advertising_card, parent, false)
            AdvertViewHolder(view, onAdvertClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is AdViewHolder -> {
                holder.updateUserData(currentUserId, currentUserPhone, rinks)
                holder.bind(item as Ad)
            }
            is AdvertViewHolder -> holder.bind(item as Advertising)
        }
    }

    override fun getItemCount(): Int = items.size

    // ---------- ViewHolder для объявлений (копия из AdCardAdapter) ----------
    class AdViewHolder(
        itemView: View,
        private val listener: AdCardAdapter.OnAdActionListener,
        private var currentUserId: String?,
        private var currentUserPhone: String?,
        private var rinks: List<Rink>
    ) : RecyclerView.ViewHolder(itemView) {

        // Все View, как в item_ad_card.xml
        private val viewStrip: View = itemView.findViewById(R.id.view_strip)
        private val tvTag: TextView = itemView.findViewById(R.id.tv_tag)
        private val llBadges: LinearLayout = itemView.findViewById(R.id.ll_badges)
        private val tvRink: TextView = itemView.findViewById(R.id.tv_rink)
        private val tvRinkAddress: TextView = itemView.findViewById(R.id.tv_rink_address)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tv_datetime)
        private val tvLevel: TextView = itemView.findViewById(R.id.tv_level)
        private val tvDelivery: TextView = itemView.findViewById(R.id.tv_delivery)
        private val tvPayment: TextView = itemView.findViewById(R.id.tv_payment)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tv_author)
        private val tvPhone: TextView = itemView.findViewById(R.id.tv_phone)
        private val layoutPlayersNeeded: LinearLayout = itemView.findViewById(R.id.layout_players_needed)
        private val tvGoalies: TextView = itemView.findViewById(R.id.tv_goalies)
        private val tvDefenders: TextView = itemView.findViewById(R.id.tv_defenders)
        private val tvForwards: TextView = itemView.findViewById(R.id.tv_forwards)
        private val tvResponsesHeader: TextView = itemView.findViewById(R.id.tvResponsesHeader)
        private val llButtons: LinearLayout = itemView.findViewById(R.id.ll_buttons)
        private val btnRespond: Button = itemView.findViewById(R.id.btn_respond)
        private val btnCancelResponse: Button = itemView.findViewById(R.id.btn_cancel_response)
        private val btnArchive: Button = itemView.findViewById(R.id.btn_archive)
        private val btnEdit: Button = itemView.findViewById(R.id.btn_edit)
        private val btnDelete: Button = itemView.findViewById(R.id.btn_delete)
        private val dividerButtons: View? = itemView.findViewById(R.id.divider_buttons)
        private val rvResponses: RecyclerView = itemView.findViewById(R.id.rvResponses)
        private val tvResponseStatus: TextView = itemView.findViewById(R.id.tv_response_status)

        fun updateUserData(userId: String?, phone: String?, newRinks: List<Rink>) {
            currentUserId = userId
            currentUserPhone = phone
            rinks = newRinks
        }

        fun bind(ad: Ad) {
            val isOwner = currentUserId != null && ad.authorId != null && currentUserId == ad.authorId
            val isArchived = "ARCHIVED" == ad.status
            val isModeration = "MODERATION" == ad.status
            val isFilled = "FILLED" == ad.status

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
            val phoneToShow: String? = ad.contactPhone?.takeIf { it.isNotEmpty() }
                ?: ad.author?.phone?.takeIf { it.isNotEmpty() }
            val phoneForProfile = phoneToShow

            // Цветная полоса
            val stripColor = getUserResponseStatus(ad)
            viewStrip.setBackgroundColor(ContextCompat.getColor(itemView.context, stripColor))

            // Тег
            tvTag.text = getTagText(ad)

            // Бейджи
            llBadges.removeAllViews()
            addBadge(llBadges, isOwner, "  Моё  ", R.color.accent, R.color.primary)
            addBadge(llBadges, isFilled, "  ✅ Набрано  ", R.color.success, R.color.white)
            addBadge(llBadges, isArchived, "  📦 Архив  ", R.color.gray_light, R.color.text_secondary)
            if (!isArchived) addBadge(llBadges, isModeration, "  ⏳ На модерации  ", R.color.warning, R.color.warning_text)
            addBadge(llBadges, ad.isNew, "  🆕 Новое  ", R.color.accent, R.color.white)

            // ЛДС
            val firstRinkId = ad.rinkIds?.firstOrNull()
            Log.d("FeedAdapter", "bind: adId=${ad.id}, rinkId=$firstRinkId, rinks size=${rinks.size}")
            val rink = getRinkById(firstRinkId)
            if (rink != null) {
                tvRink.text = rink.name ?: "ЛДС не указан"
                val address = rink.address
                if (!address.isNullOrEmpty()) {
                    tvRinkAddress.text = "📍 $address"
                    tvRinkAddress.visibility = View.VISIBLE
                } else {
                    tvRinkAddress.visibility = View.GONE
                }
            } else {
                tvRink.text = "ЛДС не указан"
                tvRinkAddress.visibility = View.GONE
            }

            // Дата и время – жирным
            tvDateTime.text = getFormattedDateTime(ad)

            // Уровень – жирным значение
            tvLevel.text = getFormattedLevel(ad)

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
                tvDelivery.text = deliverySpan
                tvDelivery.visibility = View.VISIBLE
            } else {
                tvDelivery.visibility = View.GONE
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
                tvPayment.text = paymentSpan
                tvPayment.visibility = View.VISIBLE
            } else {
                tvPayment.visibility = View.GONE
            }

            // Количество игроков
            updatePlayersUI(ad)

            // Автор и команда
            val authorName = getAuthorName(ad)
            var teamPart = ""
            if (ad.showTeam == true && !ad.team.isNullOrEmpty()) {
                teamPart = " (ХК ${ad.team})"
            }
            val fullAuthorText = authorName + teamPart
            if (TextUtils.isEmpty(fullAuthorText)) {
                tvAuthor.text = "Пользователь"
            } else {
                val spannable = SpannableString(fullAuthorText)
                if (!TextUtils.isEmpty(authorName)) {
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD), 0, authorName.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                tvAuthor.text = spannable
            }

            tvAuthor.setOnClickListener {
                listener.onProfileClick(ad.authorId, canShowPhone, phoneForProfile)
            }

            // Телефон
            if (phoneForProfile != null && canShowPhone) {
                tvPhone.visibility = View.VISIBLE
                val formattedPhone = PhoneUtils.formatPhoneNumberForDisplay(phoneForProfile)
                tvPhone.text = "📞 $formattedPhone"
                val cleanPhone = phoneForProfile.replace("[^\\d+]".toRegex(), "")
                val phoneForComparison = currentUserPhone
                val isMyOwnNumber = phoneForComparison != null &&
                        cleanPhone == phoneForComparison.replace("[^\\d+]".toRegex(), "")
                if (!isMyOwnNumber) {
                    tvPhone.setOnClickListener {
                        AlertDialog.Builder(itemView.context)
                            .setTitle("Звонок")
                            .setMessage("Позвонить по номеру $formattedPhone?")
                            .setPositiveButton("Позвонить") { _, _ ->
                                val intent = Intent(Intent.ACTION_DIAL)
                                intent.data = Uri.parse("tel:$cleanPhone")
                                itemView.context.startActivity(intent)
                            }
                            .setNegativeButton("Отмена", null)
                            .show()
                    }
                } else {
                    tvPhone.setOnClickListener(null)
                    tvPhone.isClickable = false
                }
            } else {
                tvPhone.visibility = View.GONE
            }

            // Отклики – используем ResponsesAdapter
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
            rvResponses.adapter = newAdapter
            if (rvResponses.layoutManager == null) {
                rvResponses.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(itemView.context)
            }

            val responses = ad.responses
            if (responses == null || responses.isEmpty() ||
                (!isOwner && !hasVisibleResponses(ad))) {
                rvResponses.visibility = View.GONE
                dividerButtons?.visibility = View.GONE
                tvResponsesHeader.visibility = View.GONE
            } else {
                rvResponses.visibility = View.VISIBLE
                dividerButtons?.visibility = View.VISIBLE
                tvResponsesHeader.visibility = View.VISIBLE
            }

            // Кнопки управления
            setupButtons(ad, isOwner, isArchived, isModeration, isFilled)
        }

        // Вспомогательные методы (копия из AdCardAdapter)
        private fun getRinkById(rinkId: Int?): Rink? {
            if (rinkId == null) return null
            return rinks.find { it.id == rinkId }
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

        private fun addBadge(container: LinearLayout, condition: Boolean, text: String, bgColor: Int, textColor: Int) {
            if (!condition) return
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

        private fun dpToPx(dp: Int, ctx: Context): Int {
            return (dp * ctx.resources.displayMetrics.density).toInt()
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

        private fun hasVisibleResponses(ad: Ad): Boolean {
            val responses = ad.responses ?: return false
            return responses.any { it.status == "APPROVED" }
        }

        private fun updatePlayersUI(ad: Ad) {
            if (ad.type != 1) {
                layoutPlayersNeeded.visibility = View.GONE
                return
            }
            layoutPlayersNeeded.visibility = View.VISIBLE
            when {
                ad.subType == 1 && ad.goaliesCount != null -> {
                    val accepted = ad.acceptedGoaliesCount ?: 0
                    val text = "Вратари: $accepted/${ad.goaliesCount}"
                    val spannable = SpannableString(text)
                    val start = text.indexOf(":") + 2
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    tvGoalies.text = spannable
                    tvGoalies.visibility = View.VISIBLE
                    tvDefenders.visibility = View.GONE
                    tvForwards.visibility = View.GONE
                }
                ad.subType == 2 -> {
                    tvGoalies.visibility = View.GONE
                    if (ad.defendersCount != null) {
                        val accepted = ad.acceptedDefendersCount ?: 0
                        val text = "Защитники: $accepted/${ad.defendersCount}"
                        val spannable = SpannableString(text)
                        val start = text.indexOf(":") + 2
                        spannable.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        tvDefenders.text = spannable
                        tvDefenders.visibility = View.VISIBLE
                    } else {
                        tvDefenders.visibility = View.GONE
                    }
                    if (ad.forwardsCount != null) {
                        val accepted = ad.acceptedForwardsCount ?: 0
                        val text = "Нападающие: $accepted/${ad.forwardsCount}"
                        val spannable = SpannableString(text)
                        val start = text.indexOf(":") + 2
                        spannable.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        tvForwards.text = spannable
                        tvForwards.visibility = View.VISIBLE
                    } else {
                        tvForwards.visibility = View.GONE
                    }
                }
                else -> {
                    layoutPlayersNeeded.visibility = View.GONE
                }
            }
        }

        private fun setupButtons(ad: Ad, isOwner: Boolean, isArchived: Boolean, isModeration: Boolean, isFilled: Boolean) {
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

            val adId = ad.id?.toString() ?: ""
            val authorId = ad.authorId

            if (!isOwner && !isModeration && !isArchived && !hasMyResponse) {
                btnRespond.visibility = View.VISIBLE
                btnRespond.setOnClickListener {
                    listener.onRespondClick(ad)
                }
            } else {
                btnRespond.visibility = View.GONE
            }

            if (!isOwner && hasMyResponse) {
                tvResponseStatus.visibility = View.VISIBLE
                tvResponseStatus.text = if (finalMyResponse.status == "APPROVED") "  Принят..." else "  Ожидание..."
                btnCancelResponse.visibility = View.VISIBLE
                btnCancelResponse.setOnClickListener {
                    if (finalMyResponse != null) {
                        listener.onCancelResponseClick(finalMyResponse.id ?: "", adId, authorId ?: "")
                    }
                }
            } else {
                btnCancelResponse.visibility = View.GONE
                tvResponseStatus.visibility = View.GONE
            }

            // Архив
            if (isOwner) {
                if (isArchived) {
                    val isExpired = ad.endTime?.let { endTimeStr ->
                        try {
                            val endDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).parse(endTimeStr)
                            endDate != null && endDate.before(java.util.Date())
                        } catch (e: Exception) {
                            false
                        }
                    } ?: false
                    if (!isExpired) {
                        btnArchive.visibility = View.VISIBLE
                        btnArchive.text = "Разместить"
                        btnArchive.setOnClickListener {
                            listener.onUnarchiveClick(adId)
                        }
                    } else {
                        btnArchive.visibility = View.GONE
                    }
                } else if (!isModeration && !isFilled) {
                    btnArchive.visibility = View.VISIBLE
                    btnArchive.text = "📦 В архив"
                    btnArchive.setOnClickListener {
                        listener.onArchiveClick(adId)
                    }
                } else {
                    btnArchive.visibility = View.GONE
                }
            } else {
                btnArchive.visibility = View.GONE
            }

            // Редактировать
            if (isOwner && !isArchived && !isFilled) {
                btnEdit.visibility = View.VISIBLE
                btnEdit.setOnClickListener {
                    listener.onEditClick(adId)
                }
            } else {
                btnEdit.visibility = View.GONE
            }

            // Удалить
            if (isOwner) {
                btnDelete.visibility = View.VISIBLE
                btnDelete.setOnClickListener {
                    listener.onDeleteClick(adId)
                }
            } else {
                btnDelete.visibility = View.GONE
            }

            val hasAnyButton = btnRespond.visibility == View.VISIBLE ||
                    btnCancelResponse.visibility == View.VISIBLE ||
                    btnArchive.visibility == View.VISIBLE ||
                    btnEdit.visibility == View.VISIBLE ||
                    btnDelete.visibility == View.VISIBLE
            llButtons.visibility = if (hasAnyButton) View.VISIBLE else View.GONE
        }
    }

    // ---------- ViewHolder для рекламы ----------
    class AdvertViewHolder(
        itemView: View,
        private val onAdvertClick: (Advertising) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivAdImage: ImageView = itemView.findViewById(R.id.ivAdImage)

        fun bind(advert: Advertising) {
            val normalizedUrl = ApiClient.normalizeResourceUrl(advert.imageUrl)
            Glide.with(itemView.context)
                .load(normalizedUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_broken_image)
                .into(ivAdImage)

            // Устанавливаем слушатель на ImageView
            ivAdImage.setOnClickListener {
                Log.d("AdvertViewHolder", "Реклама нажата, ссылка: ${advert.link}")
                onAdvertClick(advert)
            }
            // Отключаем слушатель на itemView, чтобы не было конфликтов
            itemView.setOnClickListener(null)
        }
    }

}