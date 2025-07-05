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
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;

public class ChunkLoadListener implements Listener {

    private final StructureSpawner plugin;
    private final Random random = new Random();

    public ChunkLoadListener(StructureSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Chỉ xử lý khi chunk này là chunk mới được tạo
        if (!event.isNewChunk()) {
            return;
        }

        double spawnChance = plugin.getConfig().getDouble("ti-le-spawn", 0);
        if (spawnChance <= 0) {
            return;
        }

        // Kiểm tra tỉ lệ spawn
        if (random.nextDouble() * 100 > spawnChance) {
            return;
        }

        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        File[] schematicFiles = schematicsDir.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));

        if (schematicFiles == null || schematicFiles.length == 0) {
            return; // Không có schematic nào để spawn
        }

        // Chọn một schematic ngẫu nhiên
        File schematicFile = schematicFiles[random.nextInt(schematicFiles.length)];

        // Chạy tác vụ dán schematic bất đồng bộ để không làm lag server
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            pasteSchematic(schematicFile, event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        });
    }

    private void pasteSchematic(File schematicFile, World world, int chunkX, int chunkZ) {
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            plugin.getLogger().warning("Không thể nhận dạng định dạng của file schematic: " + schematicFile.getName());
            return;
        }

        // Tính toán vị trí trung tâm của chunk
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        // Tìm Y cao nhất trên mặt đất (không tính cây, cỏ)
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        int yOffset = plugin.getConfig().getInt("do-cao-so-voi-mat-dat", 1);
        
        BlockVector3 pasteLocation = BlockVector3.at(x, y + yOffset, z);

        // Sử dụng WorldEdit API
        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            Clipboard clipboard = reader.read();
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(pasteLocation)
                        .ignoreAirBlocks(true) // Rất quan trọng: để không khí trong schematic không phá hủy địa hình
                        .build();
                Operations.complete(operation);
                plugin.getLogger().info("Đã spawn công trình '" + schematicFile.getName() + "' tại " + pasteLocation.toString());
            } catch (WorldEditException e) {
                plugin.getLogger().severe("Lỗi khi dán công trình: " + schematicFile.getName());
                e.printStackTrace();
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Không thể đọc file schematic: " + schematicFile.getName());
            e.printStackTrace();
        }
    }
}