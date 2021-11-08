package cc.kafuu.bilidownload;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class UseClausesActivity extends BaseActivity {
    private Toolbar mToolbar;
    private TextView mClauses;
    private Button mRefused, mAgree;

    private boolean mUserAgree;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_use_clauses);

        mUserAgree = getSharedPreferences("app", MODE_PRIVATE).getBoolean("agree_clause_0", false);

        findView();
        initView();
    }

    private void findView() {
        mToolbar = findViewById(R.id.toolbar);
        mClauses = findViewById(R.id.clauses);

        mRefused = findViewById(R.id.refused);
        mAgree = findViewById(R.id.agree);
    }

    private void initView() {
        setSupportActionBar(mToolbar);
        mClauses.setText(getDisclaimer());

        mRefused.setOnClickListener(v -> {
            mUserAgree = false;
            finish();
        });
        mAgree.setOnClickListener(v -> {
            mUserAgree = true;
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("agree_clause_0", mUserAgree).apply();

        if (!mUserAgree) {
            ActivityCollector.finishAll();
        }
    }

    private String getDisclaimer() {
        StringBuilder stringBuilder = new StringBuilder();

        try(InputStream in = getAssets().open("Clauses.txt")) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append('\n');
            }
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.clauses_load_failure, Toast.LENGTH_SHORT).show();
            finish();
        }

        return stringBuilder.toString();
    }

    public static void clauseInspection(Context context) {
        if (!context.getSharedPreferences("app", MODE_PRIVATE).getBoolean("agree_clause_0", false)) {
            context.startActivity(new Intent(context, UseClausesActivity.class));
        }
    }
}
