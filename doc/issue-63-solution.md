# Issue #63：视频列表统计信息调研

调研日期：2026-09-22。代码基线：`fac6823`。本次仅调研并新增本文，未修改应用代码。

## 结论

建议采用「优先显示列表接口自带统计，按可见条目补齐缺失字段」的方案。统一展示播放量、点赞数、收藏数，窄屏优先保留播放量和点赞数。无需为每个页面单独实现卡片，也不需要改动下载数据库。

搜索接口实测已经包含三项统计，因此搜索结果可以做到零额外请求；收藏夹缺少点赞数，观看历史和 UP 主投稿需要按各自接口能力补齐。仅展示现有字段是可独立交付的第一步，但不能视为完整解决 Issue。

来源：[Issue #63](https://github.com/KafuuNeko/BiliDownload/issues/63)。Issue 当前为 Open，维护者已[回复将在下一版本更新](https://github.com/KafuuNeko/BiliDownload/issues/63#issuecomment-5357361096)。

## 代码现状

数据路径为网络 DTO → `BiliVideoModel.create(...)` → `BiliResourceRVAdapter` → `ItemBiliVideoHolder` → `item_bili_video.xml`。

- [BiliVideoModel](../app/src/main/java/cc/kafuu/bilidownload/common/model/bili/BiliVideoModel.kt) 有七种来源的转换入口，但没有统计属性，详情接口已经解析的 `stat` 也没有传入卡片模型。
- [BiliSearchData](../app/src/main/java/cc/kafuu/bilidownload/common/network/model/BiliSearchData.kt) 已声明搜索结果的 `play`、`favorites`、`video_review`、`review`，尚未声明实测存在的 `like`。
- [BiliFavoriteData](../app/src/main/java/cc/kafuu/bilidownload/common/network/model/BiliFavoriteData.kt) 未声明 `cnt_info`；[BiliLikeData](../app/src/main/java/cc/kafuu/bilidownload/common/network/model/BiliLikeData.kt) 未声明实测存在的 `stat`。
- [BiliVideoStat](../app/src/main/java/cc/kafuu/bilidownload/common/network/model/BiliVideoData.kt) 已有完整统计字段，当前类型是非空 `Int`。
- 普通视频列表共用 [item_bili_video.xml](../app/src/main/res/views/item/layout/item_bili_video.xml)，番剧/影视使用另一类模型和布局，需要区分稿件与剧集统计口径。
- [BiliResourceRVViewModel](../app/src/main/java/cc/kafuu/bilidownload/feature/viewbinding/viewmodel/common/BiliResourceRVViewModel.kt) 用 `Set<BiliResourceModel>` 管理多选；现有视频模型没有重写相等性，选中判断依赖对象身份。

## 接口能力与实测证据

以下直接请求 Bilibili 第一方接口，使用普通浏览器 UA 和 Referer，没有使用登录凭据。成功样本只证明当前样本的能力，不代表所有视频、账号和网络都一致。

| 来源 | 可用字段或当前证据 | 实现判断 |
| --- | --- | --- |
| 全站视频搜索 `/x/web-interface/wbi/search/type` | 实测 `play`、`like`、`favorites`、`video_review`、`review` 均存在 | 增加可空 `like` 并映射已有字段，正常样本无需补充请求 |
| 收藏夹内容 `/x/v3/fav/resource/list` | 实测 `cnt_info.play`、`collect`、`danmaku`；未返回 `like` | 先显示播放、收藏，再补点赞 |
| UP 主投稿 `/x/space/wbi/arc/search` | 当前 DTO 有 `play`、`video_review`、`comment`；本次带 WBI 签名请求仍返回 `-352` | 已有字段可接入；点赞/收藏能力需登录环境复核，先按缺失可补齐设计 |
| 观看历史 `/x/web-interface/history/cursor` | 当前 DTO 未建模统计；匿名请求返回 `-101` | 不能宣称已验证实际响应；计划按 BV 号补齐，并在登录环境核对原始响应 |
| 最近点赞 `/x/space/like/video` | 实测完整 `stat.view/like/favorite` 等 | 增加可空 `stat`，通常无需额外请求 |
| 稍后再看 `/x/v2/history/toview` | 当前 DTO 未声明 `stat`；匿名请求返回 `-101` | 登录环境核对是否自带 `stat`，有则直接使用，缺失时走统一补齐 |
| BV/AV 直接检索、视频详情 `/x/web-interface/view` | 实测完整 `stat`，项目已经接入 | 直接映射，并作为缺失统计的补齐来源 |

代表样本：

1. [搜索 Mili](https://api.bilibili.com/x/web-interface/wbi/search/type?search_type=video&keyword=Mili&page=1)：`BV1jT3X6BEWG` 返回 `play=1052305`、`like=76249`、`favorites=41222`。因此「搜索接口不返回点赞数」不符合本次实测。
2. [公开收藏夹](https://api.bilibili.com/x/v3/fav/resource/list?media_id=82223950&pn=1&ps=2)：`BV1Zs411k78r` 返回 `cnt_info.play=508859`、`collect=18123`、`danmaku=871`，没有点赞字段。对应[详情响应](https://api.bilibili.com/x/web-interface/view?bvid=BV1Zs411k78r)的播放、收藏、弹幕值一致，点赞为 `13100`。
3. 收藏夹同一条目的 `cnt_info.reply=0`，但详情 `stat.reply=1007`；第二条也存在类似差异。因此不直接把收藏夹 `reply` 用作评论总数。可选评论数优先使用详情 `stat.reply`，本期可不展示。
4. [公开最近点赞列表](https://api.bilibili.com/x/space/like/video?vmid=23340150)：`BV1AY411S7kh` 自带 `stat.view=479903`、`like=19478`、`favorite=9048`。
5. 候选精简接口 [archive/stat](https://api.bilibili.com/x/web-interface/archive/stat?bvid=BV1xx411c7mD) 本次返回 HTTP 404，同 BV 的 [view](https://api.bilibili.com/x/web-interface/view?bvid=BV1xx411c7mD) 返回 `code=0` 和完整统计。不能把前者作为未经验证的默认实现，也不能仅凭一次 404 断言其永久下线。

生产调用继续复用项目已有 WBI、请求头、Cookie 和错误处理；本次匿名搜索成功不构成删除签名的理由。没有验证可用的批量统计接口，因此方案不依赖批量接口存在。

## 推荐实现

### 1. 统一统计模型与来源映射

新增 `VideoStats`，包含 `view/like/favorite/danmaku/reply: Long?`，默认 `null`。`0` 表示已知为零，`null` 表示未知或未提供；负数等无效计数归一化为未知。新增与相关现有 DTO 统计字段使用可空 `Long`，避免字段缺失被反序列化为零，以及 `Int` 的范围限制。

`BiliVideoModel` 增加初始统计快照，所有 `create(...)` 入口负责各自映射：搜索的 `favorites` → `favorite`，收藏的 `collect` → `favorite`，详情和最近点赞的 `stat` 直接转换。历史的 `is_fav` 是状态，不能映射成收藏数量；UP 主投稿的 `review` 也不能直接当评论数，现有模型明确另有 `comment`。

视频模型继承 `Serializable`，用于 Activity 参数传递。若把统计快照放进视频模型，快照也必须可序列化，不能把协程、Flow、View 或仓库实例塞入其中。

### 2. 可见条目补齐与缓存

在网络仓库层增加统计读取入口，复用 `BiliApiService.requestVideoDetail(bvid=...)`，仅向上层输出统计。列表首次响应立即展示，补齐任务不阻塞列表加载完成。

建议初始策略（属于实现参数建议，需实机调优）：

- 只补当前可见、且缺少要展示字段的普通视频；滚动稳定约 200 ms 后再调度，避免快速滑动对每条视频发请求。
- 按 BV 号维护共享的有界内存缓存，例如最多 300 条、TTL 5 分钟；同一 BV 的并发请求合并。列表本身已带完整统计时不触发补齐。
- 补齐队列最多 2 个并发，并增加发送间隔限制；仅限制并发不能保证低请求频率。移出可见范围的未执行任务移出队列。
- 暂时失败使用短暂失败缓存；收到 `-352`、`-412` 或 HTTP 429 等风控/限流信号时暂停补齐并冷却，不逐卡片弹 Toast 或立即循环重试。
- 离开页面或关闭显示时取消对应订阅、清理无用队列；无其他订阅者时取消网络请求。旧请求结果可以更新有效缓存，但不能覆盖已经切换的页面/账号状态。
- 合并时只用有效值覆盖字段，不能用缺失字段清空已知统计。保留获取时间与必要的来源信息，避免旧回调覆盖较新的快照。

这里的「补齐」仍然是每个缓存未命中的视频一次详情请求，不是一次批量请求。若一页 20 条全部需要补齐且用户全部浏览，最坏仍可能新增 20 次请求；可见范围调度主要降低首屏、快速滚动和重复进入时的请求量。

现有异步 `BiliVideoRepository.requestVideoDetail(...)` 不暴露可取消句柄。新增入口应保留 Retrofit `Call` 或使用支持取消的挂起封装，不能只取消协程却让底层请求持续执行。

### 3. 局部更新与多选兼容

优先在公共 `BiliResourceRVViewModel` 中维护以 BV 号索引的统计展示状态，视频模型保留初始快照。更新通过 Adapter payload 或专门绑定方法只刷新统计行，不要每完成一条就调用当前会触发 `notifyDataSetChanged()` 的 `updateList()`。

补齐过程中保持原视频对象身份，以免破坏现有多选 Set；同一 BV 出现多次时同步刷新所有对应可见行。Holder 复用时检查当前绑定 BV，清除旧展示状态。网络请求由 ViewModel/仓库调度，Holder 仅报告可见性和完成绑定。

### 4. 卡片布局

统一卡片显示示例：`播放 12.3万  点赞 5600  收藏 2300`，实际用项目风格的矢量图标和文本资源实现。

现有封面为 80×80dp，右侧已有标题、时长/简介、日期/作者三行。建议把时长移到封面角标，用原「时长/简介」行放统计，保留标题与作者/日期；关闭统计或整行无数据时恢复简介。这样不需要直接向固定紧凑卡片里塞第四行。

- 宽度不足时优先隐藏收藏，保留播放和点赞；缺失某项时隐藏该项，不伪造 `0`。全部未知时隐藏整行，补齐失败仍可正常点击与下载。
- `0` 正常显示；小于一万显示整数，万/亿单位最多一位小数并去尾零，确定边界进位规则。
- 使用资源文案和无障碍描述，不依赖彩色 Emoji 表达意义；验证深色模式、窄屏、长标题、字体放大以及多选按钮占用宽度。
- 首版默认开启即可；可选开关后续接入现有 `AppModel` / 设置 MVI 链路。若加入开关，关闭时同时停止额外补齐请求。

## 方案取舍与交付顺序

| 方案 | 优点 | 不足 | 建议 |
| --- | --- | --- | --- |
| 仅展示列表已有数据 | 改动小，无额外请求；搜索与最近点赞可完整展示 | 历史等入口不能完整覆盖 | 作为第一阶段 |
| 每页所有视频先请求详情再展示 | 显示字段统一 | N+1 请求、首屏等待、风控压力 | 不采用 |
| 列表字段优先 + 可见条目异步补齐 | 覆盖需求，首屏不等待，可共享缓存 | 增加生命周期、调度与局部刷新处理 | 推荐完整方案 |

第一阶段完成 DTO、模型映射、统一卡片和数值格式化，立即改善搜索、收藏和最近点赞；第二阶段接入补齐、缓存、局部刷新，覆盖观看历史及投稿缺项。登录态样本核对属于完整交付的前置验证，尤其要确认稍后再看的 `stat` 及投稿实际响应。

不建议在本 Issue 中顺带重写整个 RecyclerView 框架、迁移 Compose 或修改 Room。下载历史是独立的本地下载记录列表，本次按 Issue 中的观看历史理解；番剧/影视也不直接套用普通稿件统计。

## 验收重点

1. DTO 及来源映射：完整、缺失、显式零、负数、超过 Int 范围的值；收藏 `collect` 正确映射，`is_fav` 等状态不当数量。
2. 数字格式：9999、10000、接近一亿、一亿及以上、尾零和本地化行为。
3. 请求行为：完整搜索结果零补齐；同一 BV 只产生一次在途请求；过期、失败冷却、关闭显示和页面退出符合预期；不阻塞列表。
4. 交互回归：补齐过程中多选不丢失，Holder 快速复用不串统计，分页/刷新后旧回调不污染新状态，Activity 参数可序列化。
5. 实机覆盖搜索、收藏夹、观看历史、UP 主投稿、最近点赞、稍后再看及 BV/AV 直接搜索；校验窄屏、大字体、浅深色、失效视频和网络失败。

本次已完成代码走查及匿名公开接口抽样，没有修改或编译应用，也没有登录态接口或实机 UI 验证。
