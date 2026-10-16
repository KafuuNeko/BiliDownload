# 2.3.8.foss 发布前审计与 QA

审计日期：2026-10-16（Asia/Shanghai）。

## 结论

**已修复本轮确认的功能回归和 KDoc 缺口，已执行的自动化 QA 全部通过。** 尚未覆盖的平台与线上场景仍需按文末清单验收。

- **P2 / 已修复：视频播放页重建丢失进度、倍速和暂停状态。** 该回归由 `a580688` 引入，现已修复，并通过文件路径、Content URI 及新 Intent 场景复测。
- 重点审计的 `2.3.7.foss` 之后代码，在本轮覆盖的下载恢复、备用源、分阶段恢复、全部分 P、批量导出和统计展示场景中，未发现其他已证实的功能回归。这不代表未覆盖的平台及线上场景已通过。
- JVM 测试 **121/121 通过**；Android 设备测试 **22/22 通过**；Debug、四 ABI Release 及测试 APK 构建成功；Lint **0 errors / 87 warnings**。
- 当前 `versionName=2.3.7.foss`、`versionCode=20307` 符合“QA 通过后再迭代”的安排，不作为缺陷。生产改动限于播放器会话绑定与审计指出的文档契约；版本、签名和数据库结构未变，未触发发布。

## 审计范围与环境

| 项目 | 本轮范围 |
| --- | --- |
| 起点 | `2.3.5.foss`，`535c226dfbed7ac261d79d511393aec17c037fa5` |
| 重点分界 | `2.3.7.foss`，`127652018c4c0d1dab1ab313bfcdfce0e5f08424` |
| 审计基线 | `develop`，`7b9b444fc2e47700f7a412691147e11005be6baa`；修复复测同时覆盖本报告随附的修复及测试改动 |
| 变更规模 | 起点之后 21 个提交，193 个文件，9,944 行新增 / 1,306 行删除；重点分界之后 7 个提交 |
| 构建环境 | Windows、JDK 23、项目 Gradle Wrapper、SDK 37、NDK 28.1.13356709、CMake 3.31.0、本地 FFmpeg AAR |
| 运行环境 | Pixel 9 API 35 模拟器，Android 15，x86_64 |
| 安装方式 | `adb install -r` 覆盖安装 Debug 与测试 APK；未清空应用数据 |
| 测试数据 | 独立测试数据库、合成媒体和临时文件；无真实 B 站账号或会员 CDN 测试 |

审计依据包括 [编码规范](./coding-guidelines.md) 及下载、存储、网络、生命周期、媒体、状态与事件、注释和验证专题。原有 `.idea/misc.xml` 工作区改动未触碰。

## 已确认问题与修复

### QA-01 · P2 · Activity 重建重置播放状态（已修复）

**位置：** [MediaPlayerViewModel.onInit](../app/src/main/java/cc/kafuu/bilidownload/feature/compose/viewmodel/mediaplayer/MediaPlayerViewModel.kt)；调用入口为 [MediaPlayerActivity.onCreate / onNewIntent](../app/src/main/java/cc/kafuu/bilidownload/feature/compose/activity/MediaPlayerActivity.kt)。

**修复前根因：** `onCreate()` 每次都会发送初始化意图。原 `onInit()` 虽然复用 ViewModel 中的播放器，却每次都将倍速设回 1.0，重新 `setMediaItem()` / `prepare()`，并将 `playWhenReady` 设为 `true`。`a580688` 为支持切换媒体移除了原有初始化守卫，但没有区分同一媒体的页面重建和新媒体请求。

**修复前复现：** 使用 FFmpeg 生成 30 秒本地视频，启动播放页，暂停并定位到 12,000 ms，设为 1.5 倍速，执行 `ActivityScenario.recreate()`。当时最后一次运行实际为 `position=108, speed=1.0, playWhenReady=true`，预期仍为 `12000 / 1.5 / false`；重复运行稳定丢失状态。[首轮失败证据](../build/qa-instrumentation-final.log)予以保留。

**用户影响：** 触发 Activity 重建的配置变化会让正在观看的视频从头播放，且已暂停的媒体意外恢复播放。Manifest 已自行处理方向和屏幕尺寸变化，因此不能把本发现表述为“普通旋转必然重置”；本轮直接验证的是 Activity 重建路径，未分别操作每一种系统配置开关。

