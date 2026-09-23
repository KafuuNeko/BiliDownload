package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.model.bili.VideoStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * 共享普通稿件统计的有界缓存与在途请求，不持有页面或账号凭据。
 *
 * - 每个调用者通过挂起请求订阅结果，最后一个订阅者离开时取消底层任务。
 * - 仓库拥有共享任务作用域；任务只随有效订阅存活，账号切换时由 [invalidate] 清理。
 * - 短期缓存失败并对风控整体冷却，统计不可用不影响列表或下载。
 */
class BiliVideoStatsRepository(
    private val requestStats: suspend (String) -> Result,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val requestIntervalMillis: Long = 500L,
    private val cacheTtlMillis: Long = 5 * 60_000L,
    private val failureTtlMillis: Long = 30_000L,
    private val cooldownMillis: Long = 60_000L,
    private val maxCacheEntries: Int = 300,
) {
    /** 保留 HTTP 与业务错误码供冷却策略判断，不携带接口原文或鉴权信息。 */
    sealed interface Result {
        data class Success(val stats: VideoStats) : Result
        data class Failure(val httpCode: Int = 0, val apiCode: Int = 0) : Result {
            val isRateLimited: Boolean
                get() = httpCode == 429 || httpCode == 412 || apiCode in setOf(-352, -412, 429)
        }
    }

    private data class CacheEntry(val result: Result, val expiresAtMillis: Long)

    /** 同一 BV 的任务及订阅计数，所有登记与释放操作由 lock 保护。 */
    private class PendingRequest(val result: Deferred<Result>, var subscribers: Int = 0)

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(2)
    private val rateMutex = Mutex()
    private val cache = LinkedHashMap<String, CacheEntry>(16, 0.75f, true)
    private val pending = mutableMapOf<String, PendingRequest>()
    private var nextRequestAtMillis = 0L
    private var cooldownUntilMillis = 0L
    private var generation = 0L

    /** 读取仍有效的成功缓存；失败缓存由 [getStats] 处理，不冒充统计值。 */
    fun getCached(bvid: String): VideoStats? = synchronized(lock) {
        (cachedResult(bvid) as? Result.Success)?.stats
    }

    /**
     * 取得统计并合并相同 BV 的请求；调用方取消会释放订阅，取消不计入失败缓存。
     * 缓存、登记和启动分离，避免极快的响应先于任务登记完成。
     */
    suspend fun getStats(bvid: String): Result {
        if (bvid.isBlank()) return Result.Failure()
        val request = synchronized(lock) {
            cachedResult(bvid)?.let { return it }
            if (nowMillis() < cooldownUntilMillis) return Result.Failure(httpCode = 429)
            val currentGeneration = generation
            pending.getOrPut(bvid) {
                PendingRequest(scope.async(start = CoroutineStart.LAZY) {
                    fetchStats(bvid, currentGeneration)
                })
            }.also { it.subscribers++ }
        }

        // 订阅不成为共享任务的父 Job，单个页面退出不会取消其他页面仍需的请求。
        try {
            return request.result.await()
        } finally {
            synchronized(lock) {
                request.subscribers--
                if (request.subscribers == 0) {
                    if (pending[bvid] === request) pending.remove(bvid)
                    request.result.cancel()
                }
            }
        }
    }

    /** 账号身份变化时同步失效所有统计，取消旧请求并阻止迟到结果重新填入缓存。 */
    fun invalidate() {
        synchronized(lock) {
            generation++
            cache.clear()
            pending.values.forEach { it.result.cancel() }
            pending.clear()
            // 保留风控冷却与请求间隔，账号变化不能成为绕过限流的入口。
        }
    }

    /** 锁内读取并移除过期条目，使用单调时钟避免系统时间调整延长缓存。 */
    private fun cachedResult(bvid: String): Result? {
        val entry = cache[bvid] ?: return null
        if (nowMillis() >= entry.expiresAtMillis) {
            cache.remove(bvid)
            return null
        }
        return entry.result
    }

    /** 先控制并发与发送速率，再校验账号代次；网络完成后仅提交仍有效的结果。 */
    private suspend fun fetchStats(bvid: String, requestGeneration: Long): Result = permits.withPermit {
        rateMutex.withLock {
            delay((nextRequestAtMillis - nowMillis()).coerceAtLeast(0))
            nextRequestAtMillis = nowMillis() + requestIntervalMillis
        }
        synchronized(lock) {
            if (requestGeneration != generation) throw CancellationException("Statistics session changed")
            if (nowMillis() < cooldownUntilMillis) return@withPermit Result.Failure(httpCode = 429)
        }

        // 取消必须向下传播到 Retrofit，普通错误则交由失败缓存抑制重复补齐。
        val result = try {
            when (val response = requestStats(bvid)) {
                is Result.Success -> Result.Success(response.stats.normalized())
                is Result.Failure -> response
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.Failure()
        }
        val context = currentCoroutineContext()
        synchronized(lock) {
            context.ensureActive()
            if (requestGeneration == generation) cacheResult(bvid, result)
        }
        result
    }

    /** 锁内发布成功或短期失败缓存；限制总条数，并让风控失败暂停后续补齐。 */
    private fun cacheResult(bvid: String, result: Result) {
        val now = nowMillis()
        if (result is Result.Failure && result.isRateLimited) {
            cooldownUntilMillis = now + cooldownMillis
        }
        val ttl = if (result is Result.Success) cacheTtlMillis else failureTtlMillis
        cache[bvid] = CacheEntry(result, now + ttl)
        while (cache.size > maxCacheEntries) {
            val iterator = cache.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }
}
