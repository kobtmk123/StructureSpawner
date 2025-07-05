package com.yourdomain.structurespawner;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class Commands implements CommandExecutor {

    private final StructureSpawner plugin;

    public Commands(StructureSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("structurespawner.admin")) {
            sender.sendMessage(plugin.getTranslatedMessage("messages.prefix") + plugin.getTranslatedMessage("messages.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload":
                plugin.reloadPluginConfig();
                sender.sendMessage(plugin.getTranslatedMessage("messages.prefix") + plugin.getTranslatedMessage("messages.reload-success"));
                break;
            case "list":
                listSchematics(sender);
                break;
            default:
                sendHelpMessage(sender);
                break;
        }
        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        String prefix = plugin.getTranslatedMessage("messages.prefix");
        sender.sendMessage(prefix + "Sử dụng các lệnh sau:");
        sender.sendMessage(prefix + "/ss reload - Tải lại file config.yml.");
        sender.sendMessage(prefix + "/ss list - Liệt kê các file công trình đã có.");
    }

    private void listSchematics(CommandSender sender) {
        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        File[] schematicFiles = schematicsDir.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));

        if (schematicFiles == null || schematicFiles.length == 0) {
            sender.sendMessage(plugin.getTranslatedMessage("messages.prefix") + plugin.getTranslatedMessage("messages.list-empty"));
            return;
        }

        sender.sendMessage(plugin.getTranslatedMessage("messages.prefix") + plugin.getTranslatedMessage("messages.list-header"));
        for (File file : schematicFiles) {
            sender.sendMessage(plugin.getTranslatedMessage("messages.list-item").replace("{file_name}", file.getName()));
        }
    }
}