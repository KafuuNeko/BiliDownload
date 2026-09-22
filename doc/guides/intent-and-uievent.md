# UiIntent、UiEvent 与 ViewAction

本专题定义页面操作、一次性宿主动作、权限和系统选择器的边界。

[返回编码规范主入口](../coding-guidelines.md)

## 1. 三类数据流

| 类型 | 用途 | 本项目入口 |
| --- | --- | --- |
| UiIntent | Compose 页面向 ViewModel 传递用户行为或初始化通知 | `emit(intent)`、`@UiIntentObserver` |
| UiEvent | ViewModel 请求 Compose 宿主执行一次性动作 | `send()`、`collectEvent()` |
| ViewAction | XML 页面体系的宿主动作通道 | `sendViewAction()`、`ViewActionListener` |

可重放的加载、选中项、进度和业务弹窗状态放在 UiState / LiveData。权限请求、系统目录选择器、跳转、分享及一次性提示交给宿主执行，不能用持久 Boolean 反复触发。

## 2. Intent 命名与处理

- 命名描述动作，如 `GoBack`、`SetDownloadPathMode`、`TogglePlayPause`；不要用 `QueryRoom`、`UpdateLoading` 等实现细节命名用户行为。
- 无参数动作使用 `data object`，携带身份或选项时使用 `data class`，参数优先是稳定 ID 和明确值对象。
- 当前分发器按具体运行时类型匹配注解；标注父类型不能代替所有子类型的观察方法。
- 响应方法是无参或单个对应 Intent 参数的普通成员方法，命名为 `on` 加行为名；只由分发器调用时优先私有，已有直接调用入口按实际契约保留可见性。
- 当前分发器不使用挂起反射调用，异步工作在处理方法内启动 `viewModelScope` 协程。方法必须补充 KDoc、状态守卫和取消处理。

实际声明可查看 [SettingsUiIntent](../../app/src/main/java/cc/kafuu/bilidownload/feature/compose/viewmodel/settings/SettingsUiIntent.kt) 与 [MediaPlayerUiIntent](../../app/src/main/java/cc/kafuu/bilidownload/feature/compose/viewmodel/mediaplayer/MediaPlayerUiIntent.kt)。

## 3. UiEvent 的交付与消费

`CoreCompViewModelWithEvent<I, S, E>` 为每次事件创建 `ViewEventWrapper`，宿主通过 `collectEvent` 消费。事件类采用 `<Feature>UiEvent` 命名。

- 先建立收集，再发出可能产生事件的初始化意图；`SettingsActivity` 使用立即开始的协程收集后发送 `Init`。
- 当前 SharedFlow 没有 replay，无收集者时不会为稍后的页面补发事件。
- `send()` 返回成功只表示发送接口接受，不证明宿主动作完成。
- `awaitSend()` 会等待包装事件被消费。没有有效收集者时可能持续挂起，不能将它当成可靠后台任务队列。
- 包装器约束同一个事件的消费，不会合并两个重复发送的事件；重复提交要在业务入口拦截。
- 宿主处理失败与取消时，明确是否重试和怎样回传结果；不能假定一次性事件天然具有跨进程“恰好一次”保证。

跨页面恢复或进程重建后仍需执行的工作，由持久任务和可恢复状态驱动。收集生命周期变化后，重新核对待处理操作，不能单靠旧事件。

## 4. 权限与选择器流程

```text
用户发起操作
  -> ViewModel 校验、保留待处理参数
  -> UiEvent / ViewAction 请求宿主执行
  -> Activity Result 回传授权、URI 或取消结果
  -> ViewModel 再校验当前操作身份
  -> 用例执行并更新页面状态
```

- Launcher 由宿主注册，业务结果回到 ViewModel；不把 Activity 或 Launcher 存在全局对象中。
- 权限拒绝和选择器取消都必须结束对应等待状态，不能留下永久加载或错误的已生效配置。
- 结果可能晚于页面操作切换；先核对待处理参数，避免将授权应用到另一个请求。
- 本地网络操作复用 `LocalNetworkPermissionPolicy` 和 `LocalNetworkPermissionRequestSession`，协调并发、拒绝与撤销授权后的重新请求。
- SAF 授权、URI 使用和导出结果见[存储专题](./storage-and-resource-lifecycle.md)。

## 5. XML 动作通道

XML 页面沿用 `CoreViewModel` 和 `ViewActionListener`。Activity 跳转、提示与对话框复用已有动作类型，新增动作同步增加宿主处理并考虑重复观察。

当前通道基于 LiveData，不具备 Compose 包装事件完全相同的消费语义。页面重建、重新订阅及重复发送时须实际验证动作是否重放；不能机械地将两套基类互换。

代码入口：[Compose 事件基类](../../app/src/main/java/cc/kafuu/bilidownload/common/core/compose/CoreViewModelWithEvent.kt)、[XML ViewModel](../../app/src/main/java/cc/kafuu/bilidownload/common/core/viewbinding/CoreViewModel.kt)、[ViewActionListener](../../app/src/main/java/cc/kafuu/bilidownload/common/core/viewbinding/listener/ViewActionListener.kt)、[SettingsActivity](../../app/src/main/java/cc/kafuu/bilidownload/feature/compose/activity/SettingsActivity.kt)。
