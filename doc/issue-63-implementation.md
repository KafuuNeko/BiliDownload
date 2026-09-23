# Issue #63 实现与验证

本次按 [编码规范](./coding-guidelines.md) 实现普通视频卡片的播放量、点赞数和收藏数展示。方案依据见 [调研记录](./issue-63-solution.md)。

## 已实现行为

- 搜索、收藏夹、UP 主投稿、最近点赞、稍后再看及详情结果先映射各自已有统计；观看历史等缺项入口通过统一流程补齐。
- 列表先展示内容，滚动停止 200 ms 后仅补齐可见普通视频的缺项。番剧/影视维持原有展示。
- 同一 BV 的在途请求跨页面共享；最后一个订阅者取消时，取消底层 Retrofit 请求。页面暂停、列表刷新、滚动和视图销毁会释放补齐订阅。
- 补齐最多两个并发，两次发送至少间隔 500 ms；成功缓存 5 分钟，普通失败缓存 30 秒，风控/限流冷却 60 秒，缓存最多 300 条。
- 账号身份变化时失效共享缓存并取消旧请求；代次校验和协程取消防止迟到结果写回。
- 统计用可空 Long 表达，缺失/负数不伪装为零，真实零值正常显示。列表已有字段优先，详情只补缺项。
- 卡片时长移到封面角标，统计使用原简介行；全部未知时保留简介。宽度不足时优先隐藏收藏，保留播放与点赞。
- 中文按万/亿、其他语言按 K/M/B 缩写，向下保留最多一位小数；无障碍描述保留完整计数。
- 使用独立统计快照和局部 payload 刷新，不替换原视频对象，保留多选身份。

本次没有修改数据库、版本号或依赖，也没有增加可选的显示开关或弹幕/评论展示。

## 关键代码

| 职责 | 入口 |
| --- | --- |
| 可序列化统计与来源映射 | [VideoStats](../app/src/main/java/cc/kafuu/bilidownload/common/model/bili/VideoStats.kt)、[BiliVideoModel](../app/src/main/java/cc/kafuu/bilidownload/common/model/bili/BiliVideoModel.kt) |
| 缓存、共享、取消、限流 | [BiliVideoStatsRepository](../app/src/main/java/cc/kafuu/bilidownload/common/network/repository/BiliVideoStatsRepository.kt) |
| 可取消的详情统计请求 | [BiliVideoRepository](../app/src/main/java/cc/kafuu/bilidownload/common/network/repository/BiliVideoRepository.kt) |
| 可见范围调度与页面状态 | [BiliResourceRVFragment](../app/src/main/java/cc/kafuu/bilidownload/feature/viewbinding/view/fragment/common/BiliResourceRVFragment.kt)、[BiliResourceRVViewModel](../app/src/main/java/cc/kafuu/bilidownload/feature/viewbinding/viewmodel/common/BiliResourceRVViewModel.kt) |
| 卡片、局部更新和数值格式 | [布局](../app/src/main/res/views/item/layout/item_bili_video.xml)、[VideoStatsView](../app/src/main/java/cc/kafuu/bilidownload/feature/viewbinding/view/common/VideoStatsView.kt)、[Adapter](../app/src/main/java/cc/kafuu/bilidownload/common/adapter/BiliResourceRVAdapter.kt)、[VideoCountFormatter](../app/src/main/java/cc/kafuu/bilidownload/common/utils/VideoCountFormatter.kt) |

## 验证结果

验证日期：2026-09-22。

| 验证 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest` | 全部 69 项通过，其中新增统计相关 15 项 |
| `:app:assembleDebug` | 通过，生成四 ABI Debug APK |
| `:app:lintDebug` | 通过；修改文件范围内仅有 AccountManager 既有 SharedPreferences KTX 建议 |
| `:app:connectedDebugAndroidTest`，限定 `VideoStatsCardTest` | Pixel_10a 模拟器（Android 17）四项通过 |
| 最终布局修正后的直接 instrumentation 运行 | 四项再次通过 |
| 卡片视觉核验 | 中文、320dp、浅色、深色、2 倍字体；未知统计回退简介，零值和多选状态正确，大字体时长不越出封面 |
| 匿名真实搜索页面 | 统计图标与数字正常显示，长按多选显示已选数量，列表滚动后未观察到崩溃 |

测试入口：

- [VideoStatsTest](../app/src/test/java/cc/kafuu/bilidownload/VideoStatsTest.kt)：接口映射、零/未知/负数、大数、单位边界、序列化及补缺优先级。
- [BiliVideoStatsRepositoryTest](../app/src/test/java/cc/kafuu/bilidownload/BiliVideoStatsRepositoryTest.kt)：共享请求、取消、账号失效、LRU/TTL、失败缓存、风控冷却及并发上限。
- [VideoStatsCardTest](../app/src/androidTest/java/cc/kafuu/bilidownload/VideoStatsCardTest.kt)：Holder 复用、payload 多选身份、窄宽切换和浅深色/大字体渲染。

## 尚未覆盖的环境

没有使用登录凭据，因此未实测登录态观看历史、稍后再看和私有收藏夹的端到端流程。对应 DTO 缺失字段、历史收藏状态与分 P 保留已由确定性测试覆盖；真实服务端字段变化和账号风控仍需在登录环境验证。没有执行真实下载或跨全部 Android 版本的设备测试。
