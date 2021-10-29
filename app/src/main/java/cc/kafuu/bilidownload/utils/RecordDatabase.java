package cc.kafuu.bilidownload.utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.Objects;

public class RecordDatabase extends SQLiteOpenHelper {
    public RecordDatabase(@Nullable Context context) {
        super(context, Objects.requireNonNull(context).getDataDir() + "/record.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS video_download_record(id INTEGER PRIMARY KEY AUTOINCREMENT, vid VARCHAR, video_title VARCHAR, part_title VARCHAR, path VARCHAR, format VARCHAR)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
