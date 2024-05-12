package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.model.BiliRespond
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

/**
 * 与BiliApiService交互的仓库基类，用于发起网络请求并处理响应。
 *
 */
open class BiliRepository {
    /**
     * 异步执行网络请求，通过回调接口返回结果。
     *
     * @throws IllegalStateException 当onFailure为空且执行Response解析失败时将抛出异常
     *
     * @param T 响应数据的类型。
     * @param D 处理后的数据类型。
     * @param callback 服务器回调接口，用于返回成功或失败的结果。
     * @param processingData 数据处理函数，将T类型的数据转换为D类型。
     * @param checkResponseCode 是否检查响应代码为0
     */
    protected fun <T, D> Call<BiliRespond<T>>.enqueue(
        callback: IServerCallback<D>,
        checkResponseCode: Boolean,
        processingData: (T) -> D
    ) = enqueue(object : Callback<BiliRespond<T>> {
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
            }, processingData, checkResponseCode)
        }

        override fun onFailure(call: Call<BiliRespond<T>>, e: Throwable) {
            e.printStackTrace()
            callback.onFailure(0, 0, e.message ?: "Unknown error")
        }
    })

    protected fun <T, D> Call<BiliRespond<T>>.enqueue(
        callback: IServerCallback<D>,
        processingData: (T) -> D
    ) = enqueue(callback, true, processingData)


    /**
     * 同步执行网络请求，并返回处理后的结果。
     *
     * @note 此函数禁止在主线程中执行
     *
     * @throws IllegalStateException 当onFailure为空且执行Response解析失败时将抛出异常
     *
     * @param T 响应数据的类型。
     * @param D 处理后的数据类型。
     * @param onFailure 失败时的回调函数。如果为空，遇到错误时会抛出异常。
     * @param processingData 数据处理函数，将T类型的数据转换为D类型。
     * @param checkResponseCode 是否检查响应代码为0
     * @return D? 处理后的数据，请求失败时返回null（若存在失败回调函数情况下）。
     */
    @Throws(IOException::class, IllegalStateException::class)
    protected fun <T, D> Call<BiliRespond<T>>.execute(
        onFailure: ((Int, Int, String) -> Unit)?,
        checkResponseCode: Boolean,
        processingData: (T) -> D
    ): D? = try {
        val response = execute()
        var result: D? = null
        processResponse(response, { data ->
            result = data
        }, onFailure, processingData, checkResponseCode)
        result
    } catch (e: IOException) {
        e.printStackTrace()
        onFailure?.invoke(0, 0, e.message ?: "Unknown error") ?: throw e
        null
    }

    @Throws(IOException::class, IllegalStateException::class)
    protected fun <T, D> Call<BiliRespond<T>>.execute(
        onFailure: ((Int, Int, String) -> Unit)?,
        processingData: (T) -> D
    ): D? = execute(onFailure, true, processingData)

    /**
     * 处理响应数据，根据响应状态调用成功或失败的处理函数。
     *
     * @param T 响应数据的类型。
     * @param D 处理后的数据类型。
     * @param response 网络响应对象。
     * @param onSuccess 成功时的处理函数，接收处理后的数据D。
     * @param onFailure 失败时的回调函数，提供错误代码和消息。
     * @param processingData 数据处理函数，将T类型的数据转换为D类型。
     * @param checkResponseCode 是否检查响应代码为0
     */
    private fun <T, D> processResponse(
        response: Response<BiliRespond<T>>,
        onSuccess: (D) -> Unit,
        onFailure: ((Int, Int, String) -> Unit)?,
        processingData: (T) -> D,
        checkResponseCode: Boolean
    ) {
        if (!response.isSuccessful) {
            val message = "Network call failed: ${response.code()} - ${response.message()}"
            onFailure?.invoke(response.code(), 0, message) ?: throw IllegalStateException(message)
        } else if (response.body() == null) {
            val message = "Response body is null. Error code: ${response.code()}"
            onFailure?.invoke(response.code(), 0, message) ?: throw IllegalStateException(message)
        } else if (checkResponseCode && response.body()?.code != 0) {
            val message =
                "API call failed: ${response.body()?.code}, message: ${response.body()?.message}"
            onFailure?.invoke(
                response.code(),
                response.body()?.code ?: 0,
                message
            ) ?: throw IllegalStateException(message)
        } else try {
            val body = response.body()!!
            processingData(body.data ?: body.result!!)
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure?.invoke(
                response.code(),
                response.body()?.code ?: 0,
                e.message ?: "Unknown error"
            ) ?: throw e
            null
        }?.let { onSuccess(it) }
    }

}