package cc.kafuu.bilidownload.common.ext

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

fun <T> MutableLiveData<T>.liveData(): LiveData<T> = this