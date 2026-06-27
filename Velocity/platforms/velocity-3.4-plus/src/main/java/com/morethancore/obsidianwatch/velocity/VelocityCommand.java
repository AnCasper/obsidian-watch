package com.morethancore.obsidianwatch.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class VelocityCommand implements SimpleCommand {
    private final ObsidianWatchVelocityService service;

    public VelocityCommand(ObsidianWatchVelocityService service) {
        this.service = service;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("obsidianwatch.admin")) {
            source.sendMessage(Component.text("You do not have permission to use Obsidian Watch.", NamedTextColor.RED));
            return;
        }

        if (args.length == 0 || "menu".equalsIgnoreCase(args[0])) {
            sendMenu(source);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if ("help".equals(sub)) {
            sendHelp(source);
            return;
        }

        if ("status".equals(sub)) {
            sendBlock(source, service.statusMessage());
            return;
        }

        if ("diagnostics".equals(sub) || "diag".equals(sub)) {
            sendBlock(source, service.diagnosticsMessage());
            return;
        }

        if ("config".equals(sub)) {
            sendBlock(source, service.configMessage());
            return;
        }

        if ("reload".equals(sub)) {
            service.reload();
            source.sendMessage(Component.text("[Obsidian Watch] Config reloaded. Sync queued.", NamedTextColor.GREEN));
            return;
        }

        if ("sync".equals(sub)) {
            service.syncAllAsync();
            source.sendMessage(Component.text("[Obsidian Watch] Sync queued.", NamedTextColor.GREEN));
            return;
        }

        if ("heartbeat".equals(sub)) {
            service.heartbeatAsync();
            source.sendMessage(Component.text("[Obsidian Watch] Heartbeat queued.", NamedTextColor.GREEN));
            return;
        }

        if ("check".equals(sub)) {
            if (args.length < 2) {
                source.sendMessage(Component.text("Usage: /ow check <uuid|cached-username> [username]", NamedTextColor.RED));
                return;
            }
            String username = args.length >= 3 ? args[2] : null;
            sendBlock(source, service.checkMessage(args[1], username));
            return;
        }

        if ("testlog".equals(sub)) {
            if (args.length < 3) {
                source.sendMessage(Component.text("Usage: /ow testlog <uuid|cached-username> <notify|kick|tempban|ban|ban_dry_run|exempted> [username]", NamedTextColor.RED));
                return;
            }
            String username = args.length >= 4 ? args[3] : null;
            sendBlock(source, service.testActionLog(args[1], args[2], username));
            return;
        }

        if ("exempt".equals(sub)) {
            handleExempt(source, args);
            return;
        }

        if ("blocks".equals(sub) || "bans".equals(sub)) {
            sendBlock(source, service.localBlocksMessage());
            return;
        }

        sendHelp(source);
    }

    private void handleExempt(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /ow exempt <list|add|remove> [uuid]", NamedTextColor.RED));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            sendBlock(source, service.listExemptionsMessage());
            return;
        }

        if (args.length < 3) {
            source.sendMessage(Component.text("Usage: /ow exempt " + action + " <uuid>", NamedTextColor.RED));
            return;
        }

        if ("add".equals(action)) {
            sendBlock(source, service.addExemption(args[2]));
            return;
        }

        if ("remove".equals(action) || "del".equals(action) || "delete".equals(action)) {
            sendBlock(source, service.removeExemption(args[2]));
            return;
        }

        source.sendMessage(Component.text("Usage: /ow exempt <list|add|remove> [uuid]", NamedTextColor.RED));
    }

    private void sendMenu(CommandSource source) {
        source.sendMessage(Component.text("==== Obsidian Watch ====", NamedTextColor.AQUA));
        source.sendMessage(Component.text("/ow status - Show cache and sync status", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow diagnostics - Show API/config diagnostics", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow sync - Sync remote config and snapshot", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow check <uuid|name> - Check cached entry", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow exempt list/add/remove - Manage local exemptions", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow blocks - Show proxy-side blocks", NamedTextColor.GRAY));
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(Component.text("Obsidian Watch commands:", NamedTextColor.AQUA));
        source.sendMessage(Component.text("/ow", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow help", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow status", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow diagnostics", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow config", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow reload", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow sync", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow heartbeat", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow check <uuid|cached-username> [username]", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow testlog <uuid|cached-username> <action> [username]", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow exempt list", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow exempt add <uuid>", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow exempt remove <uuid>", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/ow blocks", NamedTextColor.GRAY));
    }

    private void sendBlock(CommandSource source, String block) {
        String[] lines = block == null ? new String[0] : block.split("\\r?\\n");
        for (String line : lines) {
            source.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission("obsidianwatch.admin")) {
            return Collections.emptyList();
        }

        String[] args = invocation.arguments();

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
