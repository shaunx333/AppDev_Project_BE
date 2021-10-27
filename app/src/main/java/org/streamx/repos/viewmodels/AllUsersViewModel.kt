package org.streamx.repos.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AllUsersViewModel : ViewModel() {
    val liveListUsers by lazy { MutableLiveData(arrayListOf<String>()) }
    val liveVideoPlaying by lazy { MutableLiveData(false) }
    val liveVideoDuration by lazy { MutableLiveData(0L) }
    val liveVideoFile by lazy { MutableLiveData<String>() }

    override fun onCleared() {
        super.onCleared()
        liveListUsers.value?.clear()
    }
}