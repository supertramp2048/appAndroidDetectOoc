package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "History.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NAME = "history";
    public static final String COL_ID = "id";
    public static final String COL_VERDICT = "verdict";
    public static final String COL_IMAGE_PATH = "image_path";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_JSON = "full_json";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_VERDICT + " TEXT, " +
                COL_IMAGE_PATH + " TEXT, " +
                COL_TIMESTAMP + " INTEGER, " +
                COL_JSON + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertHistory(String verdict, String imagePath, String json) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_VERDICT, verdict);
        values.put(COL_IMAGE_PATH, imagePath);
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        values.put(COL_JSON, json);

        db.insert(TABLE_NAME, null, values);

        // Xóa các bản ghi cũ, chỉ giữ lại 15 bản ghi mới nhất
        db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + COL_ID + " NOT IN (" +
                "SELECT " + COL_ID + " FROM " + TABLE_NAME + 
                " ORDER BY " + COL_TIMESTAMP + " DESC LIMIT 15)");
        db.close();
    }

    public List<HistoryItem> getAllHistory() {
        List<HistoryItem> historyList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY " + COL_TIMESTAMP + " DESC", null);

        if (cursor.moveToFirst()) {
            do {
                HistoryItem item = new HistoryItem(
                        cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        cursor.getString(4)
                );
                historyList.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return historyList;
    }

    public static class HistoryItem {
        public int id;
        public String verdict;
        public String imagePath;
        public long timestamp;
        public String json;

        public HistoryItem(int id, String verdict, String imagePath, long timestamp, String json) {
            this.id = id;
            this.verdict = verdict;
            this.imagePath = imagePath;
            this.timestamp = timestamp;
            this.json = json;
        }
    }
}
