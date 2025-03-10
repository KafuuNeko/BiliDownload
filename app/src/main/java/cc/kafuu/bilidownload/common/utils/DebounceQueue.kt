package cc.kafuu.bilidownload.common.utils

import kotlinx.coroutines.*

/**
 * DebounceQueue 用于对任务进行节流/防抖控制：
 * 调用 schedule() 方法添加任务，若在 delayMillis 时间内有多个请求，只会执行最新一次任务。
 * 若距离上次执行不足 delayMillis，则等待剩余时间后执行最新任务。
 *
 * @param T 任务数据的类型
 * @param scope 用于启动协程的 CoroutineScope，建议传入生命周期相关的 scope（如 lifecycleScope）。
 * @param delayMillis 最小任务间隔时间（毫秒）
 * @param onTaskExecute 实际需要执行的任务回调，参数为任务数据
 */
class DebounceQueue<T>(
    private val scope: CoroutineScope,
    private val delayMillis: Long,
    private val onTaskExecute: (T) -> Unit
) {
    private var lastExecutionTime = 0L
    private var pendingItem: T? = null
    private var job: Job? = null

    /**
     * 添加一个新的任务。如果当前已有等待任务，则只更新任务数据。
     */
    fun schedule(item: T) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLast = currentTime - lastExecutionTime

        // 保存最新任务数据
        pendingItem = item

        // 如果已有等待任务，则直接返回（只保留最后一次任务数据，不重复启动新协程）
        if (job?.isActive == true) return

        job = scope.launch {
            // 计算剩余等待时间
            val waitTime = if (timeSinceLast < delayMillis) delayMillis - timeSinceLast else 0L
            delay(waitTime)
            // 执行最新任务
            pendingItem?.let { latestItem ->
                onTaskExecute(latestItem)
                lastExecutionTime = System.currentTimeMillis()
                pendingItem = null
            }
        }
    }

    /**
     * 取消当前待执行的任务
     */
    fun cancel() {
        job?.cancel()
        job = null
        pendingItem = null
    }

    /**
     * 立即执行当前待执行任务，并取消延迟等待。
     */
    fun flush() {
        job?.cancel()
        pendingItem?.let { latestItem ->
            onTaskExecute(latestItem)
            lastExecutionTime = System.currentTimeMillis()
            pendingItem = null
        }
    }
}
