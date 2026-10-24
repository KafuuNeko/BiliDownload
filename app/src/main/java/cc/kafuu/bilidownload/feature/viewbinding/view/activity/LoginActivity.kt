package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.databinding.ActivityLoginBinding
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.LoginViewModel


/**
 * 承载 WebView 登录，导航回移动站点时将 Cookie 交给账号管理器校验。
 *
 * 页面只观察账号模型决定何时结束，不把拿到 Cookie 或网页加载完成当作登录成功。
 */
class LoginActivity : CoreActivity<ActivityLoginBinding, LoginViewModel>(
    LoginViewModel::class.java,
    R.layout.activity_login,
    BR.viewModel
) {

    /** 先清理 WebView 的旧 Cookie，再加载登录页面；账号资料发布后结束当前宿主。 */
    override fun initViews() {
        setImmersionStatusBar()
        CookieManager.getInstance().removeAllCookies {
            mViewDataBinding.wvWeb.initWeb()
        }
        AccountManager.accountLiveData.observe(this) {
            if (it != null) mViewModel.finishActivity()
        }
    }

    /** 为站点登录启用必要的 JavaScript，并安装导航交接与进度回调。 */
    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.initWeb() {
        webViewClient = webViewClient()

        webChromeClient = webChromeClient()

        // 让WebView能够执行javaScript
        settings.javaScriptEnabled = true
        // 让JavaScript可以自动打开windows
        settings.javaScriptCanOpenWindowsAutomatically = true

        loadUrl(NetworkConfig.LOGIN_URL)
    }

    /** 接管移动站点的登录跳转，其余地址由 WebView 正常处理；导航过程不记录 Cookie。 */
    private fun webViewClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest?
        ) = CookieManager.getInstance().getCookie(NetworkConfig.BILI_MAIN_URL).let { cookies ->
            val requestUrl = request?.url?.host ?: return false
            if (requestUrl == "m.bilibili.com") {
                // 只把凭据传给账号校验入口；完整 Cookie 不得进入导航调试日志。
                AccountManager.updateCookie(cookies)
                true
            } else false
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            mViewDataBinding.pbProgress.visibility = ProgressBar.VISIBLE
        }
    }

    private fun webChromeClient() = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            mViewDataBinding.pbProgress.apply {
                progress = newProgress
                if (newProgress == 100) {
                    visibility = ProgressBar.GONE
                }
            }
        }
    }
}
