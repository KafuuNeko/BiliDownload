# 依赖组织与 Kotpref

本专题用于新增依赖、共享能力和简单偏好，定义初始化顺序、对象生命周期与配置契约。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 初始化与共享能力

当前应用启动顺序由 `BiliDownload.onCreate()` 定义：初始化 Kotpref，再初始化 `CommonLibs`，随后恢复账号 Cookie 状态。

| 入口 | 职责 |
| --- | --- |
| CommonLibs | 保存应用级 Context，提供数据库和公共基础能力 |
| NetworkManager | 组织 Retrofit 服务、OkHttpClient 与网络仓库 |
| AccountManager | 账号状态、Cookie 更新与专门缓存边界 |
| AppModel | 小体量的全局偏好 |
| UseCase 构造参数 | 显式提供可替换的查询、解析、设置读取或执行能力 |

- 按生命周期复用已有对象，不在页面中反复创建数据库、网络客户端或下载调度器。
- 全局能力只持有 Application Context 或不依赖页面的对象，不能缓存 Activity、Binding、Launcher 或页面回调。
- 新增仓库或用例优先显式构造参数，不为了注册少量对象而引入另一套全局依赖框架。
- ViewModel 继续使用已有创建方式；新增非默认构造参数时提供对应工厂与宿主接入，不能只修改构造函数。

## 2. 可测试的用例依赖

`BatchDownloadUseCase` 已通过构造参数接收解析、加载 DASH、读取策略和入队函数；`BatchExportUseCase` 接收 Context 提供者、资源查询与模板读取函数。

新增业务可采用相同方式：

- 按业务能力拆参数，传入值或有清晰契约的函数，不把整个 Activity 交给用例。
- 默认实现复用已有仓库和管理者；测试替换依赖，验证真实业务结果。
- 网络与 Room 中的同名仓库使用明确包名或 import 别名。
- 只有跨调用共享状态时才采用长生命周期对象；页面专用协作者由页面持有并释放。

## 3. Kotpref 的适用范围

`AppModel` 保存下载目录模式、自定义源、清晰度不匹配策略、播放开关和命名模板等小体量设置。任务记录、资源清单、大块 JSON 以及可恢复的业务主体数据进入 Room 或对应存储。

配置由 ViewModel、用例或管理者读写，UI 渲染从状态中取得设置值。账号凭据维持 `AccountManager` 的专门边界，不加入通用偏好。

## 4. 稳定 key 与 code

当前目录策略将持久化数值和业务枚举分开：

```kotlin
private var downloadPathModeCode by intPref(
    default = DownloadPathMode.INTERNAL.code,
    key = "downloadPathMode",
)
```

- 持久化字段使用稳定 key；未显式指定 key 的属性重命名可能改变存储键，修改前必须检查。
- 枚举保存稳定 `code`，不能改用 `ordinal`。新增值不能重排已有编码。
- 定义未知值处理。例如 `DownloadPathMode.fromCode()` 回退到内部存储，避免异常配置触发意外公共发布。
- 新默认值与已有用户配置是两回事，不能用重设默认值代替迁移或强制覆盖。

## 5. 新增配置流程

- 明确默认值、合法输入、持久化方式与未知值行为。
- 明确何时生效：仅影响后续操作，或按业务要求影响正在运行的任务。
- 需要系统授权时，先走权限流程；拒绝或取消后保持正确的已生效值与页面状态。
- 同步 UiState、UiIntent、ViewModel、布局和文案，避免页面显示与真实策略脱节。
- 为纯策略、code 解析和输入校验选择单元测试；系统权限与真实存储行为用设备验证。

## 6. 构建依赖

版本集中在 `gradle/libs.versions.toml`，使用已有别名。新依赖核对必要性、许可、包体积、最低系统版本及原生 ABI；本地 FFmpeg AAR 的替换同时阅读[媒体与原生代码](./media-and-native.md)。

代码入口：[BiliDownload](../../app/src/main/java/cc/kafuu/bilidownload/BiliDownload.kt)、[AppModel](../../app/src/main/java/cc/kafuu/bilidownload/common/model/AppModel.kt)、[BatchDownloadUseCase](../../app/src/main/java/cc/kafuu/bilidownload/common/download/BatchDownloadUseCase.kt)、[DownloadPathMode](../../app/src/main/java/cc/kafuu/bilidownload/common/model/DownloadPathMode.kt)、[版本目录](../../gradle/libs.versions.toml)。
