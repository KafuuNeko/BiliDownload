package cc.kafuu.bilidownload.common.model

import java.io.Serializable

sealed class ResultWrapper<out T, out E>: Serializable {
    data class Success<out T>(val value: T) : ResultWrapper<T, Nothing>()
    data class Error<out E>(val error: E) : ResultWrapper<Nothing, E>()
}