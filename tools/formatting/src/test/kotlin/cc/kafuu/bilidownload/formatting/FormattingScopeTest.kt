package cc.kafuu.bilidownload.formatting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

/** 验证 Git 范围选择、跨平台路径及批次写入的保护边界。 */
class FormattingScopeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var mRoot: Path

    @Before
    fun setUp() {
        mRoot =
            temporaryFolder
                .newFolder("repository with spaces 中文")
                .toPath()
                .toRealPath()
        Files.copy(
            Path.of(System.getProperty("bilidownload.repository"), ".editorconfig"),
            mRoot.resolve(".editorconfig")
        )
        write(name = ".gitignore", source = "**/build/\n")
        write(name = "Main.kt", source = "val value = 1\n")
        write(name = "Deleted.kt", source = "val removed = 1\n")
        git(arguments = arrayOf("init"))
        commit()
    }

    @Test
    fun defaultsToModifiedStagedAndUntrackedSources() {
        write(name = "Main.kt", source = "val value=2\n")
        write(name = "Staged.kt", source = "val staged=1\n")
        git(arguments = arrayOf("add", "Staged.kt"))
        write(name = "新 文件.kt", source = "val fresh=1\n")
        write(name = "Readme.md", source = "not Kotlin")
        write(name = "nested/build/Generated.kt", source = "invalid generated source")
        Files.delete(mRoot.resolve("Deleted.kt"))
        assertEquals(setOf("Main.kt", "Staged.kt", "新 文件.kt"), selected())
    }

    @Test
    fun includesCommittedBranchChangesWhenBaseIsGiven() {
        val base = git(arguments = arrayOf("rev-parse", "HEAD")).trim()
        write(name = "Committed.kt", source = "val committed = 1\n")
        commit()
        write(name = "Dirty.kt", source = "val dirty=1\n")
        assertEquals(setOf("Committed.kt", "Dirty.kt"), selected(base = base))
    }

    @Test
    fun includesRenamedSourceAtItsNewPath() {
        git(arguments = arrayOf("mv", "Main.kt", "Renamed.kt"))
        assertEquals(setOf("Renamed.kt"), selected())
    }

    @Test
    fun supportsFileListsAndExplicitPathsWithSpaces() {
        write(name = "新 文件.kt", source = "val fresh=1\n")
        write(name = "Comma,Name.kt", source = "val comma=1\n")
        write(name = "selected.txt", source = "新 文件.kt\nComma,Name.kt\n")
        val options = FormattingOptions(root = mRoot, mode = "check", filesFrom = "selected.txt")
        assertEquals(
            setOf("新 文件.kt", "Comma,Name.kt"),
            SourceFiles(root = mRoot)
                .select(options = options)
                .map { it.fileName.toString() }
                .toSet(),
        )
        assertEquals(setOf("新 文件.kt"), selected(files = "新 文件.kt"))
    }

    @Test
    fun rejectsAmbiguousOutsideMissingAndGeneratedPaths() {
        expectFailure {
            FormattingOptions(
                root = mRoot,
                mode = "apply",
                all = true,
                files = "Main.kt"
            )
        }
        expectFailure { selected(files = "../Outside.kt") }
        expectFailure { selected(files = "Missing.kt") }
        write(name = "build/Generated.kt", source = "val generated = 1")
        expectFailure { selected(files = "build/Generated.kt") }
        expectFailure { FormattingOptions.parse(arguments = arrayOf("--unknown")) }
    }

    @Test
    fun checkIsReadOnlyAndApplyIsLimitedToExplicitScope() {
        val before = "val value=2\n"
        write(name = "Main.kt", source = before)
        write(name = "Other.kt", source = before)
        val messages = mutableListOf<String>()
        val options = FormattingOptions(root = mRoot, mode = "check", files = "Main.kt")
        val formatter =
            SourceFormatter { _, sources -> sources.map { "val value = 2\n".toByteArray(UTF_8) } }
        assertEquals(
            1,
            runFormatting(options = options, report = messages::add, formatter = formatter)
        )
        assertEquals(before, Files.readString(mRoot.resolve("Main.kt")))
        assertEquals(
            0,
            runFormatting(
                options = options.copy(mode = "apply"),
                report = messages::add,
                formatter = formatter
            )
        )
        assertEquals("val value = 2\n", Files.readString(mRoot.resolve("Main.kt")))
        assertEquals(before, Files.readString(mRoot.resolve("Other.kt")))
        assertEquals(
            0,
            runFormatting(options = options, report = messages::add, formatter = formatter)
        )
        assertTrue(messages.any { it.contains("need formatting") })
    }

    @Test
    fun validationFailureDoesNotWriteAnySelectedSource() {
        val before = "val value=2\n"
        write(name = "Main.kt", source = before)
        val options = FormattingOptions(root = mRoot, mode = "apply", files = "Main.kt")
        expectFailure {
            runFormatting(
                options = options,
                report = {},
                formatter = SourceFormatter { _, _ -> error("Native formatter failed") })
        }
        assertEquals(before, Files.readString(mRoot.resolve("Main.kt")))
    }

    @Test
    fun concurrentChangesPreventTheEntireBatchFromBeingWritten() {
        val before = "val value=2\n"
        write(name = "Main.kt", source = before)
        write(name = "Other.kt", source = before)
        val options = FormattingOptions(root = mRoot, mode = "apply", files = "Main.kt,Other.kt")
        val formatter = SourceFormatter { _, sources ->
            write(name = "Other.kt", source = "val edited = 3\n")
            sources.map { "val value = 2\n".toByteArray(UTF_8) }
        }
        expectFailure { runFormatting(options = options, report = {}, formatter = formatter) }
        assertEquals(before, Files.readString(mRoot.resolve("Main.kt")))
        assertEquals("val edited = 3\n", Files.readString(mRoot.resolve("Other.kt")))
    }

    @Test
    fun rejectsMalformedUtf8WithoutReplacingBytes() {
        val path = mRoot.resolve("Invalid.kt")
        val bytes = byteArrayOf(0x76, 0x61, 0x6c, 0x20, 0xFF.toByte())
        Files.write(path, bytes)
        val options = FormattingOptions(root = mRoot, mode = "apply", files = "Invalid.kt")
        expectFailure { runFormatting(options = options, report = {}) }
        assertTrue(bytes.contentEquals(Files.readAllBytes(path)))
    }

    @Test
    fun rejectsSymlinksEscapingTheRepository() {
        val outside = temporaryFolder.newFile("Outside.kt").toPath()
        val link = mRoot.resolve("Link.kt")
        try {
            Files.createSymbolicLink(link, outside)
        } catch (exception: Exception) {
            // Windows 未启用开发者模式时可能禁止创建符号链接。
            assumeNoException("Symbolic links are unavailable on this host", exception)
        }
        expectFailure { selected(files = "Link.kt") }
    }

    private fun selected(
        files: String? = null,
        base: String? = null,
    ): Set<String> =
        SourceFiles(root = mRoot)
            .select(
                options = FormattingOptions(
                    root = mRoot,
                    mode = "check",
                    files = files,
                    base = base
                )
            )
            .map {
                mRoot
                    .relativize(it)
                    .toString()
                    .replace(oldChar = '\\', newChar = '/')
            }.toSet()

    private fun write(
        name: String,
        source: String,
    ) {
        val path = mRoot.resolve(name)
        Files.createDirectories(path.parent)
        Files.writeString(path, source, UTF_8)
    }

    private fun commit() {
        git(arguments = arrayOf("add", "."))
        git(
            arguments = arrayOf(
                "-c",
                "user.name=Formatter Test",
                "-c",
                "user.email=formatter@example.invalid",
                "commit",
                "-m",
                "fixture"
            )
        )
    }

    private fun git(vararg arguments: String): String {
        // 不执行开发机的全局 Git hooks，也不让测试提交触发个人签名配置。
        val command =
            listOf(
                "git",
                "-c",
                "core.autocrlf=false",
                "-c",
                "commit.gpgSign=false",
                "-c",
                "core.hooksPath=${mRoot.resolve("disabled-hooks")}",
                "-C",
                mRoot.toString(),
            ) + arguments
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader(UTF_8).use { it.readText() }
        check(process.waitFor() == 0) { output }
        return output
    }
}
