package cc.kafuu.bilidownload;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.account.BiliAccount;
import cc.kafuu.bilidownload.model.LoginViewModel;


public class BiliLoginActivity extends BaseActivity {
    public static int RequestCode = 0x00;

    public static int ResultCodeCancel = 0x00;
    public static int ResultCodeOk = 0x01;

    private ProgressBar mProgressBar;

    private LoginViewModel mModel;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bili_login);

        mModel = new ViewModelProvider(this).get(LoginViewModel.class);

        initView();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initView() {
        WebView mWebView = findViewById(R.id.webView);
        mProgressBar = findViewById(R.id.progressBar);

        mWebView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return webViewShouldOverrideUrlLoading();
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                mProgressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                mProgressBar.setVisibility(View.GONE);
            }
        });
        WebSettings webSettings = mWebView.getSettings();
        // 让WebView能够执行javaScript
        webSettings.setJavaScriptEnabled(true);
        // 让JavaScript可以自动打开windows
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        mWebView.loadUrl("https://passport.bilibili.com/login");

        setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }


    public boolean webViewShouldOverrideUrlLoading() {
        Log.d("ViewWeb", "shouldOverrideUrlLoading");
        final String cookie = CookieManager.getInstance().getCookie("https://m.bilibili.com");
        Log.d("ViewWeb cookie", cookie);

        Thread thread = new Thread(() -> {
            Bili.biliAccount = BiliAccount.getAccount(cookie);
            Log.d("ViewWeb", Bili.biliAccount == null ? "不是有效Cookie" : "登录成功: " + Bili.biliAccount.getUserName());
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (Bili.biliAccount != null) {
            setResult(ResultCodeOk);
            Bili.updateHeaders(cookie);
            finish();
        }

        return true;
    }

    public static void actionStartForResult(Fragment fragment) {
        Intent intent = new Intent(fragment.getContext(), BiliLoginActivity.class);
        fragment.startActivityForResult(intent, RequestCode);
    }
}
