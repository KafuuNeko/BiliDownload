package cc.kafuu.bilidownload.view.activity

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.databinding.ActivityLoginBinding
import cc.kafuu.bilidownload.viewmodel.activity.LoginViewModel


class LoginActivity : CoreActivity<ActivityLoginBinding, LoginViewModel>(
    LoginViewModel::class.java,
    R.layout.activity_login,
    BR.viewModel
) {

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun initViews() {
        setImmersionStatusBar()
        CookieManager.getInstance().removeAllCookies {
            mViewDataBinding.wvWeb.initWeb()
        }
        AccountManager.accountLiveData.observe(this) {
            if (it != null) mViewModel.finishActivity()
        }
    }

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

    private fun webViewClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest?
        ) = CookieManager.getInstance().getCookie("https://bilibili.com").let { cookies ->
            val requestUrl = request?.url?.host ?: return false
            Log.d(TAG, "shouldOverrideUrlLoading: request: ${requestUrl}, $cookies")
            if (requestUrl == "m.bilibili.com") {
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