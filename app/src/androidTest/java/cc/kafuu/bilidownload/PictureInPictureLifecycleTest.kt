package cc.kafuu.bilidownload

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.feature.compose.activity.MediaPlayerActivity
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiState
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerViewModel
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 操作真实系统画中画的关闭与全屏动作，验证播放器随窗口可见性变化的生命周期。
 *
 * 使用独立的合成音视频，不访问任务数据库；标准无障碍动作不依赖菜单坐标或界面语言。
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class PictureInPictureLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var directory: File
    private lateinit var video: File
    private var originalPipEnabled = false
    private var originalAccessibilityFlags = 0

    /** 准备独立音视频，并允许测试通过无障碍节点操作系统小窗。 */
    @Before fun createFixtures() {
        originalPipEnabled = AppModel.pictureInPicturePlaybackEnabled
        originalAccessibilityFlags = instrumentation.uiAutomation.serviceInfo.flags
        AppModel.pictureInPicturePlaybackEnabled = true
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        // 有声媒体能覆盖实际音频时钟，持续时间足够长，避免自然播完被误认为关闭成功。
        directory = File(context.getExternalFilesDir(null), "resources/qa-pip-${System.nanoTime()}")
        assertTrue(directory.mkdirs())
        video = File(directory, "qa-with-audio.mp4")
        val session = FFmpegKit.executeWithArguments(arrayOf(
            "-y", "-f", "lavfi", "-i", "color=c=blue:s=160x90:r=10",
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=16000",
            "-t", "60", "-c:v", "mpeg4", "-c:a", "aac", video.path,
        ))
        assertTrue("Unable to create QA media: ${session.returnCode}", ReturnCode.isSuccess(session.returnCode))
    }

    /** 还原测试前的偏好与自动化标记，仅清理本用例创建的媒体。 */
    @After fun removeFixtures() {
        AppModel.pictureInPicturePlaybackEnabled = originalPipEnabled
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = originalAccessibilityFlags
        }
        if (::directory.isInitialized) directory.deleteRecursively()
    }

    /** 执行系统关闭动作后，小窗消失且原播放器停止推进，不能残留无入口的后台播放。 */
    @Test fun closingSystemPipWindowStopsPlayback() {
        ActivityScenario.launch<MediaPlayerActivity>(videoIntent()).use { scenario ->
            val player = enterPictureInPicture(scenario)

            // 标准关闭动作与系统关闭按钮调用相同的 PiP 退出流程，而非直接 finish 应用页面。
            performSystemPipAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)
            waitUntil("Playback continued after closing the system PiP window") {
                var stopped = false
                instrumentation.runOnMainSync { stopped = !player.playWhenReady && !player.isPlaying }
                stopped
            }

            // 暂停回调与音频时钟最终位置异步到达，先等位置稳定，再严格验证后续不再推进。
            var stoppedPositionMs = 0L
            var previousPositionMs = -1L
            var stableSinceMs = SystemClock.elapsedRealtime()
            waitUntil("Playback position did not settle after closing PiP") {
                var positionMs = 0L
                instrumentation.runOnMainSync { positionMs = player.currentPosition }
                if (positionMs != previousPositionMs) {
                    previousPositionMs = positionMs
                    stableSinceMs = SystemClock.elapsedRealtime()
                }
                SystemClock.elapsedRealtime() - stableSinceMs >= 250L
            }
            instrumentation.runOnMainSync { stoppedPositionMs = player.currentPosition }
            SystemClock.sleep(500)
            instrumentation.runOnMainSync {
                assertFalse(player.isPlaying)
                assertEquals(stoppedPositionMs, player.currentPosition)
            }
        }
    }

    /** 小窗恢复全屏继续同一播放会话，不能把退出 PiP 的所有情况都当作关闭处理。 */
    @Test fun expandingSystemPipWindowPreservesPlayback() {
        ActivityScenario.launch<MediaPlayerActivity>(videoIntent()).use { scenario ->
            val player = enterPictureInPicture(scenario)
            var positionBeforeExpandMs = 0L
            instrumentation.runOnMainSync { positionBeforeExpandMs = player.currentPosition }

            // 使用真实系统全屏动作，区分页面恢复可见与用户关闭小窗。
            performSystemPipAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)
            var playbackState = ""
            try {
                waitUntil("PiP did not return to a playing full-screen activity") {
                    var expanded = false
                    scenario.onActivity {
                        expanded = !it.isInPictureInPictureMode && player.isPlaying
                        playbackState = "pip=${it.isInPictureInPictureMode}, playing=${player.isPlaying}, " +
                            "ready=${player.playWhenReady}, state=${player.playbackState}, " +
                            "suppression=${player.playbackSuppressionReason}, position=${player.currentPosition}"
                    }
                    expanded
                }
            } catch (error: AssertionError) {
                throw AssertionError("PiP expansion failed: $playbackState", error)
            }
            scenario.onActivity {
                val state = ViewModelProvider(it)[MediaPlayerViewModel::class.java]
                    .uiStateFlow.value as MediaPlayerUiState.Playing
                assertSame(player, state.player)
                assertTrue(player.currentPosition >= positionBeforeExpandMs)
                assertEquals(1.5f, player.playbackParameters.speed, 0.01f)
            }
        }
    }

    /** 真正不可见时的暂停不能误伤配置重建中仍应继续的播放会话。 */
    @Test fun recreatingPlayingVideoPreservesPlayback() {
        ActivityScenario.launch<MediaPlayerActivity>(videoIntent()).use { scenario ->
            lateinit var player: Player
            waitUntil("Video did not start before recreation") {
                var playing = false
                scenario.onActivity {
                    val state = ViewModelProvider(it)[MediaPlayerViewModel::class.java].uiStateFlow.value
                    if (state is MediaPlayerUiState.Playing) {
                        player = state.player
                        playing = player.isPlaying
                    }
                }
                playing
            }

            // 使用非零进度和非默认倍速，检查重建前后没有暂停或重置原播放器。
            scenario.onActivity {
                player.seekTo(5_000L)
                ViewModelProvider(it)[MediaPlayerViewModel::class.java]
                    .onSetPlaybackSpeed(MediaPlayerUiIntent.SetPlaybackSpeed(1.5f))
            }
            waitUntil("Video did not resume after seeking") {
                var playing = false
                instrumentation.runOnMainSync { playing = player.isPlaying }
                playing
            }
            scenario.recreate()
            scenario.onActivity {
                val state = ViewModelProvider(it)[MediaPlayerViewModel::class.java]
                    .uiStateFlow.value as MediaPlayerUiState.Playing
                assertSame(player, state.player)
                assertTrue(player.isPlaying)
                assertTrue(player.currentPosition >= 5_000L)
                assertEquals(1.5f, player.playbackParameters.speed, 0.01f)
            }
        }
    }

    /** 等待真实播放开始，再经应用返回操作进入仍在播放的可见小窗。 */
    private fun enterPictureInPicture(scenario: ActivityScenario<MediaPlayerActivity>): Player {
        lateinit var player: Player
        waitUntil("Video did not start") {
            var playing = false
            scenario.onActivity {
                val state = ViewModelProvider(it)[MediaPlayerViewModel::class.java].uiStateFlow.value
                if (state is MediaPlayerUiState.Playing) {
                    player = state.player
                    playing = player.isPlaying
                }
            }
            playing
        }

        // 非默认倍速与非零位置能同时检查恢复全屏没有重建或重置播放器。
        scenario.onActivity {
            player.seekTo(5_000L)
            ViewModelProvider(it)[MediaPlayerViewModel::class.java]
                .onSetPlaybackSpeed(MediaPlayerUiIntent.SetPlaybackSpeed(1.5f))
        }
        waitUntil("Video did not resume after seeking") {
            var playing = false
            instrumentation.runOnMainSync { playing = player.isPlaying }
            playing
        }
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitUntil("Video did not enter PiP while playing") {
            var inPip = false
            scenario.onActivity { inPip = it.isInPictureInPictureMode && player.isPlaying }
            inPip
        }
        return player
    }

    /** 调用系统小窗提供的标准动作，等待窗口注册完成后再操作真实 PiP 控制器。 */
    private fun performSystemPipAction(action: AccessibilityNodeInfo.AccessibilityAction) {
        // 系统已注册小窗节点时入场动画仍可能在执行，等待界面空闲后再发出关闭或展开动作。
        instrumentation.uiAutomation.waitForIdle(500L, 5_000L)
        waitUntil("System PiP action was not available: ${action.id}") {
            instrumentation.uiAutomation.windows.any { window ->
                if (!window.isInPictureInPictureMode) return@any false
                val root = window.root ?: return@any false
                root.actionList.any { it.id == action.id } && root.performAction(action.id)
            }
        }
    }

    /** 为真实系统动画和播放器回调提供有上限的状态等待。 */
    private fun waitUntil(message: String, predicate: () -> Boolean) {
        val deadlineMs = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError(message)
    }

    /** 构造当前夹具的视频播放入口，沿用应用的 singleTask 路由。 */
    private fun videoIntent() = MediaPlayerActivity.buildIntent(video.path, "QA PiP with audio", "video/mp4").apply {
        setClass(context, MediaPlayerActivity::class.java)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
