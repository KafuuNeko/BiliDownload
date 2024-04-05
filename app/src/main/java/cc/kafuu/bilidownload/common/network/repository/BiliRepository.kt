package cc.kafuu.bilidownload.common.network.repository

import android.util.Log
import cc.kafuu.bilidownload.common.core.ServerCallback
import cc.kafuu.bilidownload.common.network.model.BiliRespond
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

open class BiliRepository(protected val biliApiService: BiliApiService) {
    companion object {
        private const val TAG = "BiliRepository"
    }

    protected fun <T, D> Call<BiliRespond<T>>.enqueue(
        callback: ServerCallback<D>,
        processingData: (T) -> D
    ) {
        enqueue(object : Callback<BiliRespond<T>> {
            override fun onResponse(
                call: Call<BiliRespond<T>>,
                response: Response<BiliRespond<T>>
            ) {
                processResponse(response, { data ->
                    callback.onSuccess(
                        response.code(),
                        response.body()?.code ?: 0,
                        response.message(),
                        data
                    )
                }, { code, errorCode, errorMessage ->
                    callback.onFailure(code, errorCode, errorMessage)
                }, processingData)
            }

            override fun onFailure(call: Call<BiliRespond<T>>, e: Throwable) {
                e.printStackTrace()
                callback.onFailure(0, 0, e.message ?: "Unknown error")
            }
        })
    }


    protected fun <T, D> Call<BiliRespond<T>>.execute(
        onFailure: ((Int, Int, String) -> Unit)?,
        processingData: (T) -> D
    ): D? {
        return try {
            val response = execute()
            var result: D? = null
            processResponse(response, { data ->
                result = data
            }, onFailure, processingData)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure?.invoke(0, 0, e.message ?: "Unknown error")
            null
        }
    }


    private fun <T, D> processResponse(
        response: Response<BiliRespond<T>>,
        onSuccess: (D) -> Unit,
        onFailure: ((Int, Int, String) -> Unit)?,
        processingData: (T) -> D
    ) {
        if (!response.isSuccessful) {
            Log.e(TAG, "Network call failed: ${response.code()} - ${response.message()}")
            onFailure?.invoke(response.code(), 0, response.message())
        } else if (response.body() == null) {
            Log.e(TAG, "Response body is null. Error code: ${response.code()},")
            onFailure?.invoke(response.code(), 0, "Response body is null")
        } else if (response.body()?.code != 0) {
            Log.e(
                TAG,
                "API call failed: ${response.body()?.code}, message: ${response.body()?.message}"
            )
            onFailure?.invoke(
                response.code(),
                response.body()?.code ?: 0,
                response.body()?.message ?: "Unknown API error"
            )
        } else {
            onSuccess(processingData(response.body()!!.data!!))
        }
    }

}