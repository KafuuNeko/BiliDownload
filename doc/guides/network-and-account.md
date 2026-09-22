# 网络与账号

本专题用于地址解析、接口封装、认证、登录状态和自定义下载源。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 分层与基本规则

- 接口声明放在 `common/network/service/`，响应结构放在 `common/network/model/`，转换与业务错误处理放在 `common/network/repository/`，通过 `NetworkManager` 组织调用。
- 复用已有请求配置、WBI 签名、账号更新和登录失效处理；不在页面中散落 Retrofit、OkHttpClient 或 Cookie 读写逻辑。
- 区分传输失败、HTTP 错误、业务错误码、空数据及解析失败；批量流程中单项失败要计入结果，不能静默伪装成全部成功。
- 用户输入的分享文本、BV/AV/EP/SS 和链接通过 `BiliAddressParser` 等入口处理，新增规则同时覆盖无效输入、分集信息与边界格式。
- 自定义下载源涉及外部输入与本地网络，须校验主机格式并复用权限策略。凭据只发送到明确需要且可信的目标，审查重定向和主机替换后的请求头范围。
- 日志、异常提示、测试数据、注释和文档不得包含真实 Cookie、`SESSDATA`、`bili_jct`、二维码登录凭据或完整鉴权 URL。排查问题优先记录阶段、错误码和必要的脱敏标识；历史日志写法不作为安全范例。

## 2. 接口契约与错误语义

| 结果 | 处理要求 |
| --- | --- |
| IO、连接或超时失败 | 作为传输问题处理，保留重试依据 |
| HTTP 非成功响应 | 保留 HTTP 状态含义，不进入成功数据转换 |
| HTTP 成功但业务 code 失败 | 按业务错误处理，不能当成空列表 |
| data / result 缺失 | 根据接口契约判定为空或解析错误，不默认强制解包 |
| 单项不可用 | 批量用例累计跳过或失败，不中断所有已成功条目 |
| 用户取消或请求替换 | 取消或忽略旧回调，避免显示过期错误 |

现有接口同时存在异步回调和同步执行方式。调用同步网络代码时明确 IO 线程；回调转协程时处理取消、重复和迟到结果，不仅检查 happy path。

修改响应模型时核对 Gson 映射、可空字段、服务端业务码与实际调用方；转换后的业务模型不应携带页面对象。

## 3. 地址与流源

- 输入先按现有解析器识别分享文本、链接或 BV/AV/EP/SS，保留分集信息；无效输入给出明确结果。
- 短链接或重定向后的结果仍需按目标格式解析，不能把用户输入直接当作可下载媒体地址。
- 流 URL 选择复用下载层策略，更新主机时保留必要的路径与参数，核对备用地址、测速和失败回退。
- 自定义主机使用 `LocalNetworkHostUtils` 等能力判断地址与本地网络范围，权限交互由页面宿主完成。
- 检查自定义源与重定向目标是否需要认证信息，避免将账号 Cookie 无条件扩大到任意主机。

## 4. 账号生命周期

账号状态通过 `AccountManager` 更新，网络请求通过现有配置取得所需 Cookie。登录成功、凭据刷新、登录失效及退出均须让后续请求和页面展示使用一致状态。

- 不在页面、UI State、导出文件或通用配置中复制凭据。
- 延迟返回的账号请求不得覆盖已切换或已退出的账号状态；修改认证流程时验证该时序。
- 对外展示错误使用脱敏、可理解的原因，不直接显示响应对象或请求头。
- 调试日志只记录必要的阶段、错误码和标识；新增日志不得照搬历史的完整 Cookie 或请求输出。

## 5. 验证与代码入口

纯地址解析、主机规则与格式转换优先使用确定性的 JVM 用例，覆盖空文本、混合分享文本、带分集参数、非法主机以及 IPv4/IPv6 等现有支持范围。真实接口依赖账号、网络和服务端状态，不能把一次联网成功当成唯一验证。

代码入口：[NetworkManager](../../app/src/main/java/cc/kafuu/bilidownload/common/network/manager/NetworkManager.kt)、[BiliRepository](../../app/src/main/java/cc/kafuu/bilidownload/common/network/repository/BiliRepository.kt)、[WbiManager](../../app/src/main/java/cc/kafuu/bilidownload/common/network/manager/WbiManager.kt)、[AccountManager](../../app/src/main/java/cc/kafuu/bilidownload/common/manager/AccountManager.kt)、[BiliAddressParser](../../app/src/main/java/cc/kafuu/bilidownload/common/utils/BiliAddressParser.kt)、[本地网络权限策略](../../app/src/main/java/cc/kafuu/bilidownload/common/utils/LocalNetworkPermissionPolicy.kt)。

相关专题：[事件与权限](./intent-and-uievent.md)、[下载任务](./download-and-task-lifecycle.md)、[注释与日志边界](./code-comments-and-kdoc.md)。
