package com.morethancore.obsidianwatch.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class BungeeWatchCommand extends Command implements TabExecutor {
    private final ObsidianWatchBungeeService service;

    public BungeeWatchCommand(ObsidianWatchBungeeService service) {
        super("ow", "obsidianwatch.admin", "obsidianwatch");
        this.service = service;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("obsidianwatch.admin")) {
            send(sender, "You do not have permission to use Obsidian Watch.");
            return;
        }

        if (args.length == 0 || "menu".equalsIgnoreCase(args[0])) {
            sendMenu(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if ("help".equals(sub)) {
            sendHelp(sender);
            return;
        }

        if ("status".equals(sub)) {
            sendBlock(sender, service.statusMessage());
            return;
        }

        if ("diagnostics".equals(sub) || "diag".equals(sub)) {
            sendBlock(sender, service.diagnosticsMessage());
            return;
        }

        if ("config".equals(sub)) {
            sendBlock(sender, service.configMessage());
            return;
        }

        if ("reload".equals(sub)) {
            service.reload();
            send(sender, "[Obsidian Watch] Config reloaded. Sync queued.");
            return;
        }

        if ("sync".equals(sub)) {
            service.syncAllAsync();
            send(sender, "[Obsidian Watch] Sync queued.");
            return;
        }

        if ("heartbeat".equals(sub)) {
            service.heartbeatAsync();
            send(sender, "[Obsidian Watch] Heartbeat queued.");
            return;
        }

        if ("check".equals(sub)) {
            if (args.length < 2) {
                send(sender, "Usage: /ow check <uuid|cached-username> [username]");
                return;
            }
            String username = args.length >= 3 ? args[2] : null;
            sendBlock(sender, service.checkMessage(args[1], username));
            return;
        }

        if ("testlog".equals(sub)) {
            if (args.length < 3) {
                send(sender, "Usage: /ow testlog <uuid|cached-username> <notify|kick|tempban|ban|ban_dry_run|exempted> [username]");
                return;
            }
            String username = args.length >= 4 ? args[3] : null;
            sendBlock(sender, service.testActionLog(args[1], args[2], username));
            return;
        }

        if ("exempt".equals(sub)) {
            handleExempt(sender, args);
            return;
        }

        if ("blocks".equals(sub) || "bans".equals(sub)) {
            sendBlock(sender, service.localBlocksMessage());
            return;
        }

        sendHelp(sender);
    }

    private void handleExempt(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Usage: /ow exempt <list|add|remove> [uuid]");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            sendBlock(sender, service.listExemptionsMessage());
            return;
        }

        if (args.length < 3) {
            send(sender, "Usage: /ow exempt " + action + " <uuid>");
            return;
        }

        if ("add".equals(action)) {
            sendBlock(sender, service.addExemption(args[2]));
            return;
        }

        if ("remove".equals(action) || "del".equals(action) || "delete".equals(action)) {
            sendBlock(sender, service.removeExemption(args[2]));
            return;
        }

        send(sender, "Usage: /ow exempt <list|add|remove> [uuid]");
    }

    private void sendMenu(CommandSender sender) {
        send(sender, "==== Obsidian Watch ====");
        send(sender, "/ow status - Show cache and sync status");
        send(sender, "/ow diagnostics - Show API/config diagnostics");
        send(sender, "/ow sync - Sync remote config and snapshot");
        send(sender, "/ow check <uuid|name> - Check cached entry");
        send(sender, "/ow exempt list/add/remove - Manage local exemptions");
        send(sender, "/ow blocks - Show proxy-side blocks");
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "Obsidian Watch commands:");
        send(sender, "/ow");
        send(sender, "/ow help");
        send(sender, "/ow status");
        send(sender, "/ow diagnostics");
        send(sender, "/ow config");
        send(sender, "/ow reload");
        send(sender, "/ow sync");
        send(sender, "/ow heartbeat");
        send(sender, "/ow check <uuid|cached-username> [username]");
        send(sender, "/ow testlog <uuid|cached-username> <action> [username]");
        send(sender, "/ow exempt list");
        send(sender, "/ow exempt add <uuid>");
        send(sender, "/ow exempt remove <uuid>");
        send(sender, "/ow blocks");
    }

    private void sendBlock(CommandSender sender, String block) {
        String[] lines = block == null ? new String[0] : block.split("\\r?\\n");
        for (String line : lines) {
            send(sender, line);
        }
    }

    private void send(CommandSender sender, String line) {
        sender.sendMessage(new TextComponent(line));
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("obsidianwatch.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 0) {
            return Arrays.asList("help", "status", "diagnostics", "config", "reload", "sync", "heartbeat", "check", "testlog", "exempt", "blocks");
        }

        if (args.length == 1) {
            return filter(args[0], Arrays.asList("help", "status", "diagnostics", "config", "reload", "sync", "heartbeat", "check", "testlog", "exempt", "blocks"));
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
