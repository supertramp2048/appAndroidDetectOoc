package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager {
    private static final String PREF_NAME = "prediction_history";
    private static final String KEY_HISTORY = "history_list";
    private static final int MAX_HISTORY_SIZE = 15;

    public static void saveResult(Context context, PredictionResponse result, String imagePath) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        List<HistoryItem> history = getHistory(context);

        // Tạo item mới kèm thời gian lưu
        HistoryItem newItem = new HistoryItem(result, imagePath, System.currentTimeMillis());
        
        // Thêm vào đầu danh sách
        history.add(0, newItem);

        // Giới hạn 15 bản ghi
        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(0, MAX_HISTORY_SIZE);
        }

        // Lưu lại vào SharedPreferences
        String json = new Gson().toJson(history);
        prefs.edit().putString(KEY_HISTORY, json).apply();
    }

    public static List<HistoryItem> getHistory(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<HistoryItem>>() {}.getType();
        return new Gson().fromJson(json, type);
    }

    // Class đại diện cho một mục trong lịch sử
    public static class HistoryItem {
        public PredictionResponse response;
        public String imagePath;
        public long timestamp;

        public HistoryItem(PredictionResponse response, String imagePath, long timestamp) {
            this.response = response;
            this.imagePath = imagePath;
            this.timestamp = timestamp;
        }
    }
}
