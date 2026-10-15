# Issue #70 调查记录

调查日期：2026-09-28。源码基线：`3e0406a`。

## 结论

暂未复现，也未确认根因。当前版本的列表批量下载，在连续提交、取消后重试、连续画质选择三个隔离场景中均正常恢复交互。不能据此排除真实网络、下载服务、页面重建或 Android 14 虚拟机环境中的问题。

本次仅调查，不修改生产代码，不向 GitHub 发布评论。

## 已知信息与版本边界

[Issue #70](https://github.com/KafuuNeko/BiliDownload/issues/70) 的描述仅为“第一次有反应，第二次没反应”，环境是 Android 14 虚拟机、无 root。没有应用版本、入口、视频链接、操作步骤、日志或录屏；当前任务发起人也没有额外复现信息。

调查时最新 GitHub Release 为 `2.3.7.foss`，发布于 2026-09-10。当前源码另外包含列表统计和 #69 详情页全部分 P 下载等改动。已静态对照发布标签：列表批量下载的运行状态复位、选择结果等待，以及 `CoreBasicsDialog` 的实现仍与发布版一致。设备测试针对当前源码，不代表发布 APK 已完成同样验证。

## 调用链核对

- [BiliResourceRVViewModel](../app/src/main/java/cc/kafuu/bilidownload/feature/viewbinding/viewmodel/common/BiliResourceRVViewModel.kt) 的 `onDownloadMultipleSelectItems()` 在批次运行时拒绝再次提交，在 `finally` 中复位运行状态。正常完成会清除多选，首次资源选择取消会保留选择以便重试。
- [BiliResourceRVFragment](../app/src/main/java/cc/kafuu/bilidownload/feature/viewbinding/view/fragment/common/BiliResourceRVFragment.kt) 依据运行状态恢复下载与取消按钮；选择请求使用递增 ID，结果返回同时校验类型和 ID。
- [CoreBasicsDialog](../app/src/main/java/cc/kafuu/bilidownload/common/core/viewbinding/dialog/CoreBasicsDialog.kt) 的 `onCancel()` 会发送取消结果。历史上曾缺少该回调，已在 `52d809c` 修复，并包含于 `2.3.6.foss`、`2.3.7.foss`；不能直接将旧缺陷判定为本 issue 的原因。
- [DownloadRepository](../app/src/main/java/cc/kafuu/bilidownload/common/room/repository/DownloadRepository.kt) 在事务内按 `(bvid, cid)` 查询活动任务并去重。再次选择仍在下载的同一资源会被跳过，不能将“没有新增任务”直接等同于点击失效。
- [BatchDownloadUseCase](../app/src/main/java/cc/kafuu/bilidownload/common/download/BatchDownloadUseCase.kt) 将播放流失败、规格不匹配、重复任务和部分入队失败合并进跳过数量。当前反馈不足以区分这些原因；这是后续定位的可观测性不足，不是已经证实的根因。

## 设备验证

设备：本地 `Pixel_10a` AVD，Android 17 / API 37.1，arm64。验证通过临时 instrumentation 测试完成，使用真实 `BiliResourceRVFragment`、ViewModel、LiveData、子 FragmentManager 和 `BiliPartDialog`，仅通过反射替换批量用例的网络解析、播放流加载与入队函数。

| 用例 | 操作与断言 | 结果 |
| --- | --- | --- |
| `repeatCompletedBatch` | 连续两轮点击下载并确认资源；每轮运行标志复位、待选请求清空、按钮可用；累计四次入队 | 通过 |
| `retryAfterCancel` | 第一轮通过 `Dialog.cancel()` 触发系统取消回调，第二轮重新下载并确认；状态恢复且最终两次入队 | 通过 |
| `consecutiveQualityDialogs` | 设置不匹配时询问，两个分 P 提供不同画质；连续两轮均完成两个资源选择弹窗，最终四次入队 | 通过 |

执行命令：

```bash
bash ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=cc.kafuu.bilidownload.Issue70InvestigationTest \
  -Pandroid.injected.build.abi=arm64-v8a
```

结果：`BUILD SUCCESSFUL`，3 tests，0 failures，0 errors。临时测试已移出源码目录；调查现场的测试源码、Gradle 输出及 JUnit XML 保存在 `/tmp/bilidownload-issue70/`。该路径可能被系统清理，重新执行上述命令前需将测试源码放回 `app/src/androidTest/java/cc/kafuu/bilidownload/`。

未验证范围：真实 B 站响应、实际下载入队及文件下载、账号与清晰度权限、Android 14 虚拟机差异、配置重建、后台切换，以及用户所用发布 APK。

## 下一步定位信息

需要 issue 报告者提供应用版本、操作入口及从首次操作开始的完整步骤或录屏，明确“第二次”指第二个视频还是第二轮批次，同时查看下载列表是否已经出现任务。若能提供视频链接，可进一步区分资源解析、播放流和去重行为。

若在当前版仍能复现，优先记录解析开始/结束、选择请求发出/返回、入队新增/跳过/失败、运行状态复位这些阶段及请求 ID。错误日志需保留错误分类和接口返回码，并去除 Cookie、鉴权 URL 等敏感信息。依据最后一个完成的阶段，再选择网络、弹窗生命周期或下载服务的定向验证。
