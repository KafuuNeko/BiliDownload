# 页面实现与生命周期

本专题说明 ViewModel、页面宿主、Compose、XML/DataBinding 与协程如何协作。状态建模和事件契约分别见对应专题。

[返回编码规范主入口](../coding-guidelines.md)

## 1. Compose 基础约定

- 宿主继承 `CoreCompActivity`，页面布局放在 `feature/compose/layout/`，共享组件放在 `feature/compose/views/`。
- ViewModel 使用 `CoreCompViewModel<I, S>`；需要一次性事件时使用 `CoreCompViewModelWithEvent<I, S, E>`。后者定义在 `common/core/compose/CoreViewModelWithEvent.kt`。
- UI 通过 `emit(intent)` 发送操作，ViewModel 通过 `uiStateFlow` 暴露状态，通过状态对象的 `setup()` 发布新状态；避免向外暴露可修改的状态容器。
- `@UiIntentObserver` 按 Intent 的具体运行时类型分发。响应方法使用无参或单个对应 Intent 参数的普通成员方法；异步工作在方法内部启动协程，不直接把 `suspend` 方法交给当前反射分发器。
- Intent 描述用户行为，如选择清晰度、重试发布、确认导出。业务校验和状态守卫在 ViewModel 或用例内完成，不能只靠按钮禁用避免重复操作。
- 跳转、权限请求、系统目录选择器等通过 `<Feature>UiEvent` 交给 Activity；执行结果回传 ViewModel，由其更新业务状态。
- 使用 `collectEvent` 消费事件，避免绕过事件包装自行重复处理。事件流没有 replay，`send()` 成功不等于动作已执行；应先建立收集再发送初始化意图，避免在无收集者时调用依赖消费完成的 `awaitSend()`。
- 需要在页面恢复或进程重建后继续执行的操作，应有可恢复的状态或任务记录，不能只依赖一次性事件。
- Composable 根据传入状态渲染并回传操作。重组函数体内不能启动下载、写偏好或操作数据库；页面局部动画、焦点等短期视图状态可以保存在 Compose 内。

## 2. ViewModel 中的状态更新

处理操作前检查状态，计算结果通过新的不可变状态发布。异步期间先明确工作身份；返回时检查该结果是否仍属于当前页面对象。

以下是设置开关的处理写法示例，使用本项目已有类型和基类 API：

```kotlin
/** 记录后续合成任务的源文件保留策略，并刷新当前设置页面。 */
@UiIntentObserver(SettingsUiIntent.SetDeleteSourceFilesAfterMerge::class)
private fun onSetDeleteSourceFilesAfterMerge(
    intent: SettingsUiIntent.SetDeleteSourceFilesAfterMerge,
) {
    val state = getOrNull<SettingsUiState.Normal>() ?: return
    AppModel.deleteSourceFilesAfterMerge = intent.enabled
    state.copy(deleteSourceFilesAfterMerge = intent.enabled).setup()
}
```

仅保存偏好不代表执行当前任务的删除。需要权限、耗时操作或批量处理时，应按对应流程发送事件或调用用例，不能照搬上述同步处理。

- 业务对话框由 ViewModel 状态控制；用户确认或取消通过 Intent / 回调返回。
- Activity Result 可通过明确的结果方法回传 ViewModel，不为形式统一强迫所有现有回调迁移。
- 异步接口须明确失败、空结果和取消语义，结束等待状态并给出有意义的页面反馈。

## 3. Activity 与 Composable

- Activity 负责创建生命周期内的 ViewModel、收集状态和事件、系统 Intent、权限、窗口及返回行为。
- Layout 入口接收页面状态和事件回调，子组件只接收所需数据；不要让每个子组件直接访问 ViewModel、AppModel 或仓库。
- Compose 副作用须有明确 key 与清理责任，避免重组重复注册监听器、重复启动任务或重复申请权限。
- 主要布局提供适合当前主题包装的浅色、深色 Preview，使用假数据，不能在预览中依赖数据库、网络或真实文件。
- 播放器由现有播放链路的所有者管理，Composable 只绑定所需对象；具体释放规则见[媒体专题](./media-and-native.md)。

## 4. XML/DataBinding

- 复用 `CoreActivity`、`CoreFragment`、`CoreViewModel` 以及相应列表、对话框基类，使用已有 `ViewAction` 通道处理页面动作。
- ViewModel 通过 LiveData 或明确的页面状态对象提供数据，布局表达式保持简单；筛选、排序、资源选择和下载策略放在业务层。
- Fragment 的新增视图观察者绑定 `viewLifecycleOwner`；Binding、监听器和视图引用的释放时机与视图生命周期一致。
- 新增需要宿主执行的动作时，优先传递参数和结果，避免把 UI 对象保存在 ViewModel 字段中。
- Adapter/Holder 负责绑定和交互回调，列表选择优先使用稳定业务 ID，不依赖可能因刷新改变的位置。

新增视图订阅应在对应视图生命周期可用时建立，销毁视图时清理该视图拥有的绑定与监听器。修改公共基类的生命周期处理时同时核对所有继承者，不能只验证一个页面。

## 5. 协程与线程

- 页面业务使用 `viewModelScope`，宿主收集使用对应生命周期作用域，后台任务使用其管理者拥有并负责清理的作用域；不为页面任务新增无主的全局协程。
- 阻塞式网络调用、文件复制、ContentResolver 操作等放在 `Dispatchers.IO`；较重的 PCM、频谱和解析计算放在适当的计算线程，主线程只执行 UI 与要求主线程的框架调用。
- 在调用边界检查真实实现是否阻塞，不能仅凭函数标记为 `suspend` 就认为它已切换线程。
- 捕获异常并转换为业务失败时保留取消语义；通常应重新抛出 `CancellationException`，不能用宽泛的 `catch` 或 `runCatching` 将取消吞成普通结果。
- 回调转协程时处理取消、重复回调与迟到回调；有底层取消接口时同步取消网络或媒体操作。
- Flow、LiveData、EventBus 和播放器监听器均须有明确的注册与释放配对。必须长期观察 LiveData 时，复用已有自动移除能力或显式移除观察者。
- 共享集合使用与访问模型匹配的同步方式。任务“检查、登记、启动”等复合操作不能仅依赖线程安全容器的单次操作保证一致性。
- 用 `use`、`try/finally` 等关闭响应体、流、游标、文件描述符和原生资源；不能把常规清理完全交给 GC。
- 下载进度和频谱等高频数据须控制刷新频率与分配量，避免逐字节更新 UI、重复创建大数组或无界累积数据。

## 6. 生命周期核对表

| 对象 | 所有者与核对点 |
| --- | --- |
| 页面业务 Job | ViewModel；替换请求和清理时取消，防止迟到结果写入新页面对象 |
| UI 观察与 Flow 收集 | Activity 或 Fragment 视图生命周期；重建后避免重复订阅 |
| EventBus 注册 | 实际使用方；注册、注销配对，明确回调线程 |
| Binding 与 View 引用 | 视图；释放不能等同于整个应用结束 |
| 下载任务 | Manager / Service；退出页面不自动终止后台任务 |
| 播放器与媒体会话 | 视频 ViewModel 或音乐 Service；按各自所有权释放 |
| IO 资源 | 打开它的操作；失败和取消路径也执行清理 |

代码入口：[Compose 基类](../../app/src/main/java/cc/kafuu/bilidownload/common/core/compose/)、[XML 基类](../../app/src/main/java/cc/kafuu/bilidownload/common/core/viewbinding/)、[SettingsViewModel](../../app/src/main/java/cc/kafuu/bilidownload/feature/compose/viewmodel/settings/SettingsViewModel.kt)、[SettingsActivity](../../app/src/main/java/cc/kafuu/bilidownload/feature/compose/activity/SettingsActivity.kt)。

验证页面重建、重复初始化、前后台切换、异步取消和资源释放；涉及一次性动作时同时核对[事件专题](./intent-and-uievent.md)。
