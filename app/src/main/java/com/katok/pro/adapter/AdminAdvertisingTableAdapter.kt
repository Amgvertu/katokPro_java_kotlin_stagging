package com.katok.pro.adapter

import android.content.Context
import android.graphics.Paint
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.katok.pro.R
import com.katok.pro.databinding.ItemAdminAdvertisingTableBinding
import com.katok.pro.model.Advertising
import com.katok.pro.model.City
import java.text.SimpleDateFormat
import java.util.*

class AdminAdvertisingTableAdapter(
    private val context: Context,
    private val onItemClick: (Advertising) -> Unit,
    private val onLongClick: (Advertising) -> Unit
) : RecyclerView.Adapter<AdminAdvertisingTableAdapter.ViewHolder>() {

    private var ads: List<Advertising> = emptyList()
    private var columnWidths: IntArray? = null
    private val density = context.resources.displayMetrics.density
    private val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics)
    private var citiesMap: Map<Int, String> = emptyMap()

    fun submitList(list: List<Advertising>) {
        ads = list
        calculateColumnWidths()
        notifyDataSetChanged()
    }

    fun addItems(newItems: List<Advertising>) {
        if (newItems.isEmpty()) return
        ads = ads + newItems
        calculateColumnWidths()
        notifyDataSetChanged()
    }

    private fun calculateColumnWidths() {
        if (ads.isEmpty()) {
            columnWidths = null
            return
        }
        val paint = Paint().apply { textSize = 11f * density }
        val headerPaint = Paint().apply {
            textSize = 11f * density
            isFakeBoldText = true
        }
        val headers = listOf("Статус", "Рекламодатель", "Тип", "Интервал", "Города", "Период", "Начало", "Окончание")
        val headerWidths = headers.map { headerPaint.measureText(it).toInt() + 40 }

        val maxWidths = IntArray(8) { 0 }
        for (ad in ads) {
            val status = getStatusDisplay(ad.status)
            val advertiser = ad.advertiser ?: ""
            val type = if (ad.type == 1) "Лента" else "Диалог"
            val interval = ad.interval?.toString() ?: "-"
            val cities = if (ad.allCities) {
                "Все города"
            } else {
                ad.cityIds?.mapNotNull { citiesMap[it] }?.joinToString(", ") ?: "Не указаны"
            }
            val period = "${ad.periodDays} дн."
            val start = ad.startDate?.take(10) ?: "-"
            val end = ad.endDate?.take(10) ?: "-"
            val widths = listOf(
                paint.measureText(status).toInt() + 40,
                paint.measureText(advertiser).toInt() + 40,
                paint.measureText(type).toInt() + 30,
                paint.measureText(interval).toInt() + 30,
                paint.measureText(cities).toInt() + 50,
                paint.measureText(period).toInt() + 30,
                paint.measureText(start).toInt() + 30,
                paint.measureText(end).toInt() + 30
            )
            for (i in 0 until 8) {
                if (widths[i] > maxWidths[i]) maxWidths[i] = widths[i]
            }
        }
        columnWidths = IntArray(8) { i -> maxOf(headerWidths[i], maxWidths[i]) }
    }

    private fun getStatusDisplay(status: String?): String {
        return when (status) {
            "ACTIVE" -> "Активно"
            "PAUSED" -> "Приостановлено"
            "EXPIRED" -> "Истекла"
            "DELETED" -> "Удалена"
            else -> status ?: ""
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
        val binding = ItemAdminAdvertisingTableBinding.inflate(
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

    inner class ViewHolder(private val binding: ItemAdminAdvertisingTableBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ad: Advertising, widths: IntArray?) {
            // Статус
            binding.tvStatus.text = getStatusDisplay(ad.status)
            val statusColor = when (ad.status) {
                "ACTIVE" -> android.graphics.Color.parseColor("#4CAF50")
                "PAUSED" -> android.graphics.Color.parseColor("#FF9800")
                "EXPIRED" -> android.graphics.Color.parseColor("#9E9E9E")
                "DELETED" -> android.graphics.Color.parseColor("#F44336")
                else -> android.graphics.Color.parseColor("#E0E0E0")
            }
            binding.tvStatus.setBackgroundColor(statusColor)

            // Рекламодатель
            binding.tvAdvertiser.text = ad.advertiser ?: ""

            // Тип
            binding.tvType.text = if (ad.type == 1) "Лента" else "Диалог"

            // Интервал
            binding.tvInterval.text = ad.interval?.toString() ?: "-"

            // Города
            val cityNames = if (ad.allCities) {
                "Все города"
            } else {
                ad.cityIds?.mapNotNull { citiesMap[it] }?.joinToString(", ") ?: "Не указаны"
            }
            binding.tvCities.text = cityNames
            binding.tvCities.maxLines = 1
            binding.tvCities.ellipsize = TextUtils.TruncateAt.END

            // Период
            binding.tvPeriod.text = "${ad.periodDays} дн."

            // Начало и окончание
            binding.tvStart.text = ad.startDate?.take(10) ?: "-"
            binding.tvEnd.text = ad.endDate?.take(10) ?: "-"

            widths?.let {
                binding.tvStatus.layoutParams.width = it[0]
                binding.tvAdvertiser.layoutParams.width = it[1]
                binding.tvType.layoutParams.width = it[2]
                binding.tvInterval.layoutParams.width = it[3]
                binding.tvCities.layoutParams.width = it[4]
                binding.tvPeriod.layoutParams.width = it[5]
                binding.tvStart.layoutParams.width = it[6]
                binding.tvEnd.layoutParams.width = it[7]
            }
        }
    }

    fun updateCities(cities: List<City>) {
        citiesMap = cities.associate { it.id to (it.name ?: "") }
        calculateColumnWidths()
        notifyDataSetChanged()
    }
}