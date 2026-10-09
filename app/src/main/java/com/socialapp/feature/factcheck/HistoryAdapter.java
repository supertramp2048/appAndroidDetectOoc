package com.socialapp.feature.factcheck;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.socialapp.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<DatabaseHelper.HistoryItem> historyList;
    private final Context context;

    public HistoryAdapter(List<DatabaseHelper.HistoryItem> historyList, Context context) {
        this.historyList = historyList;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DatabaseHelper.HistoryItem item = historyList.get(position);

        holder.verdictText.setText(formatVerdict(item.verdict));
        if ("FAKE".equalsIgnoreCase(item.verdict)) {
            holder.verdictText.setTextColor(0xFFD83A34);
            holder.verdictText.setBackgroundResource(R.drawable.bg_chip_danger);
        } else if ("TRUE".equalsIgnoreCase(item.verdict)) {
            holder.verdictText.setTextColor(0xFF12805C);
            holder.verdictText.setBackgroundResource(R.drawable.bg_chip_success);
        } else {
            holder.verdictText.setTextColor(0xFF5E6B78);
            holder.verdictText.setBackgroundResource(R.drawable.bg_chip_neutral);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        holder.dateText.setText(sdf.format(new Date(item.timestamp)));

        if (item.imagePath != null && (item.imagePath.startsWith("http://") || item.imagePath.startsWith("https://"))) {
            Glide.with(context).load(item.imagePath).into(holder.imageView);
        } else if (item.imagePath != null) {
            File imgFile = new File(item.imagePath);
            if (imgFile.exists()) {
                holder.imageView.setImageURI(Uri.fromFile(imgFile));
            } else {
                holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ResultActivity.class);
            intent.putExtra("PREDICTION_JSON", item.json);
            intent.putExtra("IMAGE_PATH", item.imagePath);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    private String formatVerdict(String verdict) {
        if ("FAKE".equalsIgnoreCase(verdict)) {
            return "Có dấu hiệu tin giả";
        }
        if ("TRUE".equalsIgnoreCase(verdict)) {
            return "Có thể tin cậy";
        }
        if (verdict == null || verdict.trim().isEmpty()) {
            return "Chưa xác định";
        }
        return verdict;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView verdictText, dateText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.history_image);
            verdictText = itemView.findViewById(R.id.history_verdict);
            dateText = itemView.findViewById(R.id.history_date);
        }
    }
}
