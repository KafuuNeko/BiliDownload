# Issue #71：最新需求与批量重试方案

调研日期：2026-09-30，日期按北京时间记录。代码基线：`develop / f41e3b9dd5b60a87431db7f07e74815ffad62b4b`。本次为方案调研，未修改应用实现。

## 结论

下一步应优先补齐“重试全部失败任务”。原始反馈中的短暂传输失败已有自动恢复实现；用户后续提出的是 VPN 断开造成部分任务最终失败后，在网络恢复时一次提交全部失败任务，无需进入每个详情页点击重试。

建议在现有“失败”页提供“重试全部（N）”，复用原任务及缓存，交给后台服务按失败阶段恢复；不重新创建下载记录，不再次选择清晰度或编码。首期无需新增依赖、数据库字段或修改既有状态编码。

网络路径不稳定仍是最符合用户对照反馈的解释，但具体 CDN、HTTP 错误和运营商链路尚未定位。自动恢复和批量重试改善容错与操作成本，不能保证直连与中国线路的速度、成功率相同。

## 需求依据与交付现状

| 事项 | 最新证据与判断 |
| --- | --- |
| 原始问题 | 已登录且有会员，1080P+ 下载慢、频繁失败；普通 1080P 可能正常仅为用户猜测。见 [Issue 正文](https://github.com/KafuuNeko/BiliDownload/issues/71)。 |
| 网络对照 | 用户位于马来西亚；中国线路下先测试 32 个、随后测试 170 多个短视频，报告全部成功；默认源、自动探测、自定义 Host 也曾失败。这支持路径或出口相关问题，不能确定是哪种网络机制。见 [32 个视频反馈](https://github.com/KafuuNeko/BiliDownload/issues/71#issuecomment-5856740159)、[170 多个视频及下载源设置反馈](https://github.com/KafuuNeko/BiliDownload/issues/71#issuecomment-5872517447)。 |
| 新增明确需求 | 9 月 29 日，用户以“30 个视频成功 20 个、VPN 断开导致 10 个失败”为例，希望一次重试所有失败视频。见 [功能请求](https://github.com/KafuuNeko/BiliDownload/issues/71#issuecomment-5880322016)。 |
| 维护者承诺 | 9 月 30 日回复将在下一版本处理。见 [维护者回复](https://github.com/KafuuNeko/BiliDownload/issues/71#issuecomment-5901361192)。 |
| 已有开发实现 | `f41e3b9` 已加入每资源最多 5 次额外恢复、备用源切换、一次地址刷新、可信断点续传、错误分类和探测改进。见 [实现说明](./issue-71-implementation.md)。 |
| 发布边界 | 调研时 GitHub latest 为 [2.3.7.foss](https://github.com/KafuuNeko/BiliDownload/releases/tag/2.3.7.foss)，发布时间为 2026-09-10。GitHub compare 显示当前基线比该标签领先 6 个提交；自动恢复补丁不在该发布标签内。反馈者安装版本仍未知。 |
| 剩余缺口 | 历史页没有批量重试入口；单项重试直接启动原任务服务。自动重试耗尽后，用户仍需逐个处理。 |

9 月 28 日的[原始调研](./issue-71-investigation.md)描述的是修复前基线，应与后续实现说明及本文一起阅读。

## 当前代码可以复用的能力

| 能力 | 代码依据 | 对方案的影响 |
| --- | --- | --- |
| 失败任务页 | [HomeFragment.kt:52](../app/src/main/java/cc/kafuu/bilidownload/feature/viewbinding/view/fragment/HomeFragment.kt#L52) | 已包含 `DOWNLOAD_FAILED`、`SYNTHESIS_FAILED`、`PUBLISH_FAILED`，按钮范围应与这三种状态一致。 |
| 历史页业务状态 | [HistoryViewModel.kt:34](../app/src/main/java/cc/kafuu/bilidownload/feature/viewbinding/viewmodel/fragment/HistoryViewModel.kt#L34) | 有展示快照、稳定任务 ID、多选、批量导出和删除，可沿用 XML/LiveData 体系。 |
| 单项重试 | [HistoryDetailsViewModel.kt:115](../app/src/main/java/cc/kafuu/bilidownload/feature/viewbinding/viewmodel/activity/HistoryDetailsViewModel.kt#L115) | 已按原数据库任务 ID 启动服务，不必创建新记录。 |
| 服务去重 | [DownloadService.kt:113](../app/src/main/java/cc/kafuu/bilidownload/service/DownloadService.kt#L113)、[DownloadServiceTaskRegistry.kt](../app/src/main/java/cc/kafuu/bilidownload/service/DownloadServiceTaskRegistry.kt) | 同一服务实例会过滤重复启动，并按最后有效 startId 判断停止。 |
| 执行器去重及队列 | [DownloadManager.kt:81](../app/src/main/java/cc/kafuu/bilidownload/common/manager/DownloadManager.kt#L81)、[排队入口:134](../app/src/main/java/cc/kafuu/bilidownload/common/manager/DownloadManager.kt#L134) | 已检查运行、排队和正在请求地址的任务；继续复用最多 3 个媒体任务的并发上限。 |
| 前置请求并发 | [DownloadService.kt:86](../app/src/main/java/cc/kafuu/bilidownload/service/DownloadService.kt#L86) | 视频详情同步最多 4 项，不必让批量重试在页面预先请求全部播放流。 |
| 原规格及缓存 | [资源匹配:279](../app/src/main/java/cc/kafuu/bilidownload/common/manager/DownloadManager.kt#L279)、[下载恢复:436](../app/src/main/java/cc/kafuu/bilidownload/common/manager/DownloadManager.kt#L436) | 原 DASH 记录保存 `dashId + codecId`；已完成子资源复用，未完成资源继续通过 `.part` 与检查点处理。 |
| 状态查询 | [DownloadTaskDao.kt:23](../app/src/main/java/cc/kafuu/bilidownload/common/room/dao/DownloadTaskDao.kt#L23)、[DownloadRepository](../app/src/main/java/cc/kafuu/bilidownload/common/room/repository/DownloadRepository.kt) | 已有按状态查询任务的方法，无需为批量按钮新增持久化字段。 |

## 推荐交互

- 在“失败”页提供常驻操作“重试全部（N）”，不要求先长按进入多选；N 表示当前可提交的失败任务数，已知正在恢复的任务应排除。
- 点击时固定当前失败任务的 ID 集合，提交过程中列表刷新不会追加新失败任务；再次失败的条目可在本次提交结束后重新操作。
- 没有可重试任务时禁用按钮。提交时展示短暂忙碌状态，并与导出、删除等批量操作互斥，入口同时做业务守卫。
- 反馈使用“已发送 X 项重试请求，跳过 Y 项，提交失败 Z 项”。发出服务请求与任务实际入队、下载成功是不同结果，不能提示“已全部下载成功”。
- 已提交任务继续由 Service 管理。页面退出只影响尚未提交的请求，不停止已经交给后台的任务。
- 首期以“重试全部”为必要范围；多选栏的“重试所选”可以后续复用同一用例，不作为解决本次需求的前置条件。

建议为下载历史页增加专用操作区，避免在共享 `fragment_rv.xml` 的所有资源列表上显示该按钮。现有多选栏已有四类控件，直接再增加按钮需要额外检查窄屏与英文文案布局。

## 推荐调用链与接口契约

```text
失败页点击“重试全部”
  → HistoryViewModel 固定失败任务 ID 快照并进入提交状态
  → BatchRetryUseCase 按 ID 去重，逐项重新查询数据库
  → 只发送当前仍为失败状态且未在已知下载组中的任务
  → DownloadService 接收明确的重试请求，登记后再次校验实时状态
  → 按下载 / 合成 / 发布阶段继续执行
  → 现有队列、通知及 Room 观察更新列表
```

建议新增 `BatchRetryUseCase`，用构造参数注入任务查询、在途检查和提交函数，便于验证部分失败与取消。结果至少包括提交数、跳过数和提交失败数；单项异常不能中断后续条目，`CancellationException` 始终向上传播。

在 `DownloadService` 增加明确的重试入口，例如 `retryTask(context, taskId)`，Intent 携带“仅允许失败任务”的命令标记。现有 `startDownload()` 仍可承担新任务启动和暂停后继续。单项详情重试与批量重试统一走新的入口。

页面侧的过滤只改善体验，Service 必须在执行前读取最新任务记录，并在任务登记的保护下只接受三类失败状态。已删除、已完成、已恢复或仍在处理的任务跳过，保证旧列表快照不会触发重复执行。继续复用登记类与执行器的去重边界，不把按钮禁用当作并发保护。

首期继续逐项发送任务 ID 的服务请求，复用现有入口规模；不要在页面并行启动所有播放流请求。提交成功只表示启动请求已发出；若需要展示准确的后台接受数，应增加 Service 回执契约，再使用“已入队”文案。

## 按失败阶段恢复

| 原状态 | 建议行为 | 当前边界 |
| --- | --- | --- |
| `DOWNLOAD_FAILED` | 重新取得播放流，严格匹配原清晰度和编码；复用已完成资源并按检查点恢复未完成缓存。 | 基本链路已可复用；详情同步失败时保留原失败任务及缓存。 |
| `SYNTHESIS_FAILED` | 核验已登记源资源，清理失败的合成输出后重试合成，沿现有单线程合成执行器运行。 | `assigningTask()` 当前仅为 `SYNTHESIS` 提供专用恢复分支，未覆盖 `SYNTHESIS_FAILED`；直接照搬下载重试会先请求详情、播放流，甚至重新下载已移动或重命名的源文件。 |
| `PUBLISH_FAILED` | 从发布检查点继续，复用有效工作文件，不重新下载或合成。 | `assigningTask()` 已有专用分支，可复用并补充批量场景验证。 |

合成重试应先确认源文件仍可用，并检查 `deleteDownloadTaskMixedResource()` 的 Boolean 结果；清理失败或源文件缺失时保留可诊断失败，不盲目执行。服务登记应持续到实际恢复阶段结束，不能只在把合成工作交给执行器后就释放。

### 不应直接把失败任务重置为 PREPARE

[DownloadService.kt:192](../app/src/main/java/cc/kafuu/bilidownload/service/DownloadService.kt#L192) 的详情同步失败逻辑会删除 `PREPARE` 任务；[删除实现](../app/src/main/java/cc/kafuu/bilidownload/common/room/repository/DownloadRepository.kt#L315)还会清理缓存与关联资源。如果批量重试先统一写 `PREPARE`，VPN 尚未恢复时可能丢失原失败记录及恢复依据。

首期建议保留失败持久态直到实际执行进入对应阶段，短暂的提交与排队状态由服务登记和执行器运行态表达。待启动任务可能暂时仍在失败列表中，UI 应显示正在恢复或排队，并过滤可观察到的在途任务；若需覆盖详情同步前的阶段，要补充服务登记状态的可观察接口。进程在实际启动前退出时，原失败记录仍可再次重试。

如果另行要求“提交后立即从失败页移动到进行中”，需同时设计可靠的持久化排队状态和恢复规则，并修正上述删除边界；不宜仅在 UI 预写状态。

## 方案比较与实施范围

| 方案 | 收益与问题 | 结论 |
| --- | --- | --- |
| UI 循环调用当前 `startDownload()` | 改动少，但旧快照缺少失败资格校验；合成失败不能直接从合成阶段恢复，结果也只能表示请求发出。 | 可做验证原型，不建议作为完整交付。 |
| 独立批量用例 + Service 重试命令 + 阶段恢复 | 保留原任务，复用调度、缓存和发布检查点；单项与批量契约一致，范围明确。 | 推荐。 |
| 新增 WorkManager 链或另一个下载器 | 引入新的执行所有者，与当前服务、队列、缓存和通知契约重复；无证据表明当前需求需要替换调度架构。 | 本次无需采用。 |

预计改动集中于：

- `common/download/BatchRetryUseCase.kt`：新增去重、过滤、提交和准确计数。
- `DownloadService`：重试命令、执行前资格校验、合成失败的阶段恢复及登记释放边界。
- `HistoryViewModel` / `DownloadHistoryFragment`：失败快照、批量提交状态及操作入口。
- `HistoryDetailsViewModel`：单项重试复用公共命令。
- 下载历史操作布局、图标及 `values` / `values-zh` 文案；必要的服务运行态观察。
- 对应用例、服务登记与恢复分支验证。需要更明确的查询或状态契约时扩展 Repository/DAO，但不新增表结构。

先实现公共重试命令与阶段恢复，再接入批量按钮。复杂度主要在状态与后台时序，按钮循环本身不是完整解决方案。

## 验收重点

| 场景 | 必须验证的结果 |
| --- | --- |
| 30 项中 20 项完成、10 项失败 | 一次操作仅向这 10 项发送重试请求，原 taskId、DASH 规格及已完成文件保持可复用。 |
| 点击后列表变化 | 本次目标集合固定；变为完成、删除或正在执行的任务在服务侧跳过。 |
| 快速连点、单项与批量同时重试 | 同一任务不会执行两次；服务不会提前停止其他已登记任务。 |
| VPN 恢复 / 仍断开 | 恢复后正常推进；仍失败时保留原记录与缓存，且不无限重试。 |
| 音频完成、视频失败 | 已完成音频复用；视频按可信检查点续传，身份不可信时允许安全重下。 |
| 合成失败 | 使用已登记源文件，无多余播放流请求；批量合成串行执行，失败清理不误删有效源资源。 |
| 发布失败 | 从发布检查点继续，不重新下载、不产生重复公共资源。 |
| 单项提交异常或批次取消 | 其他项继续；取消停止后续提交，已交给服务的任务不被页面取消。 |
| 170 多项、旋转与页面退出 | 前置请求与下载并发受现有上限控制，已提交任务继续由后台处理。 |

实施时补充 `BatchRetryUseCase` 的业务测试及相关恢复分支测试，并执行 Debug 构建与相关静态检查；前台服务、通知、真实文件恢复、VPN 切换和后台生命周期需设备验收。

本次仅完成 GitHub 最新评论、发布边界和本地调用链核对，并检查文档格式及链接；未运行应用构建、单元测试、真机或海外 CDN 实验。[既有实现说明](./issue-71-implementation.md)中的验证记录属于此前实施，不作为本次重新验证的结果。

## 原始问题的后续方向

在交付批量重试及现有自动恢复补丁后，收集应用版本、BV/CID、编码规格、失败阶段、脱敏错误分类与 CDN 主机，并用同一批视频比较直连和中国线路。探测样本及失败回退已经改进，目前没有证据支持指定某个固定海外 Host 为普遍最优源。

AV1 播放兼容性仍应独立排查。当前内置播放器使用 ExoPlayer，设备支持取决于实际平台解码器；能播放某个低码率 4K 视频不代表能流畅解码另一种规格。音视频合成使用流复制，不会自动把 AV1 转为 AVC。依据：[Media3 官方格式说明](https://developer.android.com/media/media3/exoplayer/supported-formats#sample-formats)、[播放器初始化](../app/src/main/java/cc/kafuu/bilidownload/feature/compose/viewmodel/mediaplayer/MediaPlayerViewModel.kt#L78)、[合成参数](../app/src/main/java/cc/kafuu/bilidownload/common/utils/FFMpegUtils.kt#L88)。
