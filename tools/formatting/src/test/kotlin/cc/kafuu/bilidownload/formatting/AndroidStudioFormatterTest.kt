package cc.kafuu.bilidownload.formatting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** 验证 Android Studio 的跨平台启动器选择、版本校验与安装目录识别。 */
class AndroidStudioFormatterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun selectsNativeLaunchersForEachPlatformWithoutShellCommands() {
        val home = temporaryFolder.newFolder("Android Studio & 中文").toPath()
        listOf("bin/studio64.exe", "bin/studio.sh", "MacOS/studio").forEach { name ->
            Files.createDirectories(home.resolve(name).parent)
            Files.createFile(home.resolve(name))
        }
        assertEquals(
            listOf(home.resolve("bin/studio64.exe").toString(), "format"),
            AndroidStudio.launcher(home, "Windows 11")
        )
        assertEquals(
            listOf(home.resolve("bin/studio.sh").toString(), "format"),
            AndroidStudio.launcher(home, "Linux")
        )
        assertEquals(
            listOf(home.resolve("MacOS/studio").toString(), "format"),
            AndroidStudio.launcher(home, "Mac OS X")
        )
    }

    @Test
    fun rejectsUnexpectedStudioBuildBeforeLaunching() {
        val root = Path.of(System.getProperty("bilidownload.repository"))
        val home = temporaryFolder.newFolder("Wrong Studio").toPath()
        Files.writeString(home.resolve("build.txt"), "AI-0.0\n")
        expectFailure { AndroidStudio.locate(root, home.toString()) }
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
        val studio = AndroidStudio.locate(root, bundle.toString())
        assertEquals(home.toAbsolutePath(), studio.home)
        assertEquals(home.resolve("jbr/Contents/Home"), studio.javaHome)
    }
}

/** 避免依赖较新 JUnit 的异常断言，同时不吞掉测试断言本身。 */
internal fun expectFailure(action: () -> Unit) {
    val failure = runCatching(action).exceptionOrNull()
    assertTrue("Expected operation to fail", failure != null)
}
