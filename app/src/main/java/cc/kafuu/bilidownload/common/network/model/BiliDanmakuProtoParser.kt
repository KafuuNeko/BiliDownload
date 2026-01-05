package cc.kafuu.bilidownload.common.network.model

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * B站弹幕Protobuf解析器
 * 解析B站弹幕API返回的protobuf格式数据
 */
object BiliDanmakuProtoParser {
    private const val TAG = "BiliDanmakuProtoParser"

    /**
     * 从protobuf二进制数据解析弹幕列表
     * B站弹幕protobuf结构简述：
     * DmSegMobileReply {
     *   repeated DanmakuElem elems = 1;
     * }
     *
     * DanmakuElem {
     *   int64 id = 1;
     *   int32 progress = 2;
     *   int32 mode = 3;
     *   int32 fontsize = 4;
     *   uint32 color = 5;
     *   string mid_hash = 6;
     *   string content = 7;
     *   int64 ctime = 8;
     *   int32 weight = 9;
     *   ...
     * }
     */
    fun parseFromProto(data: ByteArray): List<BiliXmlDanmaku> {
        val danmakuList = mutableListOf<BiliXmlDanmaku>()

        try {
            val input = ByteArrayInputStream(data)
            val dis = DataInputStream(input)

            // 简单的protobuf解析
            // protobuf格式: (tag << 3) | wire_type
            // wire_type: 0=varint, 1=64bit, 2=length-delimited, 3=start group, 4=end group, 5=32bit

            while (dis.available() > 0) {
                val tag = readVarint(dis)
                val fieldNumber = (tag shr 3).toInt()
                val wireType = (tag and 0x07).toInt()

                if (fieldNumber == 1 && wireType == 2) {
                    // DanmakuElem字段
                    val length = readVarint(dis).toInt()
                    val elemData = ByteArray(length)
                    dis.readFully(elemData)

                    parseDanmakuElem(elemData)?.let { danmakuList.add(it) }
                } else if (wireType == 2) {
                    // 跳过其他length-delimited字段
                    val length = readVarint(dis).toInt()
                    dis.skipBytes(length)
                } else if (wireType == 0) {
                    // 跳过varint字段
                    readVarint(dis)
                } else if (wireType == 5) {
                    // 跳过32bit字段
                    dis.readInt()
                } else {
                    // 未知类型，跳过
                    break
                }
            }

            dis.close()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse protobuf data", e)
        }

        return danmakuList
    }

    /**
     * 解析单个弹幕元素
     */
    private fun parseDanmakuElem(data: ByteArray): BiliXmlDanmaku? {
        val input = ByteArrayInputStream(data)
        val dis = DataInputStream(input)

        var id: Long? = null
        var progress: Float? = null
        var mode: Int? = null
        var fontsize: Int? = null
        var color: Int? = null
        var midHash: String? = null
        var content: String? = null
        var ctime: Long? = null
        var weight: Int? = null

        try {
            while (dis.available() > 0) {
                val tag = readVarint(dis)
                val fieldNumber = (tag shr 3).toInt()
                val wireType = (tag and 0x07).toInt()

                when (fieldNumber) {
                    1 -> if (wireType == 0) id = readVarint(dis)  // int64 id
                    2 -> if (wireType == 0) progress = readVarint(dis).toFloat()  // int32 progress
                    3 -> if (wireType == 0) mode = readVarint(dis).toInt()  // int32 mode
                    4 -> if (wireType == 0) fontsize = readVarint(dis).toInt()  // int32 fontsize
                    5 -> if (wireType == 0) color = readVarint(dis).toInt()  // uint32 color
                    6 -> if (wireType == 2) midHash = readString(dis)  // string mid_hash
                    7 -> if (wireType == 2) content = readString(dis)  // string content
                    8 -> if (wireType == 0) ctime = readVarint(dis)  // int64 ctime
                    9 -> if (wireType == 0) weight = readVarint(dis).toInt()  // int32 weight
                    else -> {
                        // 跳过未知字段
                        when (wireType) {
                            0 -> readVarint(dis)
                            2 -> {
                                val length = readVarint(dis).toInt()
                                dis.skipBytes(length)
                            }
                            5 -> dis.readInt()
                        }
                    }
                }
            }

            dis.close()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse danmaku element", e)
            return null
        }

        // 构造弹幕对象
        return if (id != null && progress != null && mode != null &&
                   fontsize != null && color != null && midHash != null &&
                   content != null && ctime != null) {
            BiliXmlDanmaku(
                progress = progress,
                type = mode,
                fontSize = fontsize,
                color = color,
                sendTimestamp = ctime,
                pool = 0,  // protobuf中可能没有这个字段，使用默认值
                senderHash = midHash,
                danmakuId = id,
                content = content
            )
        } else {
            Log.w(TAG, "Incomplete danmaku element: id=$id, progress=$progress, mode=$mode, content=$content")
            null
        }
    }

    /**
     * 读取varint编码的整数
     */
    private fun readVarint(dis: DataInputStream): Long {
        var result = 0L
        var shift = 0
        var b: Int

        do {
            b = dis.readByte().toInt() and 0xFF
            result = result or (((b and 0x7F) shl shift).toLong())
            shift += 7
        } while (b and 0x80 != 0)

        return result
    }

    /**
     * 读取length-delimited字符串
     */
    private fun readString(dis: DataInputStream): String {
        val length = readVarint(dis).toInt()
        val bytes = ByteArray(length)
        dis.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
