package cc.kafuu.bilidownload.common.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

fun <T> MutableLiveData<T>.liveData(): LiveData<T> = this