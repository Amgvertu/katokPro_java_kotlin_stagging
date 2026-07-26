package com.katok.pro.network

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.katok.pro.model.RealtimeEvent

class RealtimeEventBus private constructor() {

    private val eventLiveData = MutableLiveData<RealtimeEvent>()

    fun getEvents(): LiveData<RealtimeEvent> = eventLiveData

    fun postEvent(event: RealtimeEvent) {
        eventLiveData.postValue(event)
    }

    companion object {
        @Volatile
        private var instance: RealtimeEventBus? = null

        @JvmStatic
        fun getInstance(): RealtimeEventBus {
            return instance ?: synchronized(this) {
                instance ?: RealtimeEventBus().also { instance = it }
            }
        }
    }
}
