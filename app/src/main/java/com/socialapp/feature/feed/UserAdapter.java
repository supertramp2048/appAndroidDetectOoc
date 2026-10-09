package com.socialapp.feature.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.socialapp.R;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.ui.UserAvatarView;

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    public interface OnUserClickListener {
        void onUserClick(@NonNull UserResource user);
    }

    private final List<UserResource> users = new ArrayList<>();
    private final OnUserClickListener listener;

    public UserAdapter(@Nullable OnUserClickListener listener) {
        this.listener = listener;
    }

    public void setUsers(@Nullable List<UserResource> newUsers) {
        users.clear();
        if (newUsers != null) {
            users.addAll(newUsers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_search, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserResource user = users.get(position);

        holder.userNameText.setText(user.name != null ? user.name : "Thành viên");
        holder.avatarView.loadAvatar(user.getAvatarUrl());

        if (user.role != null && !user.role.trim().isEmpty()) {
            holder.userRoleText.setText(user.role.toUpperCase());
            holder.userRoleText.setVisibility(View.VISIBLE);
        } else {
            holder.userRoleText.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        UserAvatarView avatarView;
        TextView userNameText;
        TextView userRoleText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarView = itemView.findViewById(R.id.user_search_avatar);
            userNameText = itemView.findViewById(R.id.user_search_name);
            userRoleText = itemView.findViewById(R.id.user_search_role);
        }
    }
}
