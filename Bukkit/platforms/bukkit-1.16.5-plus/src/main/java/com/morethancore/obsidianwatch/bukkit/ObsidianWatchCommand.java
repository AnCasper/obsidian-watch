package com.morethancore.obsidianwatch.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ObsidianWatchCommand implements CommandExecutor, TabCompleter {
    private final ObsidianWatchService service;

    public ObsidianWatchCommand(ObsidianWatchService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("obsidianwatch.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use Obsidian Watch.");
            return true;
        }

        if (args.length == 0 || "menu".equalsIgnoreCase(args[0])) {
            sendMenu(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if ("help".equals(sub)) {
            sendHelp(sender);
            return true;
        }

        if ("status".equals(sub)) {
            sender.sendMessage(service.statusMessage());
            return true;
        }

        if ("diagnostics".equals(sub) || "diag".equals(sub)) {
            sender.sendMessage(service.diagnosticsMessage());
            return true;
        }

        if ("config".equals(sub)) {
            sender.sendMessage(service.configMessage());
            return true;
        }

        if ("reload".equals(sub)) {
            service.reload();
            sender.sendMessage(ChatColor.GREEN + "[Obsidian Watch] Config reloaded. Sync queued.");
            return true;
        }

        if ("sync".equals(sub)) {
            service.syncAllAsync();
            sender.sendMessage(ChatColor.GREEN + "[Obsidian Watch] Sync queued.");
            return true;
        }

        if ("heartbeat".equals(sub)) {
            service.heartbeatAsync();
            sender.sendMessage(ChatColor.GREEN + "[Obsidian Watch] Heartbeat queued.");
            return true;
        }

        if ("check".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /ow check <uuid|cached-username> [username]");
                return true;
            }
            String username = args.length >= 3 ? args[2] : null;
            sender.sendMessage(service.checkMessage(args[1], username));
            return true;
        }

        if ("testlog".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /ow testlog <uuid|cached-username> <notify|kick|tempban|ban|ban_dry_run|exempted> [username]");
                return true;
            }
            String username = args.length >= 4 ? args[3] : null;
            sender.sendMessage(service.testActionLog(args[1], args[2], username));
            return true;
        }

        if ("exempt".equals(sub)) {
            handleExempt(sender, args);
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void handleExempt(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /ow exempt <list|add|remove> [uuid]");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            sender.sendMessage(service.listExemptionsMessage());
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /ow exempt " + action + " <uuid>");
            return;
        }

        if ("add".equals(action)) {
            sender.sendMessage(service.addExemption(args[2]));
            return;
        }

        if ("remove".equals(action) || "del".equals(action) || "delete".equals(action)) {
            sender.sendMessage(service.removeExemption(args[2]));
            return;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /ow exempt <list|add|remove> [uuid]");
    }

    private void sendMenu(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_AQUA + "==== Obsidian Watch ====");
        sender.sendMessage(ChatColor.GRAY + "/ow status " + ChatColor.DARK_GRAY + "- " + ChatColor.WHITE + "Show cache and sync status");
        sender.sendMessage(ChatColor.GRAY + "/ow diagnostics " + ChatColor.DARK_GRAY + "- " + ChatColor.WHITE + "Show API/config diagnostics");
        sender.sendMessage(ChatColor.GRAY + "/ow sync " + ChatColor.DARK_GRAY + "- " + ChatColor.WHITE + "Sync remote config and snapshot");
        sender.sendMessage(ChatColor.GRAY + "/ow check <uuid|name> " + ChatColor.DARK_GRAY + "- " + ChatColor.WHITE + "Check cached entry");
        sender.sendMessage(ChatColor.GRAY + "/ow exempt list/add/remove " + ChatColor.DARK_GRAY + "- " + ChatColor.WHITE + "Manage local exemptions");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_AQUA + "Obsidian Watch commands:");
        sender.sendMessage(ChatColor.GRAY + "/ow");
        sender.sendMessage(ChatColor.GRAY + "/ow help");
        sender.sendMessage(ChatColor.GRAY + "/ow status");
        sender.sendMessage(ChatColor.GRAY + "/ow diagnostics");
        sender.sendMessage(ChatColor.GRAY + "/ow config");
        sender.sendMessage(ChatColor.GRAY + "/ow reload");
        sender.sendMessage(ChatColor.GRAY + "/ow sync");
        sender.sendMessage(ChatColor.GRAY + "/ow heartbeat");
        sender.sendMessage(ChatColor.GRAY + "/ow check <uuid|cached-username> [username]");
        sender.sendMessage(ChatColor.GRAY + "/ow testlog <uuid|cached-username> <action> [username]");
        sender.sendMessage(ChatColor.GRAY + "/ow exempt list");
        sender.sendMessage(ChatColor.GRAY + "/ow exempt add <uuid>");
        sender.sendMessage(ChatColor.GRAY + "/ow exempt remove <uuid>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("obsidianwatch.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(args[0], Arrays.asList("help", "status", "diagnostics", "config", "reload", "sync", "heartbeat", "check", "testlog", "exempt"));
        }

        if (args.length == 2 && "exempt".equalsIgnoreCase(args[0])) {
            return filter(args[1], Arrays.asList("list", "add", "remove"));
        }

        if (args.length == 3 && "testlog".equalsIgnoreCase(args[0])) {
            return filter(args[2], Arrays.asList("notify", "kick", "tempban", "ban", "ban_dry_run", "exempted"));
        }

        return Collections.emptyList();
    }

    private List<String> filter(String token, List<String> options) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
