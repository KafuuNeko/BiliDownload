package cc.kafuu.bilidownload.database;

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
        super(context, Objects.requireNonNull(context).getDataDir() + "/record.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS video_download_record(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "vid VARCHAR, " +
                "video_title VARCHAR, " +
                "part_title VARCHAR, " +
                "path VARCHAR, " +
                "format VARCHAR, " +
                "pic VARCHAR, " +
                "download_time DATETIME, " +
                "download_progress INTEGER)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            db.execSQL("alter table video_download_record add download_time DATETIME");
            db.execSQL("alter table video_download_record add download_progress INTEGER");
            db.execSQL("update video_download_record set download_time=datetime('now')");
            db.execSQL("update video_download_record set download_progress=1000");
        }
    }

    public static class DownloadRecord {
        private final long id;
        private final String vid;
        private final String video_title;
        private final String part_title;
        private final String path;
        private final String format;
        private final String pic;
        private final String downloadTime;
        private int downloadProgress;

        private DownloadRecord(long id, String vid, String video_title, String part_title, String path, String format, String pic, String downloadTime, int downloadProgress) {
            this.id = id;
            this.vid = vid;
            this.video_title = video_title;
            this.part_title = part_title;
            this.path = path;
            this.format = format;
            this.pic = pic;
            this.downloadTime = downloadTime;
            this.downloadProgress = downloadProgress;
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

        public String getDownloadTime() {
            return downloadTime;
        }

        public int getDownloadProgress() {
            return downloadProgress;
        }

        public void setDownloadProgress(int downloadProgress) {
            this.downloadProgress = downloadProgress;
        }
    }

    private static final Object mWriteLock = new Object();

    public DownloadRecord newDownloadRecord(String vid, String video_title, String part_title, String path, String format, String pic) {
        synchronized (mWriteLock) {
            getWritableDatabase().execSQL(
                    "INSERT INTO video_download_record(vid, video_title, part_title, path, format, pic, download_time, download_progress) VALUES(?, ?, ?, ?, ?, ?, datetime('now'), 0)",
                    new String[] {vid, video_title, part_title, path, format, pic});

            Cursor cursor = getReadableDatabase().rawQuery("select last_insert_rowid(), datetime('now') from video_download_record", null);
            int id = -1;
            if(cursor.moveToFirst()){
                id = cursor.getInt(0);
            }

            return new DownloadRecord(id, vid, video_title, part_title, path, format, pic, cursor.getString(1), 0);
        }
    }

    public List<DownloadRecord> getDownloadRecord() {
        List<DownloadRecord> records = new ArrayList<>();
        synchronized (mWriteLock) {
            Cursor cursor = getReadableDatabase().rawQuery("SELECT id, vid, video_title, part_title, path, format, pic, download_time, download_progress FROM video_download_record ORDER BY id DESC LIMIT 30", null);
            while (cursor.moveToNext()) {
                records.add(
                        new DownloadRecord (
                                cursor.getLong(0),
                                cursor.getString(1),
                                cursor.getString(2),
                                cursor.getString(3),
                                cursor.getString(4),
                                cursor.getString(5),
                                cursor.getString(6),
                                cursor.getString(7),
                                cursor.getInt(8)
                        )
                );
            }
        }
        return records;
    }

    public void updateDownloadRecord(DownloadRecord record) {
        synchronized (mWriteLock) {
            getWritableDatabase().execSQL("update video_download_record set download_progress=" + record.getDownloadProgress() + " where id=" + record.getId());
        }
    }

    public void removeDownloadRecord(DownloadRecord record) {
        synchronized (mWriteLock) {
            getWritableDatabase().execSQL("DELETE FROM video_download_record WHERE id=" + record.getId());
        }
    }

}
