package com.yourdomain.structurespawner;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.io.File;
import java.util.Random;

public class ChunkLoadListener implements Listener {

    private final StructureSpawner plugin;
    private final Random random = new Random();

    public ChunkLoadListener(StructureSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Chỉ xử lý khi chunk này là chunk mới được tạo ra
        if (!event.isNewChunk()) {
            return;
        }

        // Lấy tỉ lệ spawn từ config và thực hiện "quay số"
        double spawnChance = plugin.getConfig().getDouble("ti-le-spawn", 0);
        if (spawnChance <= 0 || (random.nextDouble() * 100 > spawnChance)) {
            return;
        }

        // Lấy danh sách các file schematic
        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        File[] schematicFiles = schematicsDir.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));

        // Nếu không có file schematic nào thì dừng lại
        if (schematicFiles == null || schematicFiles.length == 0) {
            return;
        }

        // Chọn một công trình ngẫu nhiên từ danh sách
        File schematicFile = schematicFiles[random.nextInt(schematicFiles.length)];
        World world = event.getWorld();

        // --- TÍNH TOÁN VỊ TRÍ ĐỂ SPAWN ---
        // Lấy tọa độ trung tâm của chunk
        int x = event.getChunk().getX() * 16 + 8;
        int z = event.getChunk().getZ() * 16 + 8;
        
        // Tìm tọa độ Y cao nhất trên mặt đất (không tính cây, cỏ,...)
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        
        // Lấy độ cao tùy chỉnh từ config
        int yOffset = plugin.getConfig().getInt("do-cao-so-voi-mat-dat", 1);
        
        // Tạo vector vị trí cuối cùng
        BlockVector3 pasteLocation = BlockVector3.at(x, y + yOffset, z);

        // --- BƯỚC QUAN TRỌNG NHẤT ---
        // Tạo một yêu cầu spawn mới
        PasteRequest request = new PasteRequest(schematicFile, world, pasteLocation);
        
        // Thêm yêu cầu này vào hàng đợi của plugin chính để xử lý sau
        plugin.addPasteRequest(request);
    }
}