package com.katok.pro.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ListView
import com.katok.pro.R

class MultiChoiceWithSearchDialog(
    private val context: Context,
    private val title: String,
    private val items: List<String>,
    private val selectedItems: Set<String>,
    private val onResult: (Set<String>) -> Unit
) {

    private var dialog: AlertDialog? = null
    private val checkedItems = BooleanArray(items.size) { items[it] in selectedItems }
    private val handler = Handler(Looper.getMainLooper())

    fun show() {
        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_multi_choice_search, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearch)
        val listView = dialogView.findViewById<ListView>(R.id.listView)

        // Адаптер для отображения списка
        val adapter = android.widget.ArrayAdapter(
            context,
            android.R.layout.simple_list_item_multiple_choice,
            items
        )
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        // Функция синхронизации состояний для видимых элементов
        fun syncVisibleStates() {
            for (i in 0 until listView.count) {
                val item = adapter.getItem(i) as String
                val originalIndex = items.indexOf(item)
                if (originalIndex >= 0) {
                    listView.setItemChecked(i, checkedItems[originalIndex])
                }
            }
        }

        // Первоначальная установка состояний
        syncVisibleStates()

        // Обработка поиска
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                adapter.filter.filter(query)
                // Небольшая задержка, чтобы фильтр успел обновить список
                handler.postDelayed({
                    syncVisibleStates()
                }, 50)
            }
        })

        // Обработка клика по элементу
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) as String
            val originalIndex = items.indexOf(item)
            if (originalIndex >= 0) {
                checkedItems[originalIndex] = !checkedItems[originalIndex]
                listView.setItemChecked(position, checkedItems[originalIndex])
            }
        }

        // Кнопка OK
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val result = items.filterIndexed { index, _ -> checkedItems[index] }.toSet()
                onResult(result)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}