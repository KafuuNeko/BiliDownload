package cc.kafuu.bilidownload.feature.viewbinding.view.dialog

import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.viewbinding.dialog.CoreBasicsDialog
import cc.kafuu.bilidownload.databinding.DialogExportOptionsBinding
import java.io.Serializable

/** 导出前选择是否清理源资源，每次打开均默认保留源文件。 */
class ExportOptionsDialog : CoreBasicsDialog<DialogExportOptionsBinding, ExportOptionsDialog.Result>(
    R.layout.dialog_export_options
) {
    data class Result(val deleteSourceAfterExport: Boolean) : Serializable

    override fun initViews() {
        mViewDataBinding.tvBtnLeft.setOnClickListener { dismissWithResult() }
        mViewDataBinding.tvBtnRight.setOnClickListener {
            dismissWithResult(Result(mViewDataBinding.cbDeleteSource.isChecked))
        }
    }
}
