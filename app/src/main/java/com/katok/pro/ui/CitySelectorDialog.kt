package com.katok.pro.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.katok.pro.R
import com.katok.pro.databinding.DialogCitySelectorBinding
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.LocationRepository
import kotlinx.coroutines.launch
import java.util.ArrayList
import java.util.Locale

class CitySelectorDialog(
    context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val listener: OnCitySelectedListener,
    private val locationRepository: LocationRepository
) : Dialog(context, R.style.Theme_AppCompat_Dialog) {

    interface OnCitySelectedListener {
        fun onCitySelected(city: City)
    }

    private var binding: DialogCitySelectorBinding? = null
    private var adapter: ArrayAdapter<String>? = null
    private val allCities = ArrayList<City>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogCitySelectorBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        binding?.btnShowAll?.visibility = View.GONE

        setupListView()
        setupListeners()

        loadCities()
    }

    private fun setupListView() {
        adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, ArrayList())
        binding?.lvCities?.adapter = adapter
    }

    private fun setupListeners() {
        binding?.etCitySearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterCities(s.toString())
            }
        })

        binding?.btnCancel?.setOnClickListener { dismiss() }
    }

    private fun loadCities() {
        lifecycleScope.launch {
            val result = locationRepository.getAllCitiesByCountry(1)
            when (result) {
                is NetworkResult.Success -> {
                    val cities = result.data
                    if (cities.isNotEmpty()) {
                        allCities.clear()
                        allCities.addAll(cities)
                        filterCities("")
                    }
                }
                is NetworkResult.Error -> {
                    if (allCities.isEmpty()) {
                        Toast.makeText(context, "Ошибка загрузки городов", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {}
            }
        }
    }

    private fun filterCities(query: String) {
        val cityNames = ArrayList<String>()
        if (query.isEmpty()) {
            for (city in allCities) {
                city.name?.let { cityNames.add(it) }
            }
        } else {
            val lowerQuery = query.lowercase(Locale.getDefault())
            for (city in allCities) {
                city.name?.lowercase(Locale.getDefault())?.let { nameLower ->
                    if (nameLower.contains(lowerQuery)) {
                        cityNames.add(city.name!!)
                    }
                }
            }
        }
        adapter?.clear()
        adapter?.addAll(cityNames)
        adapter?.notifyDataSetChanged()

        binding?.lvCities?.setOnItemClickListener { _, _, position, _ ->
            val selectedCityName = cityNames[position]
            for (city in allCities) {
                if (city.name == selectedCityName) {
                    listener.onCitySelected(city)
                    dismiss()
                    break
                }
            }
        }
    }

    override fun dismiss() {
        binding = null
        super.dismiss()
    }
}