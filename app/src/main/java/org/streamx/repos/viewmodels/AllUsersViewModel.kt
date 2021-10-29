package org.streamx.repos.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AllUsersViewModel : ViewModel() {
    val liveRoomId by lazy { MutableLiveData("") }
    val liveListUsers by lazy { MutableLiveData(arrayListOf<String>()) }
    val liveVideoPlaying by lazy { MutableLiveData(false) }
    val liveVideoDuration by lazy { MutableLiveData(0L) }
    val liveVideoFile by lazy { MutableLiveData<String>() }

    public override fun onCleared() {
        super.onCleared()
        liveListUsers.value?.clear()
        liveVideoPlaying.value = false
        liveVideoDuration.value = 0
        liveVideoFile.value = ""
        liveRoomId.value = ""
    }
}