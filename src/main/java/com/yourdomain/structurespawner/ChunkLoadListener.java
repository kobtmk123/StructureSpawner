package com.yourdomain.structurespawner;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.stream.Collectors;

public class ChunkLoadListener implements Listener {

    private final StructureSpawner plugin;
    private final Random random = new Random();
    
    // Danh sách các biome được coi là biển để kiểm tra
    private static final List<Biome> OCEAN_BIOMES = Arrays.asList(
            Biome.OCEAN, Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN,
            Biome.DEEP_FROZEN_OCEAN, Biome.DEEP_LUKEWARM_OCEAN, Biome.DEEP_OCEAN,
            Biome.FROZEN_OCEAN, Biome.LUKEWARM_OCEAN, Biome.WARM_OCEAN
    );

    public ChunkLoadListener(StructureSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        double spawnChance = plugin.getConfig().getDouble("ti-le-spawn", 0);
        if (spawnChance <= 0 || (random.nextDouble() * 100 > spawnChance)) return;

        FileConfiguration structuresConfig = plugin.getStructuresConfig();
        File schematicsDir = new File(plugin.getDataFolder(), "schematics");

        // Lọc danh sách các schematic có thể spawn (enabled = true)
        List<File> availableSchematics = Arrays.stream(schematicsDir.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic")))
                .filter(file -> structuresConfig.getBoolean("structures." + file.getName() + ".enabled", false))
                .collect(Collectors.toList());

        if (availableSchematics.isEmpty()) return;

        File schematicFile = availableSchematics.get(random.nextInt(availableSchematics.size()));
        World world = event.getWorld();
        String configPath = "structures." + schematicFile.getName();

        // Lấy thông tin từ structures.yml
        String spawnType = structuresConfig.getString(configPath + ".spawn-type", "LAND").toUpperCase();

        int x = event.getChunk().getX() * 16 + 8;
        int z = event.getChunk().getZ() * 16 + 8;
        int y;
        
        // Dựa vào spawn-type để quyết định cách tính tọa độ Y và kiểm tra điều kiện
        switch (spawnType) {
            case "LAND":
                Biome biome = world.getBiome(x, 0, z);
                if (OCEAN_BIOMES.contains(biome) || world.getHighestBlockAt(x, z).isLiquid()) {
                    return; // Nếu là biển hoặc trên mặt nước, hủy spawn
                }
                // Lấy Y của mặt đất liền
                y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                break;
                
            case "SEA_SURFACE":
                // Đảm bảo chỉ spawn ở biome biển
                if (!OCEAN_BIOMES.contains(world.getBiome(x, 0, z))) {
                    return; // Nếu không phải biome biển, hủy spawn
                }
                // Dùng MOTION_BLOCKING để lấy tọa độ Y của mặt nước (điểm cao nhất mà vật thể có thể va chạm)
                y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
                break;
                
            case "SEA_FLOOR":
                // Đảm bảo chỉ spawn ở biome biển
                if (!OCEAN_BIOMES.contains(world.getBiome(x, 0, z))) {
                    return; // Nếu không phải biome biển, hủy spawn
                }
                // Dùng OCEAN_FLOOR_WG để lấy tọa độ Y của đáy biển
                y = world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR_WG);
                break;
            
            case "AIR":
                // Lấy tọa độ Y cố định từ file config
                y = structuresConfig.getInt(configPath + ".y-coordinate", 150);
                break;

            case "SEA": // Trường hợp dự phòng nếu admin dùng cấu hình cũ 'SEA'
                plugin.getLogger().warning("Loại spawn 'SEA' đã lỗi thời cho công trình '" + schematicFile.getName() + "'. Vui lòng đổi thành 'SEA_SURFACE' hoặc 'SEA_FLOOR' trong structures.yml.");
                return;

            default:
                plugin.getLogger().warning("Loại spawn không hợp lệ '" + spawnType + "' cho file " + schematicFile.getName() + ". Vui lòng kiểm tra file structures.yml.");
                return;
        }

        // Áp dụng độ cao tùy chỉnh (ví dụ: đặt công trình cao hơn mặt nước 1 block)
        int yOffset = plugin.getConfig().getInt("do-cao-so-voi-mat-dat", 1);
        BlockVector3 newLocation = BlockVector3.at(x, y + yOffset, z);

        // Kiểm tra khoảng cách tối thiểu với các công trình khác
        int minDistance = plugin.getConfig().getInt("khoang-cach-toi-thieu", 150);
        double minDistanceSquared = Math.pow(minDistance, 2);
        Queue<PasteRequest> existingRequests = plugin.getPasteQueue();
        
        for (PasteRequest existingRequest : existingRequests) {
            if (existingRequest.getWorld().equals(world) && existingRequest.getPasteLocation().distanceSq(newLocation) < minDistanceSquared) {
                return; // Quá gần, hủy spawn
            }
        }
        
        // Thêm vào hàng đợi nếu mọi thứ hợp lệ
        PasteRequest request = new PasteRequest(schematicFile, world, newLocation);
        plugin.addPasteRequest(request);
        plugin.getLogger().info("Yêu cầu spawn (" + spawnType + ") cho '" + schematicFile.getName() + "' đã hợp lệ và được thêm vào hàng đợi.");
    }
}