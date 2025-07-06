package com.yourdomain.structurespawner;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.io.File;
import java.util.Queue;
import java.util.Random;

public class ChunkLoadListener implements Listener {

    private final StructureSpawner plugin;
    private final Random random = new Random();

    public ChunkLoadListener(StructureSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // --- BƯỚC 1: CÁC ĐIỀU KIỆN CƠ BẢN ---
        // Chỉ xử lý khi chunk này là chunk mới được tạo ra
        if (!event.isNewChunk()) {
            return;
        }

        // Lấy tỉ lệ spawn từ config và thực hiện "quay số"
        double spawnChance = plugin.getConfig().getDouble("ti-le-spawn", 0);
        if (spawnChance <= 0 || (random.nextDouble() * 100 > spawnChance)) {
            return;
        }

        // --- BƯỚC 2: CHUẨN BỊ DỮ LIỆU ---
        // Lấy danh sách các file schematic
        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        File[] schematicFiles = schematicsDir.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));

        // Nếu không có file schematic nào thì dừng lại
        if (schematicFiles == null || schematicFiles.length == 0) {
            return;
        }

        // Chọn một công trình ngẫu nhiên từ danh sách và lấy thông tin thế giới
        File schematicFile = schematicFiles[random.nextInt(schematicFiles.length)];
        World world = event.getWorld();

        // --- BƯỚC 3: TÍNH TOÁN VỊ TRÍ DỰ KIẾN ---
        int x = event.getChunk().getX() * 16 + 8;
        int z = event.getChunk().getZ() * 16 + 8;
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        int yOffset = plugin.getConfig().getInt("do-cao-so-voi-mat-dat", 1);
        BlockVector3 newLocation = BlockVector3.at(x, y + yOffset, z);

        // --- BƯỚC 4: KIỂM TRA KHOẢNG CÁCH (LOGIC QUAN TRỌNG NHẤT) ---
        int minDistance = plugin.getConfig().getInt("khoang-cach-toi-thieu", 150);
        // Tính bình phương khoảng cách để so sánh. Việc này hiệu quả hơn nhiều so với
        // việc tính căn bậc hai (distance), giúp tiết kiệm tài nguyên server.
        double minDistanceSquared = Math.pow(minDistance, 2);

        // Lấy hàng đợi các công trình đang chờ xử lý từ class chính
        Queue<PasteRequest> existingRequests = plugin.getPasteQueue();

        // Lặp qua tất cả các yêu cầu đang có trong hàng đợi
        for (PasteRequest existingRequest : existingRequests) {
            // Chỉ kiểm tra khoảng cách với các công trình trong cùng một thế giới
            if (existingRequest.getWorld().equals(world)) {
                // Tính bình phương khoảng cách giữa vị trí mới và vị trí đang chờ
                double distanceSquared = existingRequest.getPasteLocation().distanceSq(newLocation);
                
                // Nếu khoảng cách nhỏ hơn mức tối thiểu, hủy bỏ việc spawn và thoát khỏi hàm
                if (distanceSquared < minDistanceSquared) {
                    return; // Hủy bỏ vì quá gần
                }
            }
        }

        // --- BƯỚC 5: THÊM YÊU CẦU VÀO HÀNG ĐỢI ---
        // Nếu tất cả các kiểm tra đều vượt qua, tạo một yêu cầu mới
        PasteRequest request = new PasteRequest(schematicFile, world, newLocation);
        
        // Thêm yêu cầu vào hàng đợi của plugin để xử lý sau
        plugin.addPasteRequest(request);
        plugin.getLogger().info("Yêu cầu spawn tại " + newLocation.toString() + " đã hợp lệ và được thêm vào hàng đợi.");
    }
}