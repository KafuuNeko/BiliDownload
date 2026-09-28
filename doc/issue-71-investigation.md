# Issue #71：海外网络下载频繁失败调研

调研日期：2026-09-28。代码基线：`develop / 3e0406ac8edb8ebcafd1caa78039d23b40358955`。本次仅新增调研文档，没有修改应用实现或向 GitHub 发布评论。

## 结论

最符合现有证据的解释是：反馈者访问 Bilibili 媒体 CDN 的网络路径不稳定，而客户端缺少传输中断后的自动恢复与备用地址切换，使瞬时故障直接变为需要手动处理的下载失败。

网络原因有较强的用户对照测试支持，但尚未定位到具体域名、HTTP 错误或运营商链路。客户端恢复能力的缺口已经通过源码确认，部分行为经本地故障模拟验证。不能据此认定 Bilibili 刻意限制海外会员，也不能断言所有 1080P+ 下载都存在问题。

建议先实现有上限的自动续传、备用地址切换和错误分类，再改进探测与地址刷新。AV1 播放卡顿应单独收集设备和媒体信息。

## Issue 中的有效证据

| 证据 | 支持的判断 | 限制 |
| --- | --- | --- |
| 已登录、有会员，1080P+ 下载慢且经常需要手动重试 | 重点排查传输阶段和恢复策略 | 未提供应用版本、视频、HTTP 状态或异常日志 |
| 反馈者说明自己位于马来西亚 | 跨境访问路径值得重点排查 | 地理位置本身不能确定故障原因 |
| 中国线路 VPN 下，批量下载 32 个短视频全部成功且速度明显改善 | 更换出口或路径可改善问题 | 未提供严格的同一批视频、相同时段对照数据；不足以区分 DNS、路由、CDN 调度或 IP 策略 |
| 其他下载器同样失败；其中 YTDLnis 会失败后继续下载 | 网络因素较可信；自动恢复能改善使用体验 | 这是用户观察，本次未复测其他应用 |
| “普通 1080P 可能正常” | 可作为后续对照测试 | 用户明确表达的是猜测，不是已验证结果 |
| AV1 在内置播放器流畅、在其他播放器不稳定 | 解码和播放器差异需要独立分析 | 没有机型、编码参数、解码器名称或样本文件 |

来源：[Issue 正文](https://github.com/KafuuNeko/BiliDownload/issues/71)、[马来西亚说明](https://github.com/KafuuNeko/BiliDownload/issues/71#issuecomment-5855795240)、[32 个视频及其他下载器的测试反馈](https://github.com/KafuuNeko/BiliDownload/issues/71#issuecomment-5856740159)。维护者已[表示将研究自动重试和 CDN 选择](https://github.com/KafuuNeko/BiliDownload/issues/71#issuecomment-5857437964)。

## 当前下载链路

```text
下载任务入队（最多 3 个任务同时准备或下载）
  → 重新请求播放流
  → 按 dashId + codecId 匹配用户选择的资源
  → 每个资源只选出一个 URL
  → 音视频并行下载，写入任务独立的 .part 文件
  → 任一资源异常：整个下载组失败
  → Service 写入 DOWNLOAD_FAILED，结束本次处理
  → 用户手动重试：重新获取播放流，从已有文件恢复
```

关键实现位于 [DownloadManager](https://github.com/KafuuNeko/BiliDownload/blob/3e0406ac8edb8ebcafd1caa78039d23b40358955/app/src/main/java/cc/kafuu/bilidownload/common/manager/DownloadManager.kt#L280) 和 [DownloadService](https://github.com/KafuuNeko/BiliDownload/blob/3e0406ac8edb8ebcafd1caa78039d23b40358955/app/src/main/java/cc/kafuu/bilidownload/service/DownloadService.kt#L268)。本地对比发布标签 `2.3.7.foss` 与当前 HEAD，`DownloadManager.kt` 没有差异；但反馈者实际安装的版本仍未知。

### 已确认：没有应用层传输重试

`downloadSingleResource()` 第 483 行虽然使用 `repeat(2)`，但循环体只有 `try/finally`，没有捕获网络异常后继续的分支。第二次循环仅用于第 491 行的特殊情形：续传遇到 HTTP 416，删除缓存后重新请求。

因此，连接或读取异常、响应提前结束、普通非成功 HTTP 响应都会直接抛到下载组；`runDownloadGroup()` 随即发布失败状态。Service 没有再调度自动重试。

现有 `.part` 机制是可复用的基础：用户手动重试时读取缓存长度、发送 Range；已完成的子资源会跳过。应在资源下载层补齐自动恢复，避免每次瞬断都走终态和手动操作。

### 已确认：备用地址没有用于传输失败回退

接口模型已经保存 `baseUrl + backupUrl`，但默认配置是 `DEFAULT`：优先返回 `baseUrl`，只有主地址字段缺失才取第一个备用地址。主地址存在但不可访问时，不会自动尝试备用地址。

`AUTO_PROBE` 会先探测候选，但最终仍只返回一个 URL；`ResourceRequest` 也只保留一个 `url`。下载中途失败时没有剩余候选可供切换。自定义 Host 只在开始前探测失败时退回默认地址，传输中断后同样没有回退。

代码：[默认配置](https://github.com/KafuuNeko/BiliDownload/blob/3e0406ac8edb8ebcafd1caa78039d23b40358955/app/src/main/java/cc/kafuu/bilidownload/common/model/AppModel.kt#L17)、[URL 集合及默认选择](https://github.com/KafuuNeko/BiliDownload/blob/3e0406ac8edb8ebcafd1caa78039d23b40358955/app/src/main/java/cc/kafuu/bilidownload/common/network/model/BiliPlayStreamData.kt#L63)、[下载源策略](https://github.com/KafuuNeko/BiliDownload/blob/3e0406ac8edb8ebcafd1caa78039d23b40358955/app/src/main/java/cc/kafuu/bilidownload/common/manager/DownloadManager.kt#L300)。

### 已确认：探测只测首字节，并存在误判

`probeStreamUrl()` 使用 `Range: bytes=0-0`，单次总超时 3 秒，然后按耗时选地址。这主要反映连接、响应头和首字节延迟，无法代表持续下载速度或长连接稳定性。

第 378 行看到 200/206 就将 `isAvailable` 设为 `true`，随后才读取正文。读取抛异常时，catch 没有把它恢复为 `false`；读取返回 `-1` 也没有校验。因此无正文或正文异常的地址仍可能参与“可用地址”排序。

来源：[探测实现](https://github.com/KafuuNeko/BiliDownload/blob/3e0406ac8edb8ebcafd1caa78039d23b40358955/app/src/main/java/cc/kafuu/bilidownload/common/manager/DownloadManager.kt#L369)。这一缺陷只有进入探测模式时才相关；Issue 没有说明反馈者使用了哪种下载源设置。

### 已确认：使用默认超时；是否触发本次故障尚不明确

依赖解析确认运行时使用 OkHttp `3.14.9`，由 Retrofit `2.11.0` 引入。[NetworkManager](https://github.com/KafuuNeko/BiliDownload/blob/3e0406ac8edb8ebcafd1caa78039d23b40358955/app/src/main/java/cc/kafuu/bilidownload/common/network/manager/NetworkManager.kt#L41) 没有覆盖超时参数：连接和读取默认各 10 秒，完整调用默认不限时。

读取超时限制的是读取等待，不是视频总下载时长。仅仅速度慢不必然超时；发生较长停顿时才可能触发。OkHttp 默认开启的 `retryOnConnectionFailure` 处理部分底层连接恢复，不能替代应用在已经消费响应体后重新请求 Range 的续传逻辑。

来源：[OkHttpClient 3.14.9 源码](https://github.com/square/okhttp/blob/parent-3.14.9/okhttp/src/main/java/okhttp3/OkHttpClient.java)、[内部重试实现](https://github.com/square/okhttp/blob/parent-3.14.9/okhttp/src/main/java/okhttp3/internal/http/RetryAndFollowUpInterceptor.java)。

### 已确认：传输错误没有完整传到用户侧

传输异常仅写入 Logcat；下载状态事件传递任务、快照和状态，没有结构化错误原因。`onDownloadFailed()` 统一显示下载失败。播放流请求失败另有携带 HTTP/业务码的事件，但媒体传输阶段没有同等诊断信息。

因此现有反馈无法区分读取超时、403、连接重置、响应不完整与本地文件写入失败。建议补充阶段、异常分类、HTTP 状态、CDN 主机、续传偏移和尝试次数。

诊断必须脱敏：现有 `BiliInterceptor` 会打印 Cookie 和请求信息，不能直接要求用户公开完整原始 Logcat。采集时应排除 Cookie、完整鉴权 URL 和敏感查询参数。

## 本地验证及边界

已执行：

- Gradle `:app:dependencyInsight --dependency com.squareup.okhttp3:okhttp --configuration debugRuntimeClasspath --offline`，成功确认 OkHttp `3.14.9`。
- 使用同版本 OkHttp 和本地回环 HTTP 服务运行隔离 Java 实验，等价复现当前重试与探测分支的控制流；没有使用账号、Cookie 或外部 CDN。

| 模拟输入 | 实际结果 | 验证点 |
| --- | --- | --- |
| HTTP 200，声明 16 字节，发送 4 字节后断开 | `ProtocolException`；尝试次数 1，服务端请求次数 1，保留 4 字节 | 响应体中断后，底层默认重试和当前循环均未重新发起下载 |
| HTTP 206，声明 1 字节，正文未发送就断开 | 捕获 `ProtocolException`，但可用标记为 `true` | 正文读取异常后探测误判 |
| HTTP 200，正文长度 0 | 可用标记为 `true` | 未校验首字节读取结果 |

这是针对控制流及 HTTP 库行为的最小实验，不是直接运行 Android `DownloadManager`，也不是对用户马来西亚网络环境的复现。没有进行真机下载、会员视频测试、CDN 吞吐排名或播放器故障复现。当前证据不足以推荐某个固定海外 Host 为稳定最优源。

## 建议实现范围与顺序

### 第一阶段：减少必须手动重试的情况

- 在单资源下载层实现有限次数自动重试，保留原 `.part` 文件；每次重试重新读取实际落盘长度。优先处理超时、连接重置、响应提前结束等可恢复传输错误。资源级重试耗尽前不将整个组发布为失败。
- 将 `ResourceRequest` 从单个 URL 改为同一资源的有序候选集合，保留 API 返回的主地址与备用地址。默认模式也应支持失败回退；自动探测只影响优先顺序，不丢弃未选中的候选。
- 增加可取消的指数退避和总预算。可先以每个资源最多 5 次额外尝试、约 1/2/4/8/16 秒加抖动作为待验证初值；同源重试、切源、刷新地址共享预算，避免重试次数相乘。
- 429 遵循服务端等待提示，部分 5xx 允许重试；本地磁盘空间不足、权限错误和用户取消不能当作网络重试。403/404 需结合媒体 URL 及 API 结果分类，不能直接解释成会员或地区限制。
- 同步修复探测误判，只有成功读取有效正文后才标记可用。
- 将错误分类及重试状态传给通知和页面，例如“网络中断，正在重试（2/5）”；最终失败保留可诊断原因及手动恢复入口。

现有最大 3 个任务、音视频并行的策略先保持。需要通过弱网实验才能判断是否降低并发；盲目增加并发可能加剧不稳定。

### 第二阶段：改善地址选择与长时间下载

- 下载专用客户端可通过 `newBuilder()` 继承现有配置并单独调整超时。连接 15–20 秒、读取 30 秒可作为待测试初值，不改变所有 API 请求的等待行为，也不设置限制整个长视频时长的短总超时。
- 在候选地址耗尽或有过期迹象时，有上限地重新请求播放流，按原 `dashId + codecId` 匹配，继续保留用户清晰度和编码。刷新失败或原资源不可用时明确报告，不静默降为普通 1080P。
- 探测可由一个字节改为有读取上限、时间上限的小样本，结合实际下载吞吐和近期失败记录排序。小样本仍不能保证持续速度；优先保留失败切换能力。
- 网络切换时使旧的源评分失效。固定 Host 替换仅保留为用户可选策略，不能假定任意主机都接受相同路径和签名。

### 续传与生命周期必须同步验证

- 206 追加前核对 `Content-Range` 起点、总长度及资源一致性；同一清晰度/编码不自动保证跨源或刷新后的字节内容完全相同。无法确认一致时安全重下，不能拼接不兼容文件。
- 200 返回全量时覆盖旧缓存，416 对缓存失效进行有界恢复；不能遇到 416 就直接视为完成。未知长度及提前 EOF 需独立验证。
- 暂停或取消必须打断网络调用、退避等待和地址刷新，迟到回调不得重新拉起任务。
- 一个资源重试不能重复创建任务或重复入队；已完成的音视频资源继续复用。用完预算才发布一次终态，并保证服务登记、数据库与组状态一致。
- 合成失败、公共目录发布失败沿原检查点恢复，不应因新增网络重试而重新下载。

建议覆盖：中途断流后 Range 续传、主源失败备用成功、全部源失败、403 后有界刷新、429 退避、200/206/416、错误 Content-Range、空探测正文、无 Content-Length、退避中暂停、刷新中取消、音视频一成一败，以及批量队列继续推进。

## AV1 播放问题的独立判断

内置播放器使用 Media3 ExoPlayer；默认解码能力取决于 Android 平台及具体设备。能播放某个低码率 4K 视频，不能据此推断一定能流畅播放另一编码、位深、帧率或码率的 1080P 视频。播放器之间也可能选择不同解码路径。[Media3 官方格式说明](https://developer.android.com/media/media3/exoplayer/supported-formats#sample-formats)

当前音视频合成使用 `-c:v copy -c:a copy`，保留原编码，不会把 AV1 变为 AVC。建议收集同一文件的设备型号、Android 版本、播放器版本、codec/profile、位深、帧率、码率及实际解码器信息，再判断是解码能力、播放器配置、封装还是文件完整性问题。

来源：[内置播放器](https://github.com/KafuuNeko/BiliDownload/blob/3e0406ac8edb8ebcafd1caa78039d23b40358955/app/src/main/java/cc/kafuu/bilidownload/feature/compose/viewmodel/mediaplayer/MediaPlayerViewModel.kt#L77)、[合成实现](https://github.com/KafuuNeko/BiliDownload/blob/3e0406ac8edb8ebcafd1caa78039d23b40358955/app/src/main/java/cc/kafuu/bilidownload/common/utils/FFMpegUtils.kt#L86)。下载重试改进不应承诺解决 AV1 播放卡顿。

## 仍需补充的现场信息

- 应用版本和来源、下载源模式、设备与 Android 版本。
- 失败视频的 BV/CID、清晰度、编码，以及失败发生在解析、传输、合成还是发布阶段。
- 脱敏后的 HTTP 状态或异常分类、CDN 主机、已下载字节、失败时间点。
- 在同一批视频上比较直连与中国线路、默认源与自动探测；分别记录成功率、手动重试次数、耗时。

现有“设置 → 下载源 → 自动探测”可以作为无需改版的对照项目，但其当前实现没有中途故障回退，并存在上述探测误判，不能把这个设置视为已解决 Issue。
