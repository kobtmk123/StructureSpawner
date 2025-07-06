package com.yourdomain.structurespawner;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.World;

import java.io.File;

public class PasteRequest {
    private final File schematicFile;
    private final World world;
    private final BlockVector3 pasteLocation;

    public PasteRequest(File schematicFile, World world, BlockVector3 pasteLocation) {
        this.schematicFile = schematicFile;
        this.world = world;
        this.pasteLocation = pasteLocation;
    }

    public File getSchematicFile() {
        return schematicFile;
    }

    public World getWorld() {
        return world;
    }

    public BlockVector3 getPasteLocation() {
        return pasteLocation;
    }
}