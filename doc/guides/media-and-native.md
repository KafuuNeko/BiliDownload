# 媒体播放与原生代码

本专题用于 Media3 播放、后台音乐、频谱、FFmpeg 和 JNI/CMake 的修改。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 生命周期约束

- 本地视频沿用 `MediaPlayerViewModel` 的播放器管理；后台音乐由 `MusicPlaybackService` 持有播放器和 `MediaSession`。页面销毁与服务结束采用各自的释放边界。
- 播放界面负责展示和发送操作，播放器、Controller、Listener、进度任务和频谱分析器均应明确创建者、共享范围与释放者。
- 调整播放器生命周期时同时验证旋转、前后台切换、画中画、通知跳转和资源被删除等场景。
- PCM 和频谱处理明确采样格式、声道、时间戳及单位；新解码分支优先验证纯计算部分，避免在音频回调里执行阻塞 IO 或高频大内存分配。
- 修改 JNI 时同步核对 Kotlin `external` 声明、原生注册或符号、参数类型、数组与字符串释放，以及 CMake 源文件列表。
- 保留 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 四种 ABI 的构建与打包能力。修改本地 AAR、NDK、CMake 或第三方原生库时检查所有目标 ABI。
- 自有逻辑优先放在包装层；修改 `thirdparty/` 或替换 `ffmpeg-kit.aar` 时说明必要性、来源和验证范围，并保留许可证。

## 2. 播放所有权

| 能力 | 当前入口 | 修改时检查 |
| --- | --- | --- |
| 本地视频 | MediaPlayerViewModel / MediaPlayerActivity | Player.Listener、进度任务、窗口与画中画 |
| 后台音乐 | MusicPlaybackService / MusicPlayerViewModel | Service 持有播放器与 MediaSession，页面连接及展示 |
| 共享实时分析 | MusicPlaybackRuntime | 服务与前台频谱共享对象的线程边界 |
| 播放器创建 | MediaPlayerFactory | 渲染器与所需音频能力的统一配置 |
| 音频解码与频谱 | common/audio/pcm、common/audio/spectrum | 采样编码、时间戳、运算线程和内存开销 |

- 页面切换、配置变化、后台播放和真正停止播放是不同生命周期事件，不能统一执行一次全局释放。
- 监听器和定时进度任务避免重复注册；切换资源后旧播放器回调不得覆盖新资源状态。
- 播放错误、资源被删除、URI 授权变化时停止无效读取并更新页面反馈。
- 回收 Bitmap、频谱分片和共享数据前，确认渲染或播放端已不再使用，避免一侧释放另一侧仍访问的对象。

## 3. FFmpeg 与资源登记

FFmpeg 封装位于 `FFMpegUtils`，二进制来自本地 `app/libs/ffmpeg-kit.aar`。合成、音频转封装与容器转换沿用该入口，新增参数需明确编码、容器、MIME 和输出文件的关系。

- 正确传递含空格、引号和非 ASCII 字符的输入输出路径，避免参数被拆分。
- 使用应用专属工作目录，不直接把公共 URI 当成可用的原生文件路径。
- 返回码成功后核对输出可用性；失败或取消不能登记成品或删除唯一有效输入。
- 合成完成后的源文件清理遵循配置和任务状态，公共资源通过存储层发布。
- 修改转换逻辑时覆盖无音轨、无视频轨、单流输出及输入不可用等受影响分支。

## 4. PCM 与频谱

- 数据契约明确 PCM 编码、每采样字节数、端序、声道排列、采样率和时间单位。
- 处理未填满缓冲区、采样格式变化和流结束，避免数组越界或旧数据尾部参与计算。
- 分析取消与播放切换关联；旧音频的分析结果不发布到新曲目的 UI。
- UI 刷新做节流，音频回调不执行阻塞 IO 或持续分配大型对象。
- 可独立计算的编码映射和解码规则使用单元测试；实际时间同步、听感和设备性能用真实播放验证。

## 5. JNI 与依赖调整

- 修改 `external` 声明时同步检查 JNI 名称或注册表、签名、参数类型、返回值与异常边界。
- 字符串、数组、局部引用和原生堆资源按对应获取方式释放，失败分支不能泄漏。
- 自有逻辑放在 `app/src/main/cpp/src/`，修改源文件列表同步更新 CMake。
- 替换 AAR 或原生库时核对四种 ABI、需要的功能、来源与许可，不仅验证当前设备架构。
- 二维码相关改动同时检查 Kotlin 调用、原生编码、输出尺寸与错误输入行为。

代码入口：[MediaPlayerViewModel](../../app/src/main/java/cc/kafuu/bilidownload/feature/compose/viewmodel/mediaplayer/MediaPlayerViewModel.kt)、[MusicPlaybackService](../../app/src/main/java/cc/kafuu/bilidownload/service/MusicPlaybackService.kt)、[MusicPlaybackRuntime](../../app/src/main/java/cc/kafuu/bilidownload/common/audio/MusicPlaybackRuntime.kt)、[FFMpegUtils](../../app/src/main/java/cc/kafuu/bilidownload/common/utils/FFMpegUtils.kt)、[JNI](../../app/src/main/java/cc/kafuu/bilidownload/common/jni/)、[CMake](../../app/src/main/cpp/CMakeLists.txt)。

验证入口：[PcmDecoderTest](../../app/src/test/java/cc/kafuu/bilidownload/PcmDecoderTest.kt)、[PcmEncodingMapperTest](../../app/src/test/java/cc/kafuu/bilidownload/PcmEncodingMapperTest.kt)，构建与设备验证按[验证与交付](./testing-and-delivery.md)执行。
