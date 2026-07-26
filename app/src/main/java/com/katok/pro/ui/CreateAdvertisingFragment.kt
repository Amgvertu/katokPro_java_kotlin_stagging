package com.katok.pro.ui

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.Glide
import com.katok.pro.R
import com.katok.pro.databinding.FragmentCreateAdvertisingBinding
import com.katok.pro.model.Advertising
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.network.ApiClient
import com.katok.pro.repository.AdvertisingRepository
import com.katok.pro.repository.LocationRepository
import com.katok.pro.util.ToastHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class CreateAdvertisingFragment : BaseFragment(R.layout.fragment_create_advertising) {

    private var _binding: FragmentCreateAdvertisingBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var advertisingRepository: AdvertisingRepository
    @Inject lateinit var locationRepository: LocationRepository

    private var adId: String? = null
    private var isEdit = false
    private var selectedImageFile: File? = null
    private var uploadedImageUrl: String? = null
    private var existingAd: Advertising? = null

    private val allCities = mutableListOf<City>()
    private var selectedCityIds = mutableSetOf<Int>()
    private var allCitiesSelected = false

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                selectedImageFile = saveImageToTempFile(it)
                Glide.with(this).load(selectedImageFile).into(binding.ivPreview)
                binding.layoutImagePreview.visibility = View.VISIBLE
                uploadedImageUrl = null
                validateForm()
            } catch (e: Exception) {
                ToastHelper.showError(requireContext(), "Ошибка при выборе изображения")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            adId = it.getString("adId")
            isEdit = adId != null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateAdvertisingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        setupListeners()
        loadCities()

        if (adId != null) {
            loadAdvertisingForEdit()
        }

        validateForm()
    }

    private fun setupSpinners() {
        // Тип рекламы
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("В ленте", "В диалоге"))
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerType.adapter = typeAdapter

        // Интервал (5-10)
        val intervalValues = (5..10).map { it.toString() }
        val intervalAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, intervalValues)
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerInterval.adapter = intervalAdapter
        binding.spinnerInterval.visibility = View.GONE
        binding.tvIntervalLabel.visibility = View.GONE
    }

    private fun setupListeners() {
        // Показывать/скрывать интервал в зависимости от типа
        binding.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isType1 = position == 0
                binding.spinnerInterval.visibility = if (isType1) View.VISIBLE else View.GONE
                binding.tvIntervalLabel.visibility = if (isType1) View.VISIBLE else View.GONE
                validateForm()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Выбор городов
        binding.btnSelectCities.setOnClickListener {
            showCityMultiChoiceDialog()
        }

        // Загрузка изображения
        binding.btnPickImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnRemoveImage.setOnClickListener {
            selectedImageFile = null
            uploadedImageUrl = null
            binding.layoutImagePreview.visibility = View.GONE
            validateForm()
        }

        // Кнопка сохранения
        binding.btnSave.setOnClickListener { saveAdvertising() }
        binding.btnCancel.setOnClickListener { NavHostFragment.findNavController(this).popBackStack() }
    }

    private fun loadCities() {
        lifecycleScope.launch {
            val result = locationRepository.getAllCitiesByCountry(1)
            if (result is NetworkResult.Success) {
                allCities.clear()
                allCities.addAll(result.data)
            }
        }
    }

    private fun loadAdvertisingForEdit() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val result = advertisingRepository.getAdvertisingById(adId!!)
            binding.progressBar.visibility = View.GONE
            when (result) {
                is NetworkResult.Success -> {
                    val ad = result.data
                    existingAd = ad
                    fillForm(ad)
                    isEdit = true
                }
                is NetworkResult.Error -> {
                    handleError(result)
                    NavHostFragment.findNavController(this@CreateAdvertisingFragment).popBackStack()
                }
                else -> {}
            }
        }
    }

    private fun fillForm(ad: Advertising) {
        binding.etAdvertiser.setText(ad.advertiser)
        binding.etLink.setText(ad.link)
        binding.etRefData.setText(ad.refData)
        binding.spinnerType.setSelection(if (ad.type == 1) 0 else 1)
        if (ad.type == 1) {
            binding.spinnerInterval.visibility = View.VISIBLE
            binding.tvIntervalLabel.visibility = View.VISIBLE
            ad.interval?.let { interval ->
                val position = interval - 5
                if (position in 0..5) binding.spinnerInterval.setSelection(position)
            }
        }
        binding.etPeriodDays.setText(ad.periodDays.toString())
        if (ad.allCities) {
            allCitiesSelected = true
            selectedCityIds.clear()
        } else {
            allCitiesSelected = false
            selectedCityIds = ad.cityIds?.toMutableSet() ?: mutableSetOf()
        }
        updateSelectedCitiesDisplay()
        if (!ad.imageUrl.isNullOrEmpty()) {
            uploadedImageUrl = ad.imageUrl
            Glide.with(this)
                .load(ApiClient.normalizeResourceUrl(ad.imageUrl))
                .into(binding.ivPreview)
            binding.layoutImagePreview.visibility = View.VISIBLE
        }
        validateForm()
        binding.btnSave.isEnabled = true
    }

    private fun showCityMultiChoiceDialog() {
        if (allCities.isEmpty()) {
            ToastHelper.showInfo(requireContext(), "Загрузка городов...")
            return
        }
        val cityNames = allCities.map { it.name ?: "" }
        val checkedItems = BooleanArray(cityNames.size) { false }
        // Если allCitiesSelected, то все чекбоксы true
        if (allCitiesSelected) {
            checkedItems.indices.forEach { checkedItems[it] = true }
        } else {
            // Отмечаем выбранные
            allCities.forEachIndexed { index, city ->
                if (selectedCityIds.contains(city.id)) checkedItems[index] = true
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Выберите города")
            .setMultiChoiceItems(cityNames.toTypedArray(), checkedItems) { _, which, isChecked ->
                if (allCitiesSelected) {
                    // Если выбраны все, то при снятии галочки убираем allCitiesSelected
                    allCitiesSelected = false
                    // И сбрасываем все галочки
                    // Для этого обновляем диалог, но проще пересоздать
                }
                // Обновляем выбранные ID
                if (isChecked) {
                    if (!allCitiesSelected) {
                        selectedCityIds.add(allCities[which].id)
                    }
                } else {
                    selectedCityIds.remove(allCities[which].id)
                }
            }
            .setPositiveButton("OK") { _, _ ->
                // Если выбраны все города, то allCitiesSelected = true
                if (selectedCityIds.size == allCities.size) {
                    allCitiesSelected = true
                    selectedCityIds.clear()
                }
                updateSelectedCitiesDisplay()
                validateForm()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateSelectedCitiesDisplay() {
        val text = if (allCitiesSelected) "Все города" else "Выбрано: ${selectedCityIds.size}"
        binding.tvSelectedCities.text = text
    }

    private fun validateForm() {
        val advertiser = binding.etAdvertiser.text.toString().trim()
        val hasImage = selectedImageFile != null || uploadedImageUrl != null
        val type = binding.spinnerType.selectedItemPosition
        val intervalOk = if (type == 0) {
            binding.spinnerInterval.selectedItemPosition >= 0
        } else true
        val periodOk = binding.etPeriodDays.text.toString().toIntOrNull()?.let { it > 0 } ?: false
        val cityOk = allCitiesSelected || selectedCityIds.isNotEmpty()

        binding.btnSave.isEnabled = advertiser.isNotEmpty() && hasImage && intervalOk && periodOk && cityOk
    }

    private fun saveAdvertising() {
        val advertiser = binding.etAdvertiser.text.toString().trim()
        val link = binding.etLink.text.toString().trim().takeIf { it.isNotEmpty() }
        val refData = binding.etRefData.text.toString().trim().takeIf { it.isNotEmpty() }
        val type = binding.spinnerType.selectedItemPosition + 1 // 1 или 2
        val interval = if (type == 1) {
            binding.spinnerInterval.selectedItemPosition + 5 // 5..10
        } else null
        val periodDays = binding.etPeriodDays.text.toString().toIntOrNull() ?: 0

        // Города
        val cityIds = if (allCitiesSelected) emptyList() else selectedCityIds.toList()

        val advertising = Advertising(
            advertiser = advertiser,
            link = link,
            refData = refData,
            type = type,
            interval = interval,
            periodDays = periodDays,
            cityIds = cityIds,
            allCities = allCitiesSelected
        )

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnSave.isEnabled = false

            // Если изображение не загружено, но есть URL (при редактировании), используем его
            var imageUrl = uploadedImageUrl
            if (selectedImageFile != null && imageUrl == null) {
                // Загружаем изображение
                val mimeType = getMimeType(selectedImageFile!!)
                val part = MultipartBody.Part.createFormData(
                    "file",
                    selectedImageFile!!.name,
                    selectedImageFile!!.asRequestBody(mimeType.toMediaTypeOrNull())
                )
                val uploadResult = advertisingRepository.uploadAdvertisingImage(part)
                if (uploadResult is NetworkResult.Success) {
                    imageUrl = uploadResult.data
                } else {
                    ToastHelper.showError(requireContext(), "Ошибка загрузки изображения")
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    return@launch
                }
            }

            advertising.imageUrl = imageUrl

            val result = if (isEdit) {
                advertisingRepository.updateAdvertising(adId!!, advertising)
            } else {
                advertisingRepository.createAdvertising(advertising)
            }

            binding.progressBar.visibility = View.GONE
            binding.btnSave.isEnabled = true

            when (result) {
                is NetworkResult.Success -> {
                    ToastHelper.showSuccess(requireContext(), if (isEdit) "Реклама обновлена" else "Реклама создана")
                    NavHostFragment.findNavController(this@CreateAdvertisingFragment).popBackStack()
                }
                is NetworkResult.Error -> {
                    handleError(result)
                }
                else -> {}
            }
        }
    }

    private fun saveImageToTempFile(uri: Uri): File {
        val timeStamp = System.currentTimeMillis().toString()
        val tempFile = File(requireContext().cacheDir, "ad_$timeStamp.jpg")
        requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return tempFile
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}