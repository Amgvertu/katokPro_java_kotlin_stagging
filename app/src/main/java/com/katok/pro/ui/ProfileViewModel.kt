package com.katok.pro.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileViewModel : ViewModel() {
    private val tabPosition = MutableLiveData(0)

    fun getTabPosition(): LiveData<Int> = tabPosition

    fun setTabPosition(position: Int) {
        tabPosition.value = position
    }
}
