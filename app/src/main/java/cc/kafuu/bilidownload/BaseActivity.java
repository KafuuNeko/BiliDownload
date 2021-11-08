package cc.kafuu.bilidownload;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ActivityCollector.contains(this)) {
            ActivityCollector.addActivity(this);
        }
        Log.d(TAG, "onCreate: " + getClass().getSimpleName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ActivityCollector.contains(this)) {
            ActivityCollector.removeActivity(this);
        }
    }
}
