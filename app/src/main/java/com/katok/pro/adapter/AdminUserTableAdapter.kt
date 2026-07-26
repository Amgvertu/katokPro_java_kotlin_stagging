package com.katok.pro.adapter

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.katok.pro.databinding.ItemAdminUserTableBinding
import com.katok.pro.model.User
import com.katok.pro.util.PhoneUtils

class AdminUserTableAdapter(
    private val context: Context,
    private val onItemClick: (User) -> Unit,
    private val onLongClick: (User) -> Unit
) : RecyclerView.Adapter<AdminUserTableAdapter.ViewHolder>() {

    private var users: List<User> = emptyList()
    private var columnWidths: IntArray? = null
    private val density = context.resources.displayMetrics.density
    private val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics)

    fun submitList(list: List<User>) {
        users = list
        calculateColumnWidths()
        notifyDataSetChanged()
    }

    fun addItems(newItems: List<User>) {
        if (newItems.isEmpty()) return
        users = users + newItems
        calculateColumnWidths()
        notifyDataSetChanged()
    }

    private fun calculateColumnWidths() {
        if (users.isEmpty()) {
            columnWidths = null
            return
        }

        val paint = Paint().apply { textSize = textSizePx }
        // Заголовки (шапка) – они должны учитываться
        val headers = listOf("Телефон", "Имя Фамилия", "Роль", "Статус", "Email", "Команда")
        val headerWidths = headers.map { paint.measureText(it).toInt() + 20 } // + отступы

        // Максимальные ширины по данным
        val maxWidths = IntArray(6) { 0 }
        for (user in users) {
            val phone = PhoneUtils.formatPhoneNumberForDisplay(user.phone) ?: ""
            val name = "${user.profile?.firstName ?: ""} ${user.profile?.lastName ?: ""}".trim()
            val role = user.role ?: ""
            val status = if (user.status == "ACTIVE") "Активен" else "Заблокирован"
            val email = user.profile?.email ?: ""
            val team = user.profile?.team ?: ""

            val widths = listOf(
                paint.measureText(phone).toInt() + 20,
                paint.measureText(name).toInt() + 20,
                paint.measureText(role).toInt() + 30,
                paint.measureText(status).toInt() + 30,
                paint.measureText(email).toInt() + 20,
                paint.measureText(team).toInt() + 20
            )
            for (i in 0 until 6) {
                if (widths[i] > maxWidths[i]) maxWidths[i] = widths[i]
            }
        }

        // Берём максимум из заголовков и данных
        columnWidths = IntArray(6) { i ->
            maxOf(headerWidths[i], maxWidths[i])
        }
    }

    fun updateHeaderWidths(headerViews: List<TextView>) {
        val widths = columnWidths ?: return
        if (headerViews.size != widths.size) return
        for (i in headerViews.indices) {
            headerViews[i].layoutParams?.width = widths[i]
            headerViews[i].requestLayout()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminUserTableBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user, columnWidths)
        holder.itemView.setOnClickListener { onItemClick(user) }
        holder.itemView.setOnLongClickListener {
            onLongClick(user)
            true
        }
    }

    override fun getItemCount(): Int = users.size

    inner class ViewHolder(private val binding: ItemAdminUserTableBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User, widths: IntArray?) {
            binding.tvPhone.text = PhoneUtils.formatPhoneNumberForDisplay(user.phone)
            val fullName = "${user.profile?.firstName ?: ""} ${user.profile?.lastName ?: ""}".trim()
            binding.tvName.text = if (fullName.isEmpty()) "—" else fullName
            binding.tvRole.text = user.role ?: "USER"
            binding.tvRole.setBackgroundColor(
                when (user.role) {
                    "ADMIN" -> android.graphics.Color.parseColor("#FF6B6B")
                    "MODERATOR" -> android.graphics.Color.parseColor("#FFD93D")
                    else -> android.graphics.Color.parseColor("#6BCB77")
                }
            )
            binding.tvStatus.text = if (user.status == "ACTIVE") "Активен" else "Заблокирован"
            binding.tvStatus.setBackgroundColor(
                if (user.status == "ACTIVE") android.graphics.Color.parseColor("#6BCB77")
                else android.graphics.Color.parseColor("#FF6B6B")
            )
            binding.tvEmail.text = user.profile?.email ?: " — "
            binding.tvTeam.text = user.profile?.team ?: " — "

            // Применяем вычисленные ширины, если они есть
            widths?.let {
                binding.tvPhone.layoutParams.width = it[0]
                binding.tvName.layoutParams.width = it[1]
                binding.tvRole.layoutParams.width = it[2]
                binding.tvStatus.layoutParams.width = it[3]
                binding.tvEmail.layoutParams.width = it[4]
                binding.tvTeam.layoutParams.width = it[5]
            }
        }
    }

}