package cc.kafuu.bilidownload;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.Objects;

import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.BiliFavourite;
import cc.kafuu.bilidownload.utils.DialogTools;

public class PersonalActivity extends AppCompatActivity {
    public static int RequestCode = 0x10;

    private CardView mLoginBiliCard;
    private ImageView mUserFace;
    private TextView mUserName;
    private TextView mUserSign;

    private Button mLogout;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal);
        initView();
    }

    public static void actionStartForResult(Fragment fragment) {
        Intent intent = new Intent(fragment.getContext(), PersonalActivity.class);
        fragment.startActivityForResult(intent, RequestCode);
    }

    private void initView() {
        setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mLoginBiliCard = findViewById(R.id.loginBiliCard);
        mUserFace = findViewById(R.id.userFace);
        mUserName = findViewById(R.id.userName);
        mUserSign = findViewById(R.id.userSign);

        mLogout = findViewById(R.id.logout);

        if (Bili.biliAccount == null) {
            finish();
            return;
        }

        //加载头像/昵称/个性签名
        Glide.with(this).load(Bili.biliAccount.getFace()).placeholder(R.drawable.ic_2233).into(mUserFace);
        mUserName.setText(Bili.biliAccount.getUserName());
        mUserSign.setText(Bili.biliAccount.getSign());

        mLogout.setOnClickListener(v -> logout());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 登出
     * */
    private void logout() {
        DialogTools.confirm(this, getString(R.string.exit_login), getString(R.string.exit_login_confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setResult(1);
                finish();
            }
        }, null);

    }

    public void onClick(View view) {
        BiliFavourite.getFavourite();
    }
}

