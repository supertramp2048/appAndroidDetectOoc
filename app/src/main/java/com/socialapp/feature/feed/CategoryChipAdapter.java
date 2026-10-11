package com.socialapp.feature.feed;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.socialapp.R;
import com.socialapp.core.network.dto.CategoryResource;

import java.util.ArrayList;
import java.util.List;

public class CategoryChipAdapter extends RecyclerView.Adapter<CategoryChipAdapter.ViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(@Nullable CategoryResource category);
    }

    private final List<CategoryResource> categories = new ArrayList<>();
    private final OnCategoryClickListener listener;
    @Nullable
    private CategoryResource selectedCategory = null; // null represents "Tất cả"

    public CategoryChipAdapter(@Nullable OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setCategories(@Nullable List<CategoryResource> newCategories) {
        categories.clear();
        if (newCategories != null) {
            categories.addAll(newCategories);
        }
        notifyDataSetChanged();
    }

    public void setSelectedCategory(@Nullable CategoryResource category) {
        this.selectedCategory = category;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_chip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position == 0) {
            holder.chipText.setText("Tất cả");
            boolean isSelected = selectedCategory == null;
            updateChipStyle(holder.chipText, isSelected);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(null);
                }
            });
        } else {
            CategoryResource cat = categories.get(position - 1);
            holder.chipText.setText(cat.name != null ? cat.name : "");
            boolean isSelected = selectedCategory != null && selectedCategory.id == cat.id;
            updateChipStyle(holder.chipText, isSelected);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(cat);
                }
            });
        }
    }

    private void updateChipStyle(TextView chipText, boolean isSelected) {
        if (isSelected) {
            chipText.setBackgroundResource(R.drawable.bg_chip_selected);
            chipText.setTextColor(0xFFFFFFFF);
            chipText.setTypeface(null, Typeface.BOLD);
        } else {
            chipText.setBackgroundResource(R.drawable.bg_chip_neutral);
            chipText.setTextColor(0xFF333333);
            chipText.setTypeface(null, Typeface.NORMAL);
        }
    }

    @Override
    public int getItemCount() {
        return categories.size() + 1; // +1 for "Tất cả" chip
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView chipText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            chipText = itemView.findViewById(R.id.category_chip_text);
        }
    }
}
