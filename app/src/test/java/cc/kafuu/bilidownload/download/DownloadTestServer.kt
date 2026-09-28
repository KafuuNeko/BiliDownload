package cc.kafuu.bilidownload.download

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 本地原始 HTTP 服务，可发送截断正文及保持连接，不依赖 CDN、账号或额外测试库。 */
internal class DownloadTestServer(
    private val respond: (Request) -> Reply
) : Closeable {
    data class Request(val path: String, val headers: Map<String, String>)
    data class Reply(val raw: String, val holdOpen: Boolean = false)

    private val server = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool()
    private val sockets = CopyOnWriteArrayList<Socket>()
    val requests = CopyOnWriteArrayList<Request>()
    val peerClosed = CountDownLatch(1)
    val url = "http://127.0.0.1:${server.localPort}/stream"

    init {
        workers.execute {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    sockets.add(socket)
                    workers.execute { serve(socket) }
                }
            } catch (_: IOException) {
                // 测试结束时关闭监听端口，使接受线程正常退出。
            }
        }
    }

    private fun serve(socket: Socket) {
        try {
            socket.use {
                // 记录请求头供续传断言使用，测试请求不包含真实账号信息。
                val input = socket.getInputStream().bufferedReader()
                val path = input.readLine().split(' ')[1]
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                }
                val request = Request(path, headers)
                requests.add(request)
                // 原始正文可以小于声明长度，保持连接时等待客户端主动取消。
                val reply = respond(request)
                socket.getOutputStream().apply {
                    write(reply.raw.toByteArray(Charsets.US_ASCII))
                    flush()
                }
                if (reply.holdOpen) {
                    input.read()
                    peerClosed.countDown()
                }
            }
        } catch (_: IOException) {
            peerClosed.countDown()
        } finally {
            sockets.remove(socket)
        }
    }

    override fun close() {
        server.close()
        sockets.forEach { it.close() }
        workers.shutdownNow()
        check(workers.awaitTermination(5, TimeUnit.SECONDS))
    }

    companion object {
        /** [length] 可与实际正文不同，用于模拟响应提前结束。 */
        fun response(
            body: String = "",
            code: Int = 200,
            length: Int? = body.length,
            headers: String = "",
            holdOpen: Boolean = false
        ) = Reply(
            "HTTP/1.1 $code Test\r\n" +
                (length?.let { "Content-Length: $it\r\n" } ?: "") +
                "Connection: close\r\n$headers\r\n$body",
            holdOpen
        )
    }
}
