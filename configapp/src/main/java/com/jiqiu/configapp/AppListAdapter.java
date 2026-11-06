package com.jiqiu.configapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用列表适配器
 */
public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {
    
    private static final java.util.concurrent.ExecutorService ICONS = java.util.concurrent.Executors.newFixedThreadPool(2);
    private final android.util.LruCache<String, android.graphics.drawable.Drawable> icons = new android.util.LruCache<>(96);
    private List<AppInfo> appList;
    private List<AppInfo> filteredAppList;
    private OnAppToggleListener onAppToggleListener;
    private OnAppClickListener onAppClickListener;
    
    public interface OnAppToggleListener {
        void onAppToggle(AppInfo appInfo, boolean isEnabled);
    }
    
    public interface OnAppClickListener {
        void onAppClick(AppInfo appInfo);
    }
    
    public AppListAdapter() {
        this.appList = new ArrayList<>();
        this.filteredAppList = new ArrayList<>();
    }
    
    public void setAppList(List<AppInfo> appList) {
        this.appList = appList;
        this.filteredAppList = new ArrayList<>(appList);
        sortVisible();
        notifyDataSetChanged();
    }
    
    public void setOnAppToggleListener(OnAppToggleListener listener) {
        this.onAppToggleListener = listener;
    }
    
    public void setOnAppClickListener(OnAppClickListener listener) {
        this.onAppClickListener = listener;
    }
    
    public void filterApps(String query, boolean hideSystemApps) {
        filteredAppList.clear();
        
        for (AppInfo app : appList) {
            // 过滤系统应用
            if (hideSystemApps && app.isSystemApp()) {
                continue;
            }
            
            // 搜索过滤
            if (query == null || query.isEmpty() || 
                app.getAppName().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT)) ||
                app.getPackageName().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))) {
                filteredAppList.add(app);
            }
        }
        
        sortVisible();
        notifyDataSetChanged();
    }
    
    private void sortVisible() {
        filteredAppList.sort(new AppOrder(java.util.Locale.getDefault()));
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo appInfo = filteredAppList.get(position);
        holder.bind(appInfo);
    }
    
    @Override
    public int getItemCount() {
        return filteredAppList.size();
    }
    
    class AppViewHolder extends RecyclerView.ViewHolder {
        private ImageView appIcon;
        private TextView appName;
        private TextView packageName;
        private TextView systemAppLabel;
        private SwitchMaterial switchEnable;
        
        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.app_icon);
            appName = itemView.findViewById(R.id.app_name);
            packageName = itemView.findViewById(R.id.package_name);
            systemAppLabel = itemView.findViewById(R.id.system_app_label);
            switchEnable = itemView.findViewById(R.id.switch_enable);
        }
        
        public void bind(AppInfo appInfo) {
            String key = appInfo.getPackageName();
            appIcon.setTag(key);
            android.graphics.drawable.Drawable cached = icons.get(key);
            appIcon.setImageDrawable(cached != null ? cached : itemView.getContext().getPackageManager().getDefaultActivityIcon());
            if (cached == null) {
                android.content.Context context = itemView.getContext().getApplicationContext();
                ICONS.execute(() -> {
                    try {
                        android.graphics.drawable.Drawable icon = context.getPackageManager().getApplicationIcon(key);
                        appIcon.post(() -> {
                            icons.put(key, icon);
                            if (key.equals(appIcon.getTag())) appIcon.setImageDrawable(icon);
                        });
                    } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
                });
            }
            appName.setText(appInfo.getAppName());
            packageName.setText(appInfo.getPackageName());
            
            // 显示系统应用标签
            if (appInfo.isSystemApp()) {
                systemAppLabel.setVisibility(View.VISIBLE);
            } else {
                systemAppLabel.setVisibility(View.GONE);
            }
            
            // 设置开关状态
            switchEnable.setOnCheckedChangeListener(null); // 清除之前的监听器
            switchEnable.setChecked(appInfo.isEnabled());
            
            // 设置开关监听器
            switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                appInfo.setEnabled(isChecked);
                if (onAppToggleListener != null) {
                    onAppToggleListener.onAppToggle(appInfo, isChecked);
                    sortVisible();
                    notifyDataSetChanged();
                }
            });
            
            // 设置整个item的点击监听器
            itemView.setOnClickListener(v -> {
                if (onAppClickListener != null) {
                    onAppClickListener.onAppClick(appInfo);
                }
            });
        }
    }
}
