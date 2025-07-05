package com.yourdomain.structurespawner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class StructureSpawner extends JavaPlugin {

    private static StructureSpawner instance;

    @Override
    public void onEnable() {
        instance = this;

        // Kiểm tra xem WorldEdit có được cài đặt không
        if (getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("----------------------------------------------------");
            getLogger().severe(getConfig().getString("messages.worldedit-not-found", "&cPlugin WorldEdit không được tìm thấy! Plugin StructureSpawner sẽ bị vô hiệu hóa."));
            getLogger().severe("----------------------------------------------------");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Tạo file config.yml và thư mục schematics
        saveDefaultConfig();
        File schematicsDir = new File(getDataFolder(), "schematics");
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
        }

        // Đăng ký các sự kiện và lệnh
        getServer().getPluginManager().registerEvents(new ChunkLoadListener(this), this);
        getCommand("structurespawner").setExecutor(new Commands(this));

        getLogger().info("Plugin StructureSpawner đã được bật thành công!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin StructureSpawner đã được tắt.");
    }

    public static StructureSpawner getInstance() {
        return instance;
    }
    
    public void reloadPluginConfig() {
        reloadConfig();
        // Bạn có thể thêm các logic khác khi reload ở đây nếu cần
    }

    // Tiện ích dịch mã màu
    public String getTranslatedMessage(String path) {
        String message = getConfig().getString(path, "Message not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}