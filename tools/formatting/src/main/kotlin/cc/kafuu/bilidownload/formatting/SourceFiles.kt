package cc.kafuu.bilidownload.formatting

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/** 限定格式化范围，不跟随仓库外的路径，也不扫描构建产物。 */
internal class SourceFiles(
    root: Path,
) {
    private val mRoot = root.toRealPath()
    private val mExcluded = setOf(".git", ".gradle", ".kotlin", "build", "captures")

    /** 默认只选择工作区改动；全仓扫描必须由调用者显式指定。 */
    fun select(options: FormattingOptions): List<Path> {
        val explicit = options.files != null || options.filesFrom != null
        // 先确定唯一选择器，再统一校验路径，避免不同入口绕过范围保护。
        val names =
            when {
                options.files != null -> options.files.split(',').map { it.trim() }
                options.filesFrom != null ->
                    Files
                        .readAllLines(mRoot.resolve(options.filesFrom), UTF_8)
                        .map { it.removePrefix("\uFEFF").trim() }
                        .filter { it.isNotEmpty() }

                options.all -> git("ls-files", "--cached", "--others", "--exclude-standard", "-z")
                options.base != null -> {
                    // 把用户传入的引用先解析成提交，后续 diff 只使用已验证的哈希。
                    val commit = gitText(
                        "rev-parse",
                        "--verify",
                        "--end-of-options",
                        "${options.base}^{commit}"
                    ).trim()
                    git(
                        "diff",
                        "--name-only",
                        "--diff-filter=ACMR",
                        "-z",
                        "$commit...HEAD",
                        "--"
                    ) + dirty()
                }

                else -> dirty()
            }
        return names
            .distinct()
            .mapNotNull { name -> resolve(name = name, explicit = explicit) }
            .sortedBy { it.toString() }
    }

    private fun dirty(): List<String> {
        val tracked =
            if (runGit("rev-parse", "--verify", "HEAD").first == 0) {
                git("diff", "--name-only", "--diff-filter=ACMR", "-z", "HEAD", "--")
            } else {
                git("ls-files", "--cached", "-z")
            }
        return tracked + git("ls-files", "--others", "--exclude-standard", "-z")
    }

    private fun resolve(
        name: String,
        explicit: Boolean,
    ): Path? {
        val path = mRoot.resolve(name).normalize()
        val relative = if (path.startsWith(mRoot)) mRoot.relativize(path) else null
        val supported = name.endsWith(".kt") || name.endsWith(".kts")
        val excluded = relative == null || relative.any { it.toString() in mExcluded }
        if (!supported || excluded || !Files.isRegularFile(path)) {
            require(!explicit) { "Not an existing Kotlin source inside the repository: $name" }
            return null
        }
        require(path.toRealPath().startsWith(mRoot)) { "Source path escapes the repository: $name" }
        return path
    }

    private fun git(vararg arguments: String): List<String> =
        gitText(*arguments).split('\u0000').filter { it.isNotEmpty() }

    private fun gitText(vararg arguments: String): String {
        val (exit, output) = runGit(*arguments)
        check(exit == 0) { "Git failed (${arguments.first()}): ${output.trim()}" }
        return output
    }

    private fun runGit(vararg arguments: String): Pair<Int, String> {
        val process =
            ProcessBuilder(listOf("git", "-C", mRoot.toString()) + arguments)
                .start()
        // Git 的 CRLF 警告属于 stderr，不能混入以 NUL 分隔的路径数据。
        val errors =
            CompletableFuture.supplyAsync {
                process.errorStream.bufferedReader(UTF_8).use { it.readText() }
            }
        val output = process.inputStream.bufferedReader(UTF_8).use { it.readText() }
        val exit = process.waitFor()
        val diagnostics = errors.get()
        return exit to if (exit == 0) output else diagnostics + output
    }
}

/** 命令行选择器互斥，避免配置拼写错误时意外扩大处理范围。 */
internal data class FormattingOptions(
    val root: Path,
    val mode: String,
    val files: String? = null,
    val filesFrom: String? = null,
    val base: String? = null,
    val all: Boolean = false,
    val studioHome: String? = null,
) {
    init {
        require(mode == "apply" || mode == "check") { "Mode must be apply or check" }
        val scopeCount = listOf(files, filesFrom, base).count { it != null } + if (all) 1 else 0
        require(scopeCount <= 1) {
            "Use only one of --files, --files-from, --base or --all"
        }
        require(
            files == null || files.split(',')
                .all { it.isNotBlank() }) { "The file list must not be empty" }
    }

    companion object {
        /** 明确拒绝未知选项，防止自动化脚本静默忽略参数。 */
        fun parse(arguments: Array<String>): FormattingOptions {
            val values = mutableMapOf<String, String>()
            var all = false
            var index = 0
            // 完整读取并校验参数后再构造选项，未知选项与重复选项不能被静默忽略。
            while (index < arguments.size) {
                val option = arguments[index++]
                if (option == "--all") {
                    require(!all) { "Duplicate option: --all" }
                    all = true
                    continue
                }
                val knownOption = option in setOf(
                    "--root",
                    "--mode",
                    "--files",
                    "--files-from",
                    "--base",
                    "--studio-home"
                )
                require(knownOption) { "Unknown option: $option" }
                require(index < arguments.size && option !in values) { "Missing value or duplicate option: $option" }
                values[option] = arguments[index++]
            }
            // 互斥范围由构造器统一检查，必填参数在开始访问仓库前完成校验。
            return FormattingOptions(
                root = Path.of(requireNotNull(values["--root"]) { "Missing --root" }),
                mode = requireNotNull(values["--mode"]) { "Missing --mode" },
                files = values["--files"],
                filesFrom = values["--files-from"],
                base = values["--base"],
                all = all,
                studioHome = values["--studio-home"],
            )
        }
    }
}
