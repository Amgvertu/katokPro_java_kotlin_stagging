package com.katok.pro.adapter

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.katok.pro.databinding.ItemAdminAdTableBinding
import com.katok.pro.model.Ad
import com.katok.pro.model.Rink
import com.katok.pro.util.PhoneUtils
import java.text.SimpleDateFormat
import java.util.Locale

class AdminAdTableAdapter(

    private val context: Context,
    private val onItemClick: (Ad) -> Unit,
    private val onLongClick: (Ad) -> Unit,
    private var rinkCache: List<Rink> = emptyList()
) : RecyclerView.Adapter<AdminAdTableAdapter.ViewHolder>() {

    private var ads: List<Ad> = emptyList()
    private var columnWidths: IntArray? = null
    private val density = context.resources.displayMetrics.density
    private var lastColumnWidths: IntArray? = null
    private val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics)

    fun submitList(list: List<Ad>) {
        ads = list
        calculateColumnWidths()
        lastColumnWidths = columnWidths   // <-- сохраняем
        notifyDataSetChanged()
    }

    fun addItems(newItems: List<Ad>) {
        if (newItems.isEmpty()) return
        ads = ads + newItems
        calculateColumnWidths()
        lastColumnWidths = columnWidths   // <-- сохраняем
        notifyDataSetChanged()
    }

    fun updateHeaderWidths(headerViews: List<TextView>) {
        val widths = lastColumnWidths ?: return
        if (headerViews.size != widths.size) return
        for (i in headerViews.indices) {
            headerViews[i].layoutParams?.width = widths[i]
            headerViews[i].requestLayout()
        }
    }

    private fun calculateColumnWidths() {
        if (ads.isEmpty()) {
            columnWidths = null
            return
        }

        val paint = Paint().apply { textSize = textSizePx }
        val headers = listOf("Статус", "Тип", "Город", "Стадион", "Дата/время", "Автор", "Телефон", "Отклики", "Принято")
        val headerWidths = headers.map { paint.measureText(it).toInt() + 20 }

        val maxWidths = IntArray(9) { 0 }
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        for (ad in ads) {
            // ... остальные поля ...

            // --- СТАДИОН (учитываем название и адрес) ---
            val rinkId = ad.rinkIds?.firstOrNull()
            val rink = rinkId?.let { rinkCache.find { it.id == rinkId } }
            val rinkName = rink?.name ?: "—"
            val rinkAddress = rink?.address ?: ""
            // Вычисляем ширину как максимум из названия и адреса + отступы (30px для padding и границ)
            val rinkWidth = maxOf(
                paint.measureText(rinkName).toInt(),
                paint.measureText(rinkAddress).toInt()
            ) + 30   // ← увеличили запас

            // Ширины остальных столбцов (как было)
            val status = when (ad.status) {
                "ACTIVE" -> "Активно"
                "FILLED" -> "Набрано"
                "ARCHIVED" -> "Архив"
                "MODERATION" -> "На модерации"
                else -> ad.status ?: ""
            }
            val type = ad.getTagText()
            val city = ad.city?.name ?: "—"
            val dateTime = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = ad.startTime?.let { sdf.parse(it) }
                date?.let { dateFormat.format(it) } ?: "—"
            } catch (e: Exception) {
                ad.startTime ?: "—"
            }
            val author = ad.author?.profile?.let {
                "${it.firstName ?: ""} ${it.lastName ?: ""}".trim()
            } ?: "—"
            val phone = ad.contactPhone?.let { PhoneUtils.formatPhoneNumberForDisplay(it) } ?: "—"
            val responses = (ad.responses?.size ?: 0).toString()
            val accepted = (ad.responses?.count { it.status == "APPROVED" } ?: 0).toString()

            val widths = listOf(
                paint.measureText(status).toInt() + 40,
                paint.measureText(type).toInt() + 40,
                paint.measureText(city).toInt() + 20,
                rinkWidth + 20,  // ← используем вычисленную ширину
                paint.measureText(dateTime).toInt() + 40,
                paint.measureText(author).toInt() + 20,
                paint.measureText(phone).toInt() + 20,
                paint.measureText(responses).toInt() + 30,
                paint.measureText(accepted).toInt() + 30
            )
            for (i in 0 until 9) {
                if (widths[i] > maxWidths[i]) maxWidths[i] = widths[i]
            }
        }

        // Берём максимум из заголовков и данных
        columnWidths = IntArray(9) { i ->
            maxOf(headerWidths[i], maxWidths[i])
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminAdTableBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ad = ads[position]
        holder.bind(ad, columnWidths)
        holder.itemView.setOnClickListener { onItemClick(ad) }
        holder.itemView.setOnLongClickListener {
            onLongClick(ad)
            true
        }
    }

    override fun getItemCount(): Int = ads.size

    inner class ViewHolder(private val binding: ItemAdminAdTableBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        fun bind(ad: Ad, widths: IntArray?) {
            // Статус
            binding.tvStatus.text = when (ad.status) {
                "ACTIVE" -> "Активно"
                "FILLED" -> "Набрано"
                "ARCHIVED" -> "Архив"
                "MODERATION" -> "На модерации"
                else -> ad.status ?: ""
            }
            binding.tvStatus.setBackgroundColor(
                when (ad.status) {
                    "ACTIVE" -> android.graphics.Color.parseColor("#6BCB77")
                    "FILLED" -> android.graphics.Color.parseColor("#FFD93D")
                    "ARCHIVED" -> android.graphics.Color.parseColor("#9E9E9E")
                    "MODERATION" -> android.graphics.Color.parseColor("#FFB74D")
                    else -> android.graphics.Color.parseColor("#E0E0E0")
                }
            )

            binding.tvType.text = ad.getTagText()
            binding.tvCity.text = ad.city?.name ?: "—"

            val rinkId = ad.rinkIds?.firstOrNull()
            val rink = rinkId?.let { rinkCache.find { it.id == rinkId } }
            binding.tvRink.text = rink?.name ?: "—"
            binding.tvRinkAddress.text = rink?.address ?: ""
            binding.tvRinkAddress.visibility = if (rink?.address != null) View.VISIBLE else View.GONE

            val dateTime = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = ad.startTime?.let { sdf.parse(it) }
                date?.let { dateFormat.format(it) } ?: "—"
            } catch (e: Exception) {
                ad.startTime ?: "—"
            }
            binding.tvDateTime.text = dateTime

            val authorName = ad.author?.profile?.let {
                "${it.firstName ?: ""} ${it.lastName ?: ""}".trim()
            } ?: "—"
            binding.tvAuthor.text = authorName
            binding.tvPhone.text = ad.contactPhone?.let { PhoneUtils.formatPhoneNumberForDisplay(it) } ?: "—"
            binding.tvResponses.text = (ad.responses?.size ?: 0).toString()
            binding.tvAccepted.text = (ad.responses?.count { it.status == "APPROVED" } ?: 0).toString()

            widths?.let {
                binding.tvStatus.layoutParams.width = it[0]
                binding.tvType.layoutParams.width = it[1]
                binding.tvCity.layoutParams.width = it[2]
                binding.llRink.layoutParams.width = it[3]      // ← применяем к контейнеру
                binding.tvDateTime.layoutParams.width = it[4]
                binding.tvAuthor.layoutParams.width = it[5]
                binding.tvPhone.layoutParams.width = it[6]
                binding.tvResponses.layoutParams.width = it[7]
                binding.tvAccepted.layoutParams.width = it[8]
            }
        }
    }
    fun updateRinks(newRinks: List<Rink>) {
        this.rinkCache = newRinks
        notifyDataSetChanged()
    }

    fun recalculateWidths() {
        calculateColumnWidths()
        lastColumnWidths = columnWidths
        notifyDataSetChanged()
    }
}