package cc.kafuu.bilidownload;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.BiliVideos;
import cc.kafuu.bilidownload.bilibili.VideoParsingCallback;
import cc.kafuu.bilidownload.bilibili.resource.BiliResource;
import cc.kafuu.bilidownload.bilibili.resource.ResourceDownloadCallback;
import cc.kafuu.bilidownload.fragment.DownloadFragment;
import cc.kafuu.bilidownload.fragment.VideoParserFragment;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private Handler mHandler;
    private Toolbar mToolbar;
    private BottomNavigationView mBottomNavigationView;

    private List<Fragment> mFragments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();

        findView();
        initView();
        initFragment();

        showFragment(0);
    }

    private void findView() {
        mToolbar = findViewById(R.id.toolbar);
        mBottomNavigationView = findViewById(R.id.bottomNavigationView);
    }

    private void initView() {
        setSupportActionBar(mToolbar);
        mBottomNavigationView.setOnNavigationItemSelectedListener(this::bottomNavigationItemSelected);
    }

    private void initFragment() {
        mFragments = new ArrayList<>();
        mFragments.add(VideoParserFragment.newInstance());
        mFragments.add(DownloadFragment.newInstance());

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (Fragment fragment : mFragments) {
            transaction.add(R.id.frameLayout, fragment);
        }
        transaction.commitAllowingStateLoss();
    }

    private void showFragment(int index) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (Fragment fragment : mFragments) {
            transaction.hide(fragment);
        }
        transaction.show(mFragments.get(index));
        transaction.commitAllowingStateLoss();
    }

    private boolean bottomNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.navVideoParser) {
            showFragment(0);
        } else {
            showFragment(1);
        }
        return true;
    }
}