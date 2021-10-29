package cc.kafuu.bilidownload.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RecordDatabase extends SQLiteOpenHelper {
    public RecordDatabase(@Nullable Context context) {
        super(context, Objects.requireNonNull(context).getDataDir() + "/record.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS video_download_record(id INTEGER PRIMARY KEY AUTOINCREMENT, vid VARCHAR, video_title VARCHAR, part_title VARCHAR, path VARCHAR, format VARCHAR, pic VARCHAR)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public static class DownloadRecord {
        private long id;
        private final String vid;
        private final String video_title;
        private final String part_title;
        private final String path;
        private final String format;
        private final String pic;

        private DownloadRecord(long id, String vid, String video_title, String part_title, String path, String format, String pic) {
            this.id = id;
            this.vid = vid;
            this.video_title = video_title;
            this.part_title = part_title;
            this.path = path;
            this.format = format;
            this.pic = pic;
        }

        public long getId() {
            return id;
        }

        public String getFormat() {
            return format;
        }

        public String getPartTitle() {
            return part_title;
        }

        public String getPath() {
            return path;
        }

        public String getVid() {
            return vid;
        }

        public String getVideoTitle() {
            return video_title;
        }

        public String getPic() {
            return pic;
        }
    }

    private static final Integer mWriteLock = 0;

    public void insertDownloadRecord(String vid, String video_title, String part_title, String path, String format, String pic) {
        synchronized (mWriteLock) {
            getWritableDatabase().execSQL(
                    "INSERT INTO video_download_record(vid, video_title, part_title, path, format, pic) VALUES(?, ?, ?, ?, ?, ?)",
                    new String[] {vid, video_title, part_title, path, format, pic});
        }
    }

    public List<DownloadRecord> getDownloadRecord() {
        List<DownloadRecord> records = new ArrayList<>();
        synchronized (mWriteLock) {
            Cursor cursor = getReadableDatabase().rawQuery("SELECT id, vid, video_title, part_title, path, format, pic FROM video_download_record ORDER BY id DESC LIMIT 30", null);
            while (cursor.moveToNext()) {
                records.add(
                        new DownloadRecord (
                                cursor.getLong(0),
                                cursor.getString(1),
                                cursor.getString(2),
                                cursor.getString(3),
                                cursor.getString(4),
                                cursor.getString(5),
                                cursor.getString(6)
                        )
                );
            }
        }
        return records;
    }

    public void removeDownloadRecord(DownloadRecord record) {
        synchronized (mWriteLock) {
            getWritableDatabase().execSQL("DELETE FROM video_download_record WHERE id=" + record.getId());
        }
    }

}
