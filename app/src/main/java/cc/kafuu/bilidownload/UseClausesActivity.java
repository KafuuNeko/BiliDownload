package cc.kafuu.bilidownload;

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

public class UseClausesActivity extends AppCompatActivity {
    private Toolbar mToolbar;
    private TextView mClauses;
    private Button mRefused, mAgree;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_use_clauses);

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
            getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("agree_clause_0", false).apply();
            finish();
        });
        mAgree.setOnClickListener(v -> {
            getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("agree_clause_0", true).apply();
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
}
