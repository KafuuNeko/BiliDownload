package cc.kafuu.bilidownload.common.network.model

import androidx.annotation.StringRes
import cc.kafuu.bilidownload.R
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * B站弹幕数据模型
 * 对应B站弹幕XML格式
 */
data class BiliXmlDanmaku(
    // 弹幕发送时间（相对于视频开始时间的秒数，保留3位小数）
    val progress: Float,
    // 弹幕类型 1-3 5-7 8
    val type: Int,
    // 字号大小
    val fontSize: Int,
    // 弹幕颜色
    val color: Int,
    // 发送时间戳
    val sendTimestamp: Long,
    // 弹幕池 0-普通池 1-字幕池 2-特殊池
    val pool: Int,
    // 发送者ID（哈希后的mid）
    val senderHash: String,
    // 弹幕ID（数据库id）
    val danmakuId: Long,
    // 弹幕内容
    val content: String
) {
    companion object {
        /**
         * 从XML解析弹幕列表
         * B站弹幕API返回的XML格式示例：
         * <?xml version="1.0" encoding="UTF-8"?>
         * <i>
         *   <d p="23.45600,1,25,16777215,1234567890,0,12345678,87654321,10">弹幕内容</d>
         * </i>
         *
         * p属性格式
         * 0: 出现时间(秒)
         * 1: 类型(1-3滚动 4底部 5顶部 6逆向 7精准 8高级)
         * 2: 字号
         * 3: 颜色(十进制)
         * 4: 发送时间戳
         * 5: 弹幕池(0普通 1字幕 2特殊)
         * 6: 发送者ID(hash)
         * 7: 弹幕ID
         * 8: 权重或其他参数(可选)
         */
        fun parseFromXml(xmlString: String): List<BiliXmlDanmaku> {
            val danmakuList = mutableListOf<BiliXmlDanmaku>()
            val parser = createXmlParser(xmlString)

            // 验证根节点
            validateRootNode(parser)

            // 解析所有弹幕
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                parseDanmakuNode(parser)?.let { danmakuList.add(it) }
            }

            return danmakuList
        }

        /**
         * 创建并配置XML解析器
         */
        private fun createXmlParser(xmlString: String): XmlPullParser {
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }
            return factory.newPullParser().apply {
                setInput(StringReader(sanitizeDanmakuXml(xmlString)))
            }
        }

        /**
         * 验证XML根节点是否为<i>
         */
        private fun validateRootNode(parser: XmlPullParser) {
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, "i")
        }

        /**
         * 解析单个弹幕节点
         * @return 成功解析返回BiliDanmaku对象，失败返回null
         */
        private fun parseDanmakuNode(parser: XmlPullParser): BiliXmlDanmaku? {
            // 跳过非<d>标签
            if (!isDanmakuStartTag(parser)) {
                return null
            }

            // 获取p属性
            val pAttribute = parser.getAttributeValue(null, "p") ?: run {
                parser.nextText()
                return null
            }

            // 获取弹幕内容
            val content = parser.nextText()

            // 解析并创建弹幕对象
            return parseDanmakuAttribute(pAttribute, content)
        }

        /**
         * 判断是否为弹幕开始标签
         */
        private fun isDanmakuStartTag(parser: XmlPullParser): Boolean {
            return parser.eventType == XmlPullParser.START_TAG && parser.name == "d"
        }

        /**
         * 解析弹幕属性并创建对象
         */
        private fun parseDanmakuAttribute(pAttribute: String, content: String): BiliXmlDanmaku? {
            return try {
                val params = pAttribute.split(",")
                if (params.size < 8) return null
                BiliXmlDanmaku(
                    progress = params[0].toFloatOrNull() ?: return null,
                    type = params[1].toIntOrNull() ?: return null,
                    fontSize = params[2].toIntOrNull() ?: return null,
                    color = params[3].toIntOrNull() ?: return null,
                    sendTimestamp = params[4].toLongOrNull() ?: return null,
                    pool = params[5].toIntOrNull() ?: return null,
                    senderHash = params[6],
                    danmakuId = params[7].toLongOrNull() ?: return null,
                    content = content
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        /**
         * 清理XML中的非法字符
         * 将未转义的&符号替换为&amp;
         */
        fun sanitizeDanmakuXml(xml: String): String {
            return xml.replace(
                Regex("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)"),
                "&amp;"
            )
        }
    }

    /**
     * 获取弹幕类型的字符串资源ID
     * @return 字符串资源ID，外部通过 CommonLibs.getString() 获取本地化字符串
     */
    @StringRes
    fun getTypeDescription(): Int {
        return when (type) {
            1 -> R.string.danmaku_type_scroll
            4 -> R.string.danmaku_type_bottom
            5 -> R.string.danmaku_type_top
            6 -> R.string.danmaku_type_reverse
            7 -> R.string.danmaku_type_precise
            8 -> R.string.danmaku_type_advanced
            else -> R.string.danmaku_type_unknown
        }
    }

    /**
     * 获取颜色的十六进制字符串
     */
    fun getColorHexString(): String {
        return String.format("#%06X", (0xFFFFFF and color))
    }

    /**
     * 获取格式化后的时间字符串（HH:MM:SS.mmm）
     */
    fun getFormattedTime(): String {
        val totalSeconds = progress.toLong()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val milliseconds = ((progress - totalSeconds) * 1000).toInt()

        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds)
    }
}
