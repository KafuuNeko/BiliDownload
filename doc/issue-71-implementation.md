# Issue #71：下载自动恢复实现

实施日期：2026-09-28。问题依据见 [调研报告](./issue-71-investigation.md)。

## 结果

媒体传输的短暂失败现在先进入资源内部恢复，而不是立即终止整个下载组。主地址与备用地址始终保留，恢复耗尽后再显示分类失败原因；合成、发布及数据库状态编码保持原有契约。

| 能力 | 当前行为 |
| --- | --- |
| 恢复预算 | 每个资源首次尝试后最多 5 次恢复；同源重试、切源、刷新共用预算 |
| 等待 | 1/2/4/8/16 秒指数退避，加 0–250 毫秒抖动；不短于 Retry-After |
| 限流 | 支持秒数和 HTTP 日期；要求等待超过 60 秒时停止自动恢复，允许稍后手动重试 |
| 切源 | 网络中断先尝试原源续传；连续失败后换源，403/404 等可恢复 HTTP 错误直接尝试下一个源 |
| 刷新 | 候选轮换耗尽后最多刷新一次，严格按原 dashId/codecId 匹配，不静默降低清晰度 |
| 续传 | 新增同目录 `.part.resume`，保存源指纹、强 ETag 和长度；请求携带 Range/If-Range，检查 Content-Range 起点、总长度及版本 |
| 不可信缓存 | 跨源缺少强 ETag、版本或长度不一致、旧缓存缺少检查点时安全重下；已完成源资源继续复用 |
| 200/416 | 200 全量响应覆盖旧缓存；416 清空无效缓存后有界重试，不直接视为完成 |
| 探测 | 每源最多 64 KiB、3 秒，依据有效正文及样本吞吐排序，保留回退候选；读取失败或空正文不算可用 |
| 超时 | 下载专用客户端连接 20 秒、读取 30 秒；API 客户端超时不变 |
| 取消 | 在连接、正文读取、退避和地址刷新阶段传播取消；同组资源最终失败会取消其他子资源 |
| 文件交付 | 已知长度严格核验，空正文不交付；跨文件系统复制使用目标目录暂存文件，不暴露半截成品 |
| 展示 | 通知及详情页显示恢复次数；终态区分网络、HTTP、本地存储、响应校验和资源刷新失败 |
| 诊断 | 下载日志保留阶段相关信息、状态码、尝试次数和偏移；不改变现有接口拦截器日志行为 |

`DownloadFailure` 和 `DownloadRetry` 随运行态快照传递，不新增数据库列。进程重建后已失败任务仍显示通用失败文案；具体运行态错误没有持久化。自动重试期间使用原下载槽位，仍维持最多 3 个任务。

## 代码职责

- [ResourceDownloader](../app/src/main/java/cc/kafuu/bilidownload/common/download/ResourceDownloader.kt)：下载、恢复预算、候选轮换、正文校验及成品交付。
- [DownloadCheckpoint](../app/src/main/java/cc/kafuu/bilidownload/common/download/DownloadCheckpoint.kt)：续传身份、损坏检查点恢复及存储错误边界。
- [DownloadHttpCall](../app/src/main/java/cc/kafuu/bilidownload/common/download/DownloadHttpCall.kt)：响应头和正文全生命周期的取消。
- [DownloadSourceSelector](../app/src/main/java/cc/kafuu/bilidownload/common/download/DownloadSourceSelector.kt)：有界样本探测和候选排序。
- [DownloadManager](../app/src/main/java/cc/kafuu/bilidownload/common/manager/DownloadManager.kt)：任务调度、资源身份匹配、地址刷新和聚合快照；原子启动确保刚启动就暂停也会释放登记。
- [DownloadFeedbackText](../app/src/main/java/cc/kafuu/bilidownload/common/utils/DownloadFeedbackText.kt)：通知与详情共用的脱敏文案。

## 验证

本地回环 HTTP 测试直接运行生产传输实现，无真实账号、CDN 或新增依赖。新增 25 个专项测试，覆盖：

- 中途断流后的实际偏移续传、跨源有/无强校验标识、200 覆盖、416 重下。
- 错误 Content-Range、ETag 变化、总长度变化、旧/损坏检查点、未知长度及空正文。
- 备用源优先于刷新、刷新成功/失败、总恢复预算、Retry-After 和磁盘失败不重试。
- 退避、正文读取及刷新时取消，同组子资源失败时关闭其他资源连接。
- 空或截断探测正文、失败的自定义源回退、忽略 Range 时仍限制样本大小、探测取消。

执行 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline`：94 个 JVM 测试全部通过，Debug APK 构建成功，Lint 无错误（报告 66 条警告）。没有升级依赖、应用版本或修改数据库结构。

## 验证边界与设备验收

当前未连接 Android 设备，尚未验证真机前台服务、通知交互、海外线路和会员 CDN。需要用同一视频、清晰度、编码比较直连与中国线路，并执行下载中断网、恢复网络、暂停/继续、批量部分失败及前后台切换。

旧 `.part` 缺少身份元数据时第一次恢复会重新下载，以避免拼接错误；强 ETag 缺失的跨源切换也会重下。这可能增加流量。未知长度响应正常 EOF 只能提供传输层结束判断，不能证明服务端原始媒体完整。小样本排序不保证长期吞吐，也未实现跨任务历史评分。

本次处理下载可靠性。Issue 中 AV1 在不同播放器卡顿的问题仍需设备和媒体样本独立验证，下载恢复不改变视频编码。
