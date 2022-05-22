package cc.kafuu.bilidownload;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
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
import cc.kafuu.bilidownload.bilibili.account.BiliFollow;
import cc.kafuu.bilidownload.fragment.personal.FollowFragment;
import cc.kafuu.bilidownload.fragment.personal.FavoriteFragment;
import cc.kafuu.bilidownload.fragment.personal.HistoryFragment;
import cc.kafuu.bilidownload.utils.DialogTools;

public class PersonalActivity extends BaseActivity {
    public static int ResultCodeLogout = 0x01;
    public static int ResultCodeVideoClicked = 0x02;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal);
        initView();
    }

    public static void actionStartForResult(Context context, ActivityResultLauncher<Intent> launcher) {
        if (ActivityCollector.contains(PersonalActivity.class)) {
            return;
        }

        Intent intent = new Intent(context, PersonalActivity.class);
        launcher.launch(intent);
    }

    private void initView() {
        setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        final CardView loginBiliCard = findViewById(R.id.loginBiliCard);
        final ImageView userFace = findViewById(R.id.userFace);
        final TextView userName = findViewById(R.id.userName);
        final TextView userSign = findViewById(R.id.userSign);

        final TabLayout tabLayout = findViewById(R.id.tabLayout);
        final ViewPager2 viewPager = findViewById(R.id.viewPager);

        if (Bili.biliAccount == null) {
            finish();
            return;
        }

        //加载头像/昵称/个性签名
        Glide.with(this).load(Bili.biliAccount.getFace()).placeholder(R.drawable.ic_2233).into(userFace);
        userName.setText(Bili.biliAccount.getUserName());
        if (Bili.biliAccount.getSign() == null || Bili.biliAccount.getSign().length() == 0) {
            userSign.setText(getText(R.string.no_sign));
        } else {
            userSign.setText(Bili.biliAccount.getSign());
        }

        List<Pair<CharSequence, Fragment>> mFragments = new ArrayList<>();

        mFragments.add(new Pair<>(getString(R.string.history), HistoryFragment.newInstance()));
        mFragments.add(new Pair<>(getString(R.string.favorite), FavoriteFragment.newInstance()));

        mFragments.add(new Pair<>(getString(R.string.cartoon), FollowFragment.newInstance(BiliFollow.Type.Cartoon)));
        mFragments.add(new Pair<>(getString(R.string.teleplay), FollowFragment.newInstance(BiliFollow.Type.Teleplay)));


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
        DialogTools.confirm(this, getString(R.string.exit_login), getString(R.string.exit_login_confirm), (dialog, which) -> {
            setResult(ResultCodeLogout);
            finish();
        }, null);

    }

}

