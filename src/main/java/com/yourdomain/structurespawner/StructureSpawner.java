package com.yourdomain.structurespawner;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class StructureSpawner extends JavaPlugin {

    private static StructureSpawner instance;
    private final Queue<PasteRequest> pasteQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask queueProcessorTask;

    // Các biến để quản lý file cấu hình structures.yml
    private File structuresFile;
    private FileConfiguration structuresConfig;

    @Override
    public void onEnable() {
        instance = this;

        // Kiểm tra plugin phụ thuộc WorldEdit
        if (getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("Plugin WorldEdit không được tìm thấy! Vô hiệu hóa StructureSpawner.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Tải các file cấu hình
        saveDefaultConfig();
        loadStructuresConfig();
        syncSchematicsWithConfig(); // Đồng bộ schematics với file cấu hình

        // Đăng ký sự kiện và lệnh
        getServer().getPluginManager().registerEvents(new ChunkLoadListener(this), this);
        getCommand("structurespawner").setExecutor(new Commands(this));

        // Bắt đầu bộ xử lý hàng đợi
        startQueueProcessor();

        getLogger().info("Plugin StructureSpawner đã được bật thành công!");
    }

    @Override
    public void onDisable() {
        // Hủy tác vụ đang chạy để tránh lỗi khi tắt/reload
        if (queueProcessorTask != null && !queueProcessorTask.isCancelled()) {
            queueProcessorTask.cancel();
        }
        getLogger().info("Plugin StructureSpawner đã được tắt.");
    }

    // --- CÁC PHƯƠNG THỨC QUẢN LÝ FILE STRUCTURES.YML ---

    /**
     * Tải file structures.yml từ thư mục của plugin.
     * Nếu file không tồn tại, nó sẽ được tạo từ file mặc định trong file .jar.
     */
    public void loadStructuresConfig() {
        structuresFile = new File(getDataFolder(), "structures.yml");
        if (!structuresFile.exists()) {
            // Lưu file mặc định từ trong JAR ra ngoài nếu chưa có
            saveResource("structures.yml", false);
        }
        structuresConfig = YamlConfiguration.loadConfiguration(structuresFile);
    }

    /**
     * Cung cấp quyền truy cập vào cấu hình của các công trình.
     * @return FileConfiguration của structures.yml.
     */
    public FileConfiguration getStructuresConfig() {
        return this.structuresConfig;
    }

    /**
     * Lưu lại các thay đổi vào file structures.yml.
     */
    public void saveStructuresConfig() {
        try {
            structuresConfig.save(structuresFile);
        } catch (IOException e) {
            getLogger().severe("Không thể lưu file structures.yml!");
            e.printStackTrace();
        }
    }
    
    /**
     * Quét thư mục 'schematics' và tự động thêm các file .schem mới
     * vào file cấu hình 'structures.yml' với các giá trị mặc định.
     */
    private void syncSchematicsWithConfig() {
        File schematicsDir = new File(getDataFolder(), "schematics");
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
            return;
        }
        
        File[] schematicFiles = schematicsDir.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));
        if (schematicFiles == null) return;
        
        boolean wasUpdated = false;
        for (File file : schematicFiles) {
            String path = "structures." + file.getName();
            // Nếu chưa có cấu hình cho file này
            if (!structuresConfig.contains(path)) {
                structuresConfig.set(path + ".enabled", true);
                structuresConfig.set(path + ".spawn-type", "LAND");
                // Thêm y-coordinate mặc định cho trường hợp admin đổi sang AIR
                if (!structuresConfig.contains(path + ".y-coordinate")) {
                    structuresConfig.set(path + ".y-coordinate", 150);
                }
                wasUpdated = true;
                getLogger().info("Đã phát hiện và thêm công trình mới '" + file.getName() + "' vào structures.yml.");
            }
        }
        
        // Chỉ lưu lại file nếu có sự thay đổi
        if (wasUpdated) {
            saveStructuresConfig();
        }
    }

    // --- CÁC PHƯƠNG THỨC XỬ LÝ HÀNG ĐỢI VÀ SPAWN (giữ nguyên) ---

    public void addPasteRequest(PasteRequest request) {
        pasteQueue.add(request);
    }

    private void startQueueProcessor() {
        this.queueProcessorTask = getServer().getScheduler().runTaskTimer(this, () -> {
            PasteRequest request = pasteQueue.poll();
            if (request != null) {
                pasteSchematic(request);
            }
        }, 100L, 100L); // Giữ khoảng cách 5 giây để giảm tải
    }

    private void pasteSchematic(PasteRequest request) {
        File schematicFile = request.getSchematicFile();
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            getLogger().warning("Không thể nhận dạng định dạng của file schematic: " + schematicFile.getName());
            return;
        }
        BlockVector3 pasteLocation = request.getPasteLocation();
        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            Clipboard clipboard = reader.read();
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(request.getWorld());
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(pasteLocation)
                        .ignoreAirBlocks(true)
                        .build();
                Operations.complete(operation);
                getLogger().info("Đã spawn thành công công trình '" + schematicFile.getName() + "' tại " + pasteLocation.toString());
            } catch (WorldEditException e) {
                getLogger().severe("Lỗi WorldEdit khi dán công trình: " + schematicFile.getName());
                e.printStackTrace();
            }
        } catch (IOException e) {
            getLogger().severe("Không thể đọc file schematic: " + schematicFile.getName());
            e.printStackTrace();
        }
    }

    public static StructureSpawner getInstance() {
        return instance;
    }

    public void reloadPluginConfig() {
        reloadConfig(); // Tải lại config.yml
        loadStructuresConfig(); // Tải lại structures.yml
        syncSchematicsWithConfig(); // Đồng bộ lại phòng trường hợp admin vừa thêm schem
    }

    public String getTranslatedMessage(String path) {
        String message = getConfig().getString(path, "Message not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public Queue<PasteRequest> getPasteQueue() {
        return this.pasteQueue;
    }
}