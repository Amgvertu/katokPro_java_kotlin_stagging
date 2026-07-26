package com.katok.pro.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.katok.pro.R
import com.katok.pro.databinding.ItemAdminMessageBinding
import com.katok.pro.model.admin.AdminMessage
import com.katok.pro.network.ApiClient
import java.text.SimpleDateFormat
import java.util.*

class MessagesAdapter(
    private val onItemClick: (AdminMessage) -> Unit,
    private val onDeleteClick: (AdminMessage) -> Unit,
    private val onLongClick: (AdminMessage) -> Unit
) : ListAdapter<AdminMessage, MessagesAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onItemClick, onDeleteClick)  // ← исправлено
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemAdminMessageBinding,
        private val onItemClick: (AdminMessage) -> Unit,
        private val onDeleteClick: (AdminMessage) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        private val displayFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        fun bind(message: AdminMessage) {
            // 1. Заголовок
            if (message.title.isNullOrEmpty()) {
                binding.tvTitle.visibility = View.GONE
            } else {
                binding.tvTitle.visibility = View.VISIBLE
                binding.tvTitle.text = message.title
            }

            // 2. Изображение
            val imageUrl = message.imageUrl
            if (!imageUrl.isNullOrEmpty()) {
                binding.ivImage.visibility = View.VISIBLE
                val normalizedUrl = ApiClient.normalizeResourceUrl(imageUrl)
                Glide.with(binding.root.context)
                    .load(normalizedUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_broken_image)
                    .fitCenter()
                    .into(binding.ivImage)
            } else {
                binding.ivImage.visibility = View.GONE
            }

            // 3. Текст (content)
            if (message.content.isNullOrEmpty()) {
                binding.tvContent.visibility = View.GONE
            } else {
                binding.tvContent.visibility = View.VISIBLE
                binding.tvContent.text = message.content
            }

            // 4. Ссылка
            val link = message.link
            if (!link.isNullOrEmpty()) {
                binding.tvLink.visibility = View.VISIBLE
                binding.tvLink.text = "🔗 " + link
                binding.tvLink.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    binding.root.context.startActivity(intent)
                }
            } else {
                binding.tvLink.visibility = View.GONE
                binding.tvLink.setOnClickListener(null)
            }

            // 5. Дата
            val formattedDate = try {
                val date = serverFormat.parse(message.createdAt)
                date?.let { displayFormat.format(it) } ?: message.createdAt
            } catch (e: Exception) {
                message.createdAt
            }
            binding.tvDate.text = formattedDate

            // 6. Индикатор непрочитанного
            binding.viewUnreadIndicator.visibility = if (!message.isRead) View.VISIBLE else View.GONE

            // 7. Клик по элементу – отмечаем прочитанным
            binding.root.setOnClickListener { onItemClick(message) }

            // 8. Кнопка удаления
            binding.ivDelete.setOnClickListener {
                onDeleteClick(message)
            }

            binding.root.setOnLongClickListener {
                onLongClick(message)
                true
            }

        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AdminMessage>() {
        override fun areItemsTheSame(oldItem: AdminMessage, newItem: AdminMessage) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AdminMessage, newItem: AdminMessage) =
            oldItem == newItem
    }
}