package cc.kafuu.bilidownload.formatting

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** 定位 Android Studio 的原生命令行入口，固定构建版本以统一各平台的格式化结果。 */
internal class AndroidStudio private constructor(
    val home: Path,
    val command: List<String>,
    val build: String,
) {
    val javaHome: Path
        get() = home.resolve("jbr/Contents/Home").takeIf(Files::isDirectory) ?: home.resolve("jbr")

    companion object {
        /** 读取仓库固定版本，优先使用显式安装目录，并拒绝版本不符或存在歧义的安装。 */
        fun locate(root: Path, configuredHome: String? = null): AndroidStudio {
            val settings = Properties().apply {
                Files.newInputStream(root.resolve("tools/formatting/android-studio.properties"))
                    .use(::load)
            }
            val expectedBuild =
                requireNotNull(settings.getProperty("build")) { "Missing Android Studio build in version settings" }
            // 显式配置只校验指定目录，避免配置错误时静默使用另一套 IDE。
            val explicitHome = configuredHome ?: System.getenv("ANDROID_STUDIO_HOME")
            val homes = if (explicitHome != null) {
                listOf(normalizeHome(Path.of(explicitHome)))
            } else {
                defaultHomes().map(::normalizeHome).distinct().filter { launcher(it) != null }
            }
            val matching = homes.filter { build(it) == expectedBuild }
            require(matching.size == 1) {
                "Install Android Studio $expectedBuild and set ANDROID_STUDIO_HOME or -PandroidStudioHome to its installation. " +
                    "Found: ${homes.joinToString { "$it (${build(it) ?: "unavailable"})" }}"
            }
            val home = matching.single()
            // 安装包的启动器与 JBR 配套使用，保证本机和 CI 的格式化引擎一致。
            return AndroidStudio(
                home,
                requireNotNull(launcher(home)) { "No Android Studio launcher in $home" },
                expectedBuild
            )
        }

        /** 原生可执行文件接收参数列表，Windows 路径无需经过 cmd 的二次解释。 */
        internal fun launcher(
            home: Path,
            os: String = System.getProperty("os.name")
        ): List<String>? {
            val candidates = when {
                os.startsWith("Windows") -> listOf("bin/studio64.exe" to listOf("format"))
                os.startsWith("Mac") -> listOf(
                    "MacOS/studio" to listOf("format"),
                    "bin/format.sh" to emptyList()
                )

                else -> listOf("bin/studio.sh" to listOf("format"), "bin/format.sh" to emptyList())
            }
            return candidates.firstOrNull { Files.isRegularFile(home.resolve(it.first)) }
                ?.let { (file, args) ->
                    listOf(home.resolve(file).toString()) + args
                }
        }

        private fun normalizeHome(home: Path): Path =
            (if (Files.isDirectory(home.resolve("Contents"))) home.resolve("Contents") else home).toAbsolutePath()
                .normalize()

        private fun build(home: Path): String? =
            listOf(
                home.resolve("build.txt"),
                home.resolve("Resources/build.txt")
            ).firstOrNull(Files::isRegularFile)
                ?.let { Files.readString(it).trim() }

        private fun defaultHomes(): List<Path> {
            val user = Path.of(System.getProperty("user.home"))
            val candidates = mutableListOf<Path>()
            // 各平台只扫描常见安装位置，不遍历开发机的任意目录。
            val directories = when {
                System.getProperty("os.name").startsWith("Windows") -> listOfNotNull(
                    System.getenv("ProgramFiles")?.let { Path.of(it, "Android") },
                    System.getenv("LOCALAPPDATA")?.let { Path.of(it, "Programs") },
                )

                System.getProperty("os.name").startsWith("Mac") -> listOf(
                    Path.of("/Applications"),
                    user.resolve("Applications")
                )

                else -> {
                    candidates += listOf(
                        Path.of("/opt/android-studio"),
                        Path.of("/usr/local/android-studio"),
                        user.resolve("android-studio")
                    )
                    emptyList()
                }
            }
            // 同时保留带版本后缀的安装目录，交由 locate 统一校验固定构建。
            directories.filter(Files::isDirectory).forEach { directory ->
                Files.list(directory).use { entries ->
                    entries.filter { it.fileName.toString().startsWith("Android Studio") }
                        .forEach(candidates::add)
                }
            }
            return candidates
        }
    }
}
