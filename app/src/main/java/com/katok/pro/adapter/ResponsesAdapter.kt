package com.katok.pro.adapter

import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.katok.pro.R
import com.katok.pro.model.Ad
import com.katok.pro.model.Response

class ResponsesAdapter(
    private val currentUserId: String?,
    private val listener: OnResponseActionListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface OnResponseActionListener {
        fun onConfirmClick(responseId: String, adId: String, userId: String?)
        fun onCancelApprovalClick(responseId: String, adId: String, userId: String?)
        fun onCancelResponseClick(responseId: String, adId: String, authorId: String?)
        fun onProfileClick(userId: String?, canShowPhone: Boolean, phone: String?)
    }

    companion object {
        private const val TYPE_HEADER = 1
        private const val TYPE_RESPONSE = 2
    }

    private var items: List<Any> = emptyList()
    private var adId: String = ""
    private var authorId: String? = null
    private var isOwner: Boolean = false
    private var isFieldPlayersAd: Boolean = false

    // счётчики для блокировки кнопки принятия
    private var defendersCount: Int = 0
    private var forwardsCount: Int = 0
    private var goaliesCount: Int = 0
    private var acceptedDefenders: Int = 0
    private var acceptedForwards: Int = 0
    private var acceptedGoalies: Int = 0
    private var adType: Int = 0
    private var acceptedTotal: Int = 0

    fun setData(responses: List<Response>?, ad: Ad) {
        Log.d("DEBUG_RESPONSES", "setData received ${responses?.size} responses")
        this.adId = ad.id.toString()
        this.authorId = ad.authorId
        this.isOwner = currentUserId != null && currentUserId == authorId
        this.isFieldPlayersAd = (ad.type == 1 && ad.subType == 2)

        this.defendersCount = ad.defendersCount ?: 0
        this.forwardsCount = ad.forwardsCount ?: 0
        this.goaliesCount = ad.goaliesCount ?: 0
        this.acceptedDefenders = ad.acceptedDefendersCount ?: 0
        this.acceptedForwards = ad.acceptedForwardsCount ?: 0
        this.acceptedGoalies = ad.acceptedGoaliesCount ?: 0
        this.adType = ad.type


        val allResponses = responses ?: emptyList()

        acceptedTotal = allResponses.count { it.status == "APPROVED" }
        // Фильтрация: не-автор видит только APPROVED
        val filteredResponses = if (isOwner) {
            allResponses
        } else {
            allResponses.filter { it.status == "APPROVED" }
        }

        // Сортировка: APPROVED → PENDING → остальные
        val sortedResponses = filteredResponses.sortedWith(
            compareBy<Response> { it.status != "APPROVED" }
                .thenBy { it.status != "PENDING" }
                .thenBy { it.createdAt }
        )

        val newItems = mutableListOf<Any>()
        if (isFieldPlayersAd) {
            val defenders = sortedResponses.filter { it.responseRole == "DEFENDER" }
            val forwards = sortedResponses.filter { it.responseRole == "FORWARD" }
            if (defenders.isNotEmpty()) {
                newItems.add("Защитники")
                newItems.addAll(defenders)
            }
            if (forwards.isNotEmpty()) {
                newItems.add("Нападающие")
                newItems.addAll(forwards)
            }
        } else {
            newItems.addAll(sortedResponses)
        }
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) TYPE_HEADER else TYPE_RESPONSE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_response_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_response, parent, false)
            ResponseViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                val groupName = items[position] as String
                holder.bind(groupName)
            }
            is ResponseViewHolder -> {
                val response = items[position] as Response
                holder.bind(response)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    private fun getResponseUserName(response: Response): String {
        val profile = response.user?.profile
        return when {
            profile?.firstName != null && profile.firstName!!.isNotEmpty() -> {
                val lastName = profile.lastName ?: ""
                profile.firstName + if (lastName.isNotEmpty()) " $lastName" else ""
            }
            profile?.lastName != null && profile.lastName!!.isNotEmpty() -> profile.lastName!!
            else -> "Пользователь"
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvResponseName)

        fun bind(groupName: String) {
            tvName.text = groupName
            tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            tvName.setTypeface(tvName.typeface, android.graphics.Typeface.BOLD)
        }
    }

    inner class ResponseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvResponseName)
        private val tvRole: TextView = itemView.findViewById(R.id.tvResponseRole)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvResponseStatus)
        private val btnConfirm: TextView = itemView.findViewById(R.id.btnConfirmResponse)
        private val btnCancelApproval: TextView = itemView.findViewById(R.id.btnCancelApproval)
        private val btnCancelResponse: TextView = itemView.findViewById(R.id.btnCancelResponse)

        fun bind(response: Response) {
            val userId = response.userId ?: response.user?.id
            val name = if (currentUserId != null && currentUserId == userId) "Вы"
            else getResponseUserName(response)

            tvName.text = name
            tvName.setOnClickListener {
                val phone = response.user?.phone
                listener.onProfileClick(userId, isOwner, phone)
            }

            val role = when (response.responseRole) {
                "DEFENDER" -> "Защитник"
                "FORWARD" -> "Нападающий"
                "GOALIE" -> "Вратарь"
                else -> null
            }
            if (role != null) {
                tvRole.text = "($role)"
                tvRole.visibility = View.VISIBLE
            } else {
                tvRole.visibility = View.GONE
            }

            // Сброс состояния кнопок
            btnConfirm.visibility = View.GONE
            btnCancelApproval.visibility = View.GONE
            btnCancelResponse.visibility = View.GONE
            tvStatus.visibility = View.GONE

            // Определяем, можно ли принять отклик (только для автора и не принятых)
            val canApprove = when {
                adType == 1 -> {
                    when (response.responseRole) {
                        "DEFENDER" -> acceptedDefenders < defendersCount
                        "FORWARD" -> acceptedForwards < forwardsCount
                        "GOALIE" -> acceptedGoalies < goaliesCount
                        else -> true // для специалистов, матчей и пр.
                    }
                }
                adType == 3 -> {
                    // Товарищеский матч – можно принять только один отклик
                    acceptedTotal < 1
                }
                else -> {
                    // Для остальных типов (например, специалисты) разрешаем не более одного,
                    // но если нужно, можно настроить отдельно
                    acceptedTotal < 1
                }
            }

// Определяем, является ли отклик от текущего пользователя
            val isMyResponse = currentUserId != null && currentUserId == userId

// --- Установка статуса ---
            when {
                isMyResponse && response.status == "PENDING" -> {
                    tvStatus.text = "Вы откликнулись"
                    tvStatus.visibility = View.VISIBLE
                }

                isMyResponse && response.status == "APPROVED" -> {
                    tvStatus.text = "✅ Отклик принят"
                    tvStatus.visibility = View.VISIBLE
                }

                isOwner && response.status == "APPROVED" -> {
                    tvStatus.text = "✅ Принят"
                    tvStatus.visibility = View.VISIBLE
                }

                isOwner && response.status == "PENDING" -> {
                    tvStatus.text = "⏳ Ожидает"
                    tvStatus.visibility = View.VISIBLE
                }

                else -> {
                    tvStatus.visibility = View.GONE
                }
            }

// --- Управление кнопками ---
            when {
                isOwner && response.status != "APPROVED" -> {
                    if (canApprove) {
                        btnConfirm.visibility = View.VISIBLE
                        btnConfirm.isEnabled = true
                        btnConfirm.setOnClickListener {
                            listener.onConfirmClick(response.id ?: "", adId, userId ?: "")
                        }
                    } else {
                        btnConfirm.visibility = View.VISIBLE
                        btnConfirm.isEnabled = false
                        btnConfirm.text = "Мест нет"
                    }
                }

                isOwner && response.status == "APPROVED" -> {
                    btnCancelApproval.visibility = View.VISIBLE
                    btnCancelApproval.setOnClickListener {
                        listener.onCancelApprovalClick(response.id ?: "", adId, userId ?: "")
                    }
                }

                !isOwner && isMyResponse && response.status == "PENDING" -> {
                    // Только для своих откликов в статусе PENDING показываем кнопку отмены
                    btnCancelResponse.visibility = View.VISIBLE
                    btnCancelResponse.setOnClickListener {
                        listener.onCancelResponseClick(response.id ?: "", adId, authorId ?: "")
                    }
                }
                // Для своих APPROVED кнопку отмены НЕ показываем (она скрыта по умолчанию)
            }
        }
    }
}