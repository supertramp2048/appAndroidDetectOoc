package com.socialapp.feature.factcheck;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.socialapp.R;

import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        Toolbar toolbar = findViewById(R.id.history_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Lịch sử tra cứu");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        RecyclerView recyclerView = findViewById(R.id.history_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        try (DatabaseHelper dbHelper = new DatabaseHelper(this)) {
            List<DatabaseHelper.HistoryItem> historyList = dbHelper.getAllHistory();
            HistoryAdapter adapter = new HistoryAdapter(historyList, this);
            recyclerView.setAdapter(adapter);

            View emptyState = findViewById(R.id.empty_history_state);
            boolean hasHistory = !historyList.isEmpty();
            recyclerView.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
            emptyState.setVisibility(hasHistory ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
