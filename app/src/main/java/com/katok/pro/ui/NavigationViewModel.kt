package com.katok.pro.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class NavigationViewModel : ViewModel() {
    private val currentFragmentTag = MutableLiveData<String>()

    fun getCurrentFragmentTag(): LiveData<String> = currentFragmentTag

    fun setCurrentFragmentTag(tag: String) {
        currentFragmentTag.value = tag
    }
}
