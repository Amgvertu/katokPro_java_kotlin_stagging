package com.katok.pro.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import com.katok.pro.R

class FilterDialogHelper(private val fragment: Fragment) {

    fun showTypeDialog(
        onTypeSelected: (category: Int, subtype: Int, role: String?) -> Unit
    ) {
        val dialogView = fragment.layoutInflater.inflate(R.layout.dialog_type_filter, null)
        val dialog = AlertDialog.Builder(fragment.requireContext()).setView(dialogView).create()
        val lvCategories = dialogView.findViewById<ListView>(R.id.lvCategories)
        val lvSubtypes = dialogView.findViewById<ListView>(R.id.lvSubtypes)
        val lvDetail = dialogView.findViewById<ListView>(R.id.lvDetail)

        val categories = arrayOf("Все", "Ищу игрока", "Ищу лёд", "Товарищеский матч", "Ищу специалиста")
        val categoryAdapter = ArrayAdapter(fragment.requireContext(), R.layout.item_simple_text, categories)
        lvCategories.adapter = categoryAdapter

        lvCategories.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                onTypeSelected(0, 0, null)
                dialog.dismiss()
                return@setOnItemClickListener
            }
            val category = position
            val subtypes = when (category) {
                1 -> arrayOf("Все", "Вратаря", "Полевого")
                2 -> arrayOf("Все", "Вратарь", "Полевой")
                3 -> arrayOf("Все", "Ищу", "Предлагаю")
                else -> arrayOf("Все", "Судья", "Фотограф", "Медик", "Тренер")
            }
            val subtypeAdapter = ArrayAdapter(fragment.requireContext(), R.layout.item_simple_text, subtypes)
            lvSubtypes.adapter = subtypeAdapter

            lvSubtypes.setOnItemClickListener { _, _, pos2, _ ->
                if (pos2 == 0) {
                    onTypeSelected(category, 0, null)
                    dialog.dismiss()
                    return@setOnItemClickListener
                }
                val subtype = pos2
                if (category == 1 && subtype == 2) {
                    val roles = arrayOf("Все", "Защитники", "Нападающие")
                    val roleAdapter = ArrayAdapter(fragment.requireContext(), R.layout.item_simple_text, roles)
                    lvDetail.adapter = roleAdapter
                    lvDetail.setOnItemClickListener { _, _, pos3, _ ->
                        val role = when (pos3) {
                            0 -> null
                            1 -> "DEFENDER"
                            else -> "FORWARD"
                        }
                        onTypeSelected(category, subtype, role)
                        dialog.dismiss()
                    }
                } else {
                    onTypeSelected(category, subtype, null)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    // Аналогично можно вынести showLevelDialog, showDateRangeDialog, showTimeDialog, showRinkDialog
    // Но пока оставим так, чтобы не перегружать.
}