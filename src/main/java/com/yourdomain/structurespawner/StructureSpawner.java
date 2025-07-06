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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class StructureSpawner extends JavaPlugin {

    private static StructureSpawner instance;

    // Hàng đợi thread-safe để chứa các yêu cầu spawn công trình
    private final Queue<PasteRequest> pasteQueue = new ConcurrentLinkedQueue<>();
    // Tác vụ lặp lại để xử lý hàng đợi
    private BukkitTask queueProcessorTask;

    @Override
    public void onEnable() {
        instance = this;

        // Kiểm tra xem WorldEdit có được cài đặt không
        if (getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("----------------------------------------------------");
            getLogger().severe(getTranslatedMessage("messages.worldedit-not-found"));
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

        // Khởi động bộ xử lý hàng đợi
        startQueueProcessor();

        getLogger().info("Plugin StructureSpawner đã được bật thành công!");
    }

    @Override
    public void onDisable() {
        // Hủy tác vụ xử lý hàng đợi để tránh memory leak khi reload hoặc tắt server
        if (queueProcessorTask != null && !queueProcessorTask.isCancelled()) {
            queueProcessorTask.cancel();
        }
        getLogger().info("Plugin StructureSpawner đã được tắt.");
    }

    /**
     * Thêm một yêu cầu dán công trình vào hàng đợi để xử lý sau.
     * @param request Thông tin về công trình cần dán.
     */
    public void addPasteRequest(PasteRequest request) {
        pasteQueue.add(request);
    }

    /**
     * Bắt đầu một tác vụ lặp lại để xử lý các yêu cầu trong hàng đợi một cách tuần tự,
     * tránh gây quá tải cho server.
     */
    private void startQueueProcessor() {
        // Chạy tác vụ này trên luồng chính của server
        // Bắt đầu sau 2 giây (40 ticks), và lặp lại mỗi 2 giây (40 ticks)
        this.queueProcessorTask = getServer().getScheduler().runTaskTimer(this, () -> {
            // Lấy một yêu cầu từ hàng đợi ra (nếu có)
            PasteRequest request = pasteQueue.poll();
            if (request != null) {
                // Nếu có yêu cầu, tiến hành dán công trình
                getLogger().info("Đang xử lý một yêu cầu spawn từ hàng đợi...");
                pasteSchematic(request);
            }
        }, 40L, 40L);
    }

    /**
     * Thực hiện việc dán một công trình bằng WorldEdit API.
     * @param request Yêu cầu dán công trình chứa đầy đủ thông tin.
     */
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
                        .ignoreAirBlocks(true) // Quan trọng: không phá hủy địa hình bằng các khối không khí
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
        reloadConfig();
    }

    public String getTranslatedMessage(String path) {
        String message = getConfig().getString(path, "Message not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}