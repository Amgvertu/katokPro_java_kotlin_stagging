package com.katok.pro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.katok.pro.R
import com.katok.pro.adapter.MessagesAdapter
import com.katok.pro.databinding.FragmentMessagesBinding
import com.katok.pro.model.admin.AdminMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MessagesFragment : BaseFragment(R.layout.fragment_messages ) {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MessagesViewModel
    private lateinit var adapter: MessagesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = androidx.lifecycle.ViewModelProvider(requireActivity())[MessagesViewModel::class.java]

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.toolbar.title = "Уведомления"
        binding.toolbar.setTitleTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))

        adapter = MessagesAdapter(
            onItemClick = { message ->
                if (!message.isRead) {
                    viewModel.markAsRead(message.id)
                }
            },
            onDeleteClick = { message ->
                showDeleteConfirmationDialog(message)
            },
            onLongClick = { message -> showMessageContextMenu(message) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // Кнопка "Удалить все"
        binding.btnDeleteAll.setOnClickListener {
            showDeleteAllConfirmationDialog()
        }

        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.submitList(messages)
                binding.tvEmpty.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
                binding.btnDeleteAll.visibility = if (messages.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showDeleteConfirmationDialog(message: com.katok.pro.model.admin.AdminMessage) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить сообщение")
            .setMessage("Вы уверены, что хотите удалить это сообщение?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteMessage(message.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить все сообщения")
            .setMessage("Вы уверены, что хотите удалить все сообщения? Это действие нельзя отменить.")
            .setPositiveButton("Удалить все") { _, _ ->
                viewModel.deleteAllMessages()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showMessageContextMenu(message: AdminMessage) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // Отметить как прочитанное (если ещё не прочитано)
        if (!message.isRead) {
            options.add("Отметить как прочитанное")
            actions.add {
                AlertDialog.Builder(requireContext())
                    .setTitle("Отметить как прочитанное")
                    .setMessage("Вы уверены, что хотите отметить сообщение как прочитанное?")
                    .setPositiveButton("Да") { _, _ ->
                        viewModel.markAsRead(message.id)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }

        // Удалить (используем существующий диалог)
        options.add("Удалить")
        actions.add {
            showDeleteConfirmationDialog(message)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Действия")
            .setItems(options.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }
}