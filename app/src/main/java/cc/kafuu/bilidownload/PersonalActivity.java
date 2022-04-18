package cc.kafuu.bilidownload;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import cc.kafuu.bilidownload.bilibili.BiliFavourite;

public class PersonalActivity extends AppCompatActivity {
    public static int RequestCode = 0x10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal);
    }

    public static void actionStartForResult(Fragment fragment) {
        Intent intent = new Intent(fragment.getContext(), PersonalActivity.class);
        fragment.startActivityForResult(intent, RequestCode);
    }

    public void onClick(View view) {

        BiliFavourite.getFavourite();
    }
}