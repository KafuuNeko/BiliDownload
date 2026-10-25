package cc.kafuu.bilidownload.formatting

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** Gradle 与命令行共用入口，检查模式不写文件。 */
fun main(arguments: Array<String>) {
    val exit =
        try {
            runFormatting(FormattingOptions.parse(arguments), ::println)
        } catch (exception: Exception) {
            System.err.println("Formatting failed: ${exception.message}")
            2
        }
    exitProcess(exit)
}

/**
 * 对所选源码执行批次格式化，全部文件验证成功后才允许写回。
 *
 * - 检查模式只报告差异，应用模式在首次写入前核对全部待修改文件的原始快照。
 * - 解码、原生格式化或并发编辑检查失败时抛出异常，保留尚未写入的源码。
 *
 * @return 检查通过或应用成功时返回 0，检查发现格式差异时返回 1。
 */
internal fun runFormatting(
    options: FormattingOptions,
    report: (String) -> Unit,
    formatter: SourceFormatter = AndroidStudioFormatter(options.studioHome, report),
): Int {
    val root = options.root.toRealPath()
    val paths = SourceFiles(root).select(options)
    if (paths.isEmpty()) {
        report("No Kotlin sources selected.")
        return 0
    }

    // UTF-8 解码不允许替换坏字节；任何文件失败时，其他文件也不应被提前写入。
    val sources = paths.map { path ->
        val bytes = Files.readAllBytes(path)
        validateUtf8(bytes)
        SourceSnapshot(path, bytes)
    }
    val formatted = formatter.format(root, sources)
    check(formatted.size == sources.size) { "Formatter did not return every selected source" }
    val changes = sources.zip(formatted).mapNotNull { (source, result) ->
        validateUtf8(result)
        if (source.before.contentEquals(result)) return@mapNotNull null
        SourceChange(
            path = source.path,
            before = source.before,
            after = result,
        )
    }
    // 检查模式到此结束，只有应用模式可以进入写入阶段。
    changes.forEach { change ->
        val relativePath = root.relativize(change.path).toString().replace('\\', '/')
        report(relativePath)
    }
    if (options.mode == "check") {
        report("Checked ${paths.size} Kotlin source(s); ${changes.size} need formatting.")
        return if (changes.isEmpty()) 0 else 1
    }

    // 全部并发编辑检查也在首次写入之前完成；原位写入保留文件权限和 ACL。
    changes.forEach { change ->
        check(Files.readAllBytes(change.path).contentEquals(change.before)) {
            "Source changed while formatting: ${root.relativize(change.path)}"
        }
        check(Files.isWritable(change.path)) { "Source is not writable: ${root.relativize(change.path)}" }
    }
    changes.forEach { Files.write(it.path, it.after) }
    report("Formatted ${changes.size} of ${paths.size} Kotlin source(s).")
    return 0
}

private fun validateUtf8(bytes: ByteArray) {
    UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
}

/** 记录选定源码的路径与格式化前字节，用于计算差异和检查并发编辑。 */
internal data class SourceSnapshot(val path: Path, val before: ByteArray)

/** 格式化引擎接收源码快照，保持输入顺序返回结果，原始文件由批次入口统一写回。 */
internal fun interface SourceFormatter {
    /** 在临时副本上格式化全部输入，失败时抛出异常并保留原始文件。 */
    fun format(root: Path, sources: List<SourceSnapshot>): List<ByteArray>
}

private data class SourceChange(
    val path: Path,
    val before: ByteArray,
    val after: ByteArray,
)
