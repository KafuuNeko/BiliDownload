package cc.kafuu.bilidownload.formatting

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** 验证 Android Studio 的跨平台启动器选择、版本校验与安装目录识别。 */
class AndroidStudioFormatterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** 原生引擎在真实脚本路径上验证稳定性，同时处理 Windows 行尾并保留两份原文。 */
    @Test
    fun formatsKotlinAndGradleScriptsInCopiesWithoutTouchingOriginal() {
        val repository = Path.of(System.getProperty("bilidownload.repository"))
        val root = temporaryFolder.newFolder("Native formatter 中文").toPath().toRealPath()
        // 使用真实项目配置与原生引擎，覆盖 Gradle 顶层调用被普通 Kotlin 副本误判的情况。
        listOf(
            ".editorconfig",
            ".idea/codeStyles/Project.xml",
            "tools/formatting/android-studio.properties"
        )
            .forEach { relative ->
                val target = root.resolve(relative)
                Files.createDirectories(target.parent)
                Files.copy(repository.resolve(relative), target)
            }
        val source = root.resolve("Main.kt")
        val before = "val value = 1\r\n".toByteArray(UTF_8)
        Files.write(source, before)
        val script = root.resolve("build.gradle.kts")
        val scriptBefore =
            "plugins {\n    id(\"example.plugin\")\n}\n\nprintln(\"configured\")\n".toByteArray(
                UTF_8
            )
        Files.write(script, scriptBefore)

        val formatter =
            AndroidStudioFormatter(mStudioHome = System.getProperty("bilidownload.studio.home"))
        val result = formatter.format(
            root = root,
            sources = listOf(
                SourceSnapshot(path = source, before = before),
                SourceSnapshot(path = script, before = scriptBefore)
            )
        )
        assertArrayEquals(before, Files.readAllBytes(source))
        assertArrayEquals(scriptBefore, Files.readAllBytes(script))
        assertEquals("val value = 1\n", result[0].toString(UTF_8))
        assertArrayEquals(scriptBefore, result[1])
    }

    @Test
    fun selectsNativeLaunchersForEachPlatformWithoutShellCommands() {
        val home = temporaryFolder.newFolder("Android Studio & 中文").toPath()
        listOf("bin/studio64.exe", "bin/studio.sh", "MacOS/studio").forEach { name ->
            Files.createDirectories(home.resolve(name).parent)
            Files.createFile(home.resolve(name))
        }
        assertEquals(
            listOf(home.resolve("bin/studio64.exe").toString(), "format"),
            AndroidStudio.launcher(home = home, os = "Windows 11")
        )
        assertEquals(
            listOf(home.resolve("bin/studio.sh").toString(), "format"),
            AndroidStudio.launcher(home = home, os = "Linux")
        )
        assertEquals(
            listOf(home.resolve("MacOS/studio").toString(), "format"),
            AndroidStudio.launcher(home = home, os = "Mac OS X")
        )
    }

    @Test
    fun rejectsUnexpectedStudioBuildBeforeLaunching() {
        val root = Path.of(System.getProperty("bilidownload.repository"))
        val home = temporaryFolder.newFolder("Wrong Studio").toPath()
        Files.writeString(home.resolve("build.txt"), "AI-0.0\n")
        expectFailure { AndroidStudio.locate(root = root, configuredHome = home.toString()) }
    }

    @Test
    fun locatesMacBundleResourcesAndRuntime() {
        val root = Path.of(System.getProperty("bilidownload.repository"))
        val settings = Properties().apply {
            Files.newInputStream(root.resolve("tools/formatting/android-studio.properties"))
                .use(::load)
        }
        val bundle = temporaryFolder.newFolder("Android Studio.app").toPath()
        val home = bundle.resolve("Contents")
        Files.createDirectories(home.resolve("Resources"))
        Files.createDirectories(home.resolve("jbr/Contents/Home"))
        Files.writeString(home.resolve("Resources/build.txt"), settings.getProperty("build"))
        listOf("bin/studio64.exe", "bin/studio.sh", "MacOS/studio").forEach { name ->
            Files.createDirectories(home.resolve(name).parent)
            Files.createFile(home.resolve(name))
        }
        val studio = AndroidStudio.locate(root = root, configuredHome = bundle.toString())
        assertEquals(home.toAbsolutePath(), studio.home)
        assertEquals(home.resolve("jbr/Contents/Home"), studio.javaHome)
    }
}

/** 避免依赖较新 JUnit 的异常断言，同时不吞掉测试断言本身。 */
internal fun expectFailure(action: () -> Unit) {
    val failure = runCatching(action).exceptionOrNull()
    assertTrue("Expected operation to fail", failure != null)
}
