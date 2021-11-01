package cc.kafuu.bilidownload.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cc.kafuu.bilidownload.database.model.VideoBase;
import cc.kafuu.bilidownload.database.model.VideoDownloadRecord;
import cc.kafuu.bilidownload.database.model.VideoInfo;

public class RecordDatabase extends SQLiteOpenHelper {
    public RecordDatabase(@Nullable Context context) {
        super(context, Objects.requireNonNull(context).getDataDir() + "/record.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        initDatabase(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            db.execSQL("ALTER TABLE video_download_record RENAME TO VideoDownloadRecord_v1");
            initDatabase(db);
        }
    }

    private void initDatabase(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS VideoDownloadRecord(id INTEGER PRIMARY KEY AUTOINCREMENT, downloadID INTEGER, avid INTEGER, cid INTEGER, quality INTEGER, downloadTime DATETIME, file VARCHAR)");
        db.execSQL("CREATE TABLE IF NOT EXISTS VideoInfo(avid INTEGER, cid INTEGER, quality INTEGER, videoPic VARCHAR, partPic VARCHAR, videoTitle VARCHAR, partTitle VARCHAR, videoDescription VARCHAR, partDescription VARCHAR)");
    }

    private long lastInsertRowId(String table) {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT last_insert_rowid() FROM " + table + " LIMIT 1", null);
        long rowId = cursor.moveToFirst() ? cursor.getLong(0) : -1;
        cursor.close();
        return rowId;
    }

    private VideoDownloadRecord queryVideoDownloadRecord(long id) {
        VideoDownloadRecord record = null;
        Cursor cursor = getReadableDatabase().rawQuery("SELECT * FROM VideoDownloadRecord WHERE id=" + id, null);
        if (cursor.moveToFirst()) {
            record = new VideoDownloadRecord(id,
                    cursor.getLong(cursor.getColumnIndex("avid")),
                    cursor.getLong(cursor.getColumnIndex("cid")),
                    cursor.getInt(cursor.getColumnIndex("quality")),
                    cursor.getLong(cursor.getColumnIndex("downloadID")),
                    cursor.getString(cursor.getColumnIndex("downloadTime")),
                    new File(cursor.getString(cursor.getColumnIndex("file"))));
        }
        cursor.close();
        return record;
    }

    private VideoDownloadRecord newVideoDownloadRecord(long downloadID, long avid, long cid, int quality, File file) {
        getWritableDatabase().execSQL("INSERT INTO VideoDownloadRecord VALUES(?, ?, ?, ?, datetime('now'), ?)"
                , new String[]{String.valueOf(downloadID), String.valueOf(avid), String.valueOf(cid), String.valueOf(quality), String.valueOf(file)});
        return queryVideoDownloadRecord(lastInsertRowId("VideoDownloadRecord"));
    }

    private VideoInfo queryVideoInfo(long avid, long cid, int quality) {
        VideoInfo info = null;

        Cursor cursor = getReadableDatabase().rawQuery("SELECT * FROM VideoInfo WHERE avid=? AND cid=? AND quality=?",
                new String[]{String.valueOf(avid), String.valueOf(cid), String.valueOf(quality)});

        if (cursor.moveToFirst()) {
            info = new VideoInfo(avid, cid, quality,
                    cursor.getString(cursor.getColumnIndex("videoPic")),
                    cursor.getString(cursor.getColumnIndex("partPic")),
                    cursor.getString(cursor.getColumnIndex("videoTitle")),
                    cursor.getString(cursor.getColumnIndex("partTitle")),
                    cursor.getString(cursor.getColumnIndex("videoDescription")),
                    cursor.getString(cursor.getColumnIndex("partDescription")));
        }

        cursor.close();
        return info;
    }

    private void insertOrUpdateVideoInfo(VideoInfo videoInfo) {
        String sql;

        if (queryVideoInfo(videoInfo.getAvid(), videoInfo.getCid(), videoInfo.getQuality()) != null) {
            sql = "UPDATE VideoInfo SET videoPic=?, partPic=?, videoTitle=?, partTitle=?, videoDescription=?, partDescription=? WHERE avid=? AND cid=? AND quality=?";
        } else {
            sql = "INSERT INTO VideoInfo(videoPic, partPic, videoTitle, partTitle, videoDescription, partDescription, avid, cid, quality) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }

        String[] args = new String[]{videoInfo.getVideoPic(),
                videoInfo.getPartPic(),
                videoInfo.getVideoTitle(),
                videoInfo.getPartTitle(),
                videoInfo.getVideoDescription(),
                videoInfo.getPartDescription(),
                String.valueOf(videoInfo.getAvid()),
                String.valueOf(videoInfo.getCid()),
                String.valueOf(videoInfo.getQuality())};

        getWritableDatabase().execSQL(sql, args);
    }

}
