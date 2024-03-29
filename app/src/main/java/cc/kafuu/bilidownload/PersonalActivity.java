package cc.kafuu.bilidownload;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Pair;
import android.view.Gravity;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cc.kafuu.bilidownload.adapter.PersonalFragmentPagesAdapter;
import cc.kafuu.bilidownload.bilibili.Bili;
import cc.kafuu.bilidownload.bilibili.model.BiliUser;
import cc.kafuu.bilidownload.bilibili.model.BiliFollowType;
import cc.kafuu.bilidownload.fragment.personal.FollowFragment;
import cc.kafuu.bilidownload.fragment.personal.FavoriteFragment;
import cc.kafuu.bilidownload.fragment.personal.HistoryFragment;
import cc.kafuu.bilidownload.fragment.personal.MyVideoFragment;
import cc.kafuu.bilidownload.utils.DialogTools;

public class PersonalActivity extends BaseActivity {
    public static int ResultCodeLogout = 0x01;
    public static int ResultCodeVideoClicked = 0x02;

    private long mUserId;
    private String mUserFace;
    private String mUserName;
    private String mUserSign;
    private boolean mMyself;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal);
        Intent intent = getIntent();

        mMyself = intent.getBooleanExtra("myself", false);
        mUserId = intent.getLongExtra("accountId", 0);
        mUserFace = intent.getStringExtra("accountFace");
        mUserName = intent.getStringExtra("accountName");
        mUserSign = intent.getStringExtra("accountSign");

        if (mMyself && Bili.biliAccount == null) {
            finish();
            return;
        }

        initView();

    }

    public static void actionStartForResult(Context context, ActivityResultLauncher<Intent> launcher) {
        actionStartForResult(context, launcher, null);
    }

    public static void actionStartForResult(Context context, ActivityResultLauncher<Intent> launcher, BiliUser userCard) {
        if (ActivityCollector.contains(PersonalActivity.class)) {
            return;
        }

        Intent intent = new Intent(context, PersonalActivity.class);
        if (userCard != null) {
            intent.putExtra("myself", Bili.biliAccount != null && userCard.getId() == Bili.biliAccount.getId());
            intent.putExtra("accountId", userCard.getId());
            intent.putExtra("accountFace", userCard.getFace());
            intent.putExtra("accountName", userCard.getName());
            intent.putExtra("accountSign", userCard.getSign());
        } else {
            intent.putExtra("myself", true);
            intent.putExtra("accountId", Bili.biliAccount.getId());
            intent.putExtra("accountFace", Bili.biliAccount.getFace());
            intent.putExtra("accountName", Bili.biliAccount.getName());
            intent.putExtra("accountSign", Bili.biliAccount.getSign());
        }

        launcher.launch(intent);
    }

    private void initView() {
        final Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        toolbar.setTitle(mUserName);

        final CardView loginBiliCard = findViewById(R.id.loginBiliCard);
        final ImageView userFace = findViewById(R.id.userFace);
        final TextView userName = findViewById(R.id.userName);
        final TextView userSign = findViewById(R.id.userSign);

        final TabLayout tabLayout = findViewById(R.id.tabLayout);
        final ViewPager2 viewPager = findViewById(R.id.viewPager);

        //加载头像/昵称/个性签名
        Glide.with(this).load(mUserFace).placeholder(R.drawable.ic_2233).into(userFace);
        userName.setText(mUserName);

        if (mUserSign == null || mUserSign.length() == 0) {
            userSign.setText(getText(R.string.no_sign));
        } else {
            userSign.setText(mUserSign);
        }

        List<Pair<CharSequence, Fragment>> mFragments = new ArrayList<>();

        if (Bili.biliAccount != null && mUserId == Bili.biliAccount.getId()) {
            mFragments.add(new Pair<>(getString(R.string.history), HistoryFragment.newInstance()));
        }
        mFragments.add(new Pair<>(getString(R.string.uploaded_video), MyVideoFragment.newInstance(mUserId)));
        mFragments.add(new Pair<>(getString(R.string.favorite), FavoriteFragment.newInstance(mUserId)));
        mFragments.add(new Pair<>(getString(R.string.cartoon), FollowFragment.newInstance(mUserId, BiliFollowType.Cartoon)));
        mFragments.add(new Pair<>(getString(R.string.teleplay), FollowFragment.newInstance(mUserId, BiliFollowType.Teleplay)));

        viewPager.setAdapter(new PersonalFragmentPagesAdapter(this, mFragments));
        viewPager.setOffscreenPageLimit(Objects.requireNonNull(viewPager.getAdapter()).getItemCount());

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                tabLayout.selectTab(tabLayout.getTabAt(position));
            }
        });

        TabLayoutMediator mediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            TextView tabView = new TextView(PersonalActivity.this);
            tabView.setText(mFragments.get(position).first);
            tabView.setGravity(Gravity.CENTER);

            int[][] states = new int[2][];
            states[0] = new int[]{android.R.attr.state_selected};
            states[1] = new int[]{};
            tabView.setTextColor(new ColorStateList(states, new int[]{Color.parseColor("#F888A3"), Color.parseColor("#323232")}));

            tab.setCustomView(tabView);
        });

        mediator.attach();

        loginBiliCard.setOnClickListener(v -> logout());
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
     */
    private void logout() {
        if (!mMyself) {
            return;
        }
        DialogTools.confirm(this, getString(R.string.exit_login), getString(R.string.exit_login_confirm), (dialog, which) -> {
            setResult(ResultCodeLogout);
            finish();
        }, null);

    }

}