**修复实现：** 初始化意图增加默认关闭的 `restartPlayback`。页面重建时，ViewModel 根据实际播放 URI 识别已有会话并保留播放器、进度、倍速、暂停及错误状态；`onNewIntent()` 明确请求重新播放，并保存新的 Activity Intent，确保后续重建绑定最新媒体。即使新请求 URI 与当前相同，也能重新打开资源或从播放错误中重试。播放器仍仅由 ViewModel 持有和释放。

**验收：** [ReleaseQaTest](../app/src/androidTest/java/cc/kafuu/bilidownload/ReleaseQaTest.kt) 保留原回归断言，并扩展到文件路径及 Content URI；增加不同媒体新 Intent 切换后再次重建、错误状态重建后同 URI 重试、暂停后返回关闭三项测试。上述测试及画中画、后台音频均通过，[最终设备日志](../build/qa-fix-instrumentation-final.log)为 `OK (22 tests)`。

## 重点提交审查

| 提交 | 关注点 | 证据与结论 |
| --- | --- | --- |
| `fac6823` 自定义导出命名 | 单项与批量模板复用、文件名清理、超长 UTF-8 名称、扩展名与重名 | 命名相关 13 项 JVM 测试通过；设备导出验证不会覆盖已有文件。公共 API 的文档缺口已补齐 |
| `f12ab8a` 编码规范 | 规范与当前实现、交付流程的一致性 | 按专题核对；格式检查通过。审计指出的新增入口已补齐中文 KDoc，播放器改动补充会话契约和阶段注释 |
| `ee6f0d5` 列表统计 | 空值与 0、缓存/失败退避、并发与请求节流、回收复用、多选状态 | 仓库与模型/格式化 15 项 JVM 测试通过；设备验证回收、多选、窄宽度、浅深色及大字体 |
| `6525daf` 详情统计 | 请求期间快照展示、来源身份与异步更新 | 设备测试确认详情请求尚未完成时可显示传入统计；修正了原测试固定中文的设备语言假设 |
| `3e0406a` 全部分 P 与批量导出 | 分 P 去重、规格选择、取消、部分失败、固定目标集合、URI 与真实文件 | 新增 4 项全部分 P 用例，含 170 项顺序提交；新增 5 项真实 IO 导出用例，均通过 |
| `f41e3b9` 自动恢复与备用源 | Range/ETag/长度校验、缓存可信度、共享重试预算、刷新一次、取消与存储失败 | 19 项真实回环 HTTP 下载测试、2 项重试策略及 4 项源选择测试通过；检查 DownloadManager 的接入、并发槽位和规格匹配 |
| `7b9b444` 分阶段恢复与批量重试基础 | 原任务身份、失败阶段、发布检查点、去重与服务所有权、合成输入保护 | 新增 14 项恢复测试通过；现有后处理、服务登记测试通过；公共媒体发布失败后保留源文件并重试的设备测试通过 |

`2.3.5.foss` 至 `2.3.7.foss` 的审查另覆盖：批量列表与画质策略、地址/分享入口、通知导航、API 37 本地网络权限策略、重复任务与前台服务退出、选择弹窗取消、页面重建、稍后再看、后台音频及画中画。地址、权限分类、批量策略、服务登记有 JVM 验证；后台音频和画中画有本轮设备验证。分享选择器、通知点击、稍后再看线上接口未做端到端操作，不标为设备验收通过。

## 执行结果与证据

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| JVM 全量测试 | 21 个测试类，121 项通过，0 失败 / 错误 / 跳过 | [HTML 报告](../app/build/reports/tests/testDebugUnitTest/index.html)、[修复后构建日志](../build/qa-fix-final-build.log) |
| Android 设备测试 | 22 项运行，全部通过 | [修复后设备日志](../build/qa-fix-instrumentation-final.log) |
| Debug / Release / 测试 APK | 构建成功，四种目标 ABI 均有产物 | [修复后构建与 Lint 日志](../build/qa-fix-final-build.log) |
| Lint Debug | 0 errors / 87 warnings | [Lint 文本](../app/build/reports/lint-results-debug.txt)、[Lint HTML](../app/build/reports/lint-results-debug.html) |
| APK 元数据与 ZIP 对齐 | 四包的应用 ID、版本、minSdk 24、targetSdk 37 和单一 ABI 正确；`zipalign -c -P 16` 全通过 | [修复后包检查记录](../build/qa-fix-package-verification.log) |
| 64 位原生库 ELF 对齐 | arm64-v8a、x86_64 各 12 个 `.so` 的 LOAD 段对齐均为 `0x4000`（16 KiB） | [ELF 检查记录](../build/qa-native-alignment.csv) |
| 补丁格式 | 审计范围 diff、本轮已跟踪改动及新增文件无空白错误 | `git diff --check` 及新增文件独立检查 |

原生库对齐是静态检查，不等价于已在 16 KiB 页设备或四种 ABI 真机上运行。Release 产物为 unsigned，本轮未验证正式签名、线上更新覆盖安装或远程发布流程。

设备测试具体分布：

- Room 迁移 2 项：真实 schema 1 → 4、3 → 4，测试数据与关联保留。
- 应用上下文及公共存储 4 项：媒体发布到 Movies/Video 集合、删除释放有效载荷、检查点落库失败保留源文件且可再次发布。
- 统计卡片 5 项：快照传递、回收后清除旧统计、已知 0 展示、多选状态不被 payload 破坏、宽度变化及浅深色/大字体。
- 审计与修复累计新增 11 项：导出 5 项，画中画 1 项，后台音频 1 项，播放器重建 1 项，新 Intent 与错误恢复 2 项，正常返回 1 项，均通过。

画中画使用真实本地视频，确认返回后进入 PiP 且仍在播放；后台音频使用合成 AAC，确认页面进入后台后 MediaController 仍为播放状态且进度继续增长。静音合成音频及无窗口模拟器不能证明扬声器音质、蓝牙或耳机路由。

浅色、深色和大字体截图已人工检查，未发现统计行遮挡或重叠；窄宽度按设计隐藏收藏数，大字体标题省略显示。证据：[浅色](../build/qa-stats-light.png)、[深色](../build/qa-stats-dark.png)、[大字体](../build/qa-stats-large-font.png)。

上述 `build/` 与 `app/build/` 路径为本次本地证据，不纳入 Git；清理构建目录后需重新生成。

## 代码规范与交付范围

**P3 / 已修复：审计指出的新增 API 已补齐 KDoc。** [ExportFileNameUtils.kt](../app/src/main/java/cc/kafuu/bilidownload/common/utils/ExportFileNameUtils.kt) 已说明模板快照、回退、扩展名处理及重名责任；[SettingsViewModel.kt](../app/src/main/java/cc/kafuu/bilidownload/feature/compose/viewmodel/settings/SettingsViewModel.kt) 的三项导出模板观察方法已说明偏好持久化与状态刷新契约。播放器变更也补充了类型、初始化意图、宿主入口及会话阶段注释。依据为 [方法级 KDoc 规范](./guides/code-comments-and-kdoc.md#4-方法级-kdoc)，未扩展为全仓库历史注释重写。

Lint 警告中包括依赖版本建议 37 项、未使用资源 20 项、默认 Locale 9 项、复数候选 5 项和其他布局/样式建议。不能把版本建议或全部 87 条直接认定为本次新增缺陷；也不能把 Lint 无错误等同于完全符合代码规范。PiP 的静态建议已结合代码中条件调用及设备验证复核，未据此新增功能缺陷；网络 cleartext 基础配置继承了此前允许明文的策略，未作为本次新增回归。

**批量重试当前为基础能力。** `BatchRetryUseCase` 和 `DownloadService.retryTask()` 已存在，但生产调用链中尚无历史页按钮调用该用例；[方案文档](./issue-71-batch-retry-solution.md)也明确先实现公共命令与恢复，再接入按钮。因此本轮只认定底层用例已验证，不宣称“历史页批量重试已交付”。若 2.3.8.foss 计划宣传这一用户功能，仍需完成 UI 接入和相应验收。

## 本轮测试改动

审计与修复累计新增 **18 项 JVM + 11 项设备测试**，生产修复见 QA-01：

- [DownloadRecoveryQaTest.kt](../app/src/test/java/cc/kafuu/bilidownload/download/DownloadRecoveryQaTest.kt)：14 项阶段恢复、资格过滤、取消传播、部分失败计数、合成前置校验与源文件保护。
- [BatchDownloadUseCaseTest.kt](../app/src/test/java/cc/kafuu/bilidownload/BatchDownloadUseCaseTest.kt)：新增 4 项全部分 P 测试，包含 170 项、去重、空资源及取消。
- [ReleaseQaTest.kt](../app/src/androidTest/java/cc/kafuu/bilidownload/ReleaseQaTest.kt)：11 项平台测试，QA-01 的进度、倍速和暂停断言完整保留，修复后通过；另验证播放器实例被复用。
- [QaExportDirectory.kt](../app/src/androidTest/java/androidx/documentfile/provider/QaExportDirectory.kt)：仅用于测试 APK，以真实文件模拟 SAF 常用扩展名行为；源 URI 通过真实 ContentResolver 读取。该适配器不验证 DocumentsUI 授权或所有第三方文档提供器。

同时修正既有测试的环境假设：

- 两项下载测试在循环内清理上轮成品，避免 Windows 与 Android/Linux 的 `renameTo` 覆盖行为差异干扰检查点验证。
- 存储失败测试允许在请求前或第一次写入时发现错误；仍要求 STORAGE 分类、没有重试、没有成品。原因是不同平台的目录 `length()` 不一致。
- 详情统计测试按 Activity 当前语言核对文本，避免英文设备被固定中文 `1.2万` 断言误判；数字格式化自身仍有独立测试。
- 新设备测试的 MediaController 明确使用主线程 looper，并在该线程释放。修正测试夹具的线程问题后，后台播放通过。
- 新 Intent 测试重新构造请求，避免复用被 ActivityScenario 添加 `FLAG_ACTIVITY_CLEAR_TASK` 的启动 Intent；该标记会销毁宿主，不能代表普通外部播放请求。

首次离线构建因 Media3 1.11.0 尚未缓存失败，联网获取依赖后后续离线构建成功；没有通过改依赖版本规避问题。

## 可复现命令

在仓库根目录，准备本地 FFmpeg AAR 及项目需要的 SDK/NDK 后执行：

```powershell
.\gradlew.bat --no-daemon --console=plain --offline --continue :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease :app:assembleDebugAndroidTest

$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $qaAdb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
& $qaAdb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $qaAdb shell am instrument -w -r cc.kafuu.bilidownload.test/androidx.test.runner.AndroidJUnitRunner

git diff --check 2.3.5.foss..HEAD
git diff --check
```

设备命令适用于本轮 x86_64 模拟器。以 runner 最后的测试汇总判断成功，不能只看 adb 命令退出码；修复后已确认 `OK (22 tests)`。首次依赖未缓存时去掉 `--offline`。

## 发布前剩余验收

1. 在支持范围代表设备上验收 API 24 旧存储路径、API 37 本地网络授权及拒绝流程；本轮仅在 API 35 运行。
2. 使用真实媒体完成下载 → 合成/转封装 → 发布 → 导出 → 播放，并验证前台服务通知、暂停/继续、网络断开/恢复、进程终止后的检查点恢复。回环 HTTP 和分层测试不能替代这些完整链路。
3. 验收实际 SAF 目录选择/撤权、空间不足、长批次跨后台及系统资源压力；本轮覆盖了部分失败与文件保留的可控注入，未制造设备磁盘耗尽或杀进程故障。
4. 真实 B 站登录、稍后再看、会员清晰度、海外 CDN/VPN、自定义源及 AV1 等媒体规格按发布范围补测。本轮没有真实账号、线路吞吐或全编码矩阵证据。
5. 正式版本迭代后验证签名 Release 包的升级安装与代表 ARM 设备运行；确认发布说明不将尚未接入 UI 的批量重试描述为已交付。

已发现的功能回归和文档缺口均已处理，当前自动化 QA 全部通过。完整发布验收仍包含上述平台与线上场景，本报告不将未执行的验收标为通过。
