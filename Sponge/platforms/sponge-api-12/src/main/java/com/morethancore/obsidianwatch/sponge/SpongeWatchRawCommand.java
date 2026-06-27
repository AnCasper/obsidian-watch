package com.morethancore.obsidianwatch.sponge;

import net.kyori.adventure.text.Component;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandCompletion;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.parameter.ArgumentReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class SpongeWatchRawCommand implements Command.Raw {
    private final ObsidianWatchSpongeService service;
    private final Component usage = Component.text("/ow <help|status|diagnostics|config|reload|sync|heartbeat|check|testlog|exempt|blocks>");

    public SpongeWatchRawCommand(ObsidianWatchSpongeService service) {
        this.service = service;
    }

    @Override
    public CommandResult process(CommandCause cause, ArgumentReader.Mutable arguments) throws CommandException {
        String raw = arguments.remaining() == null ? "" : arguments.remaining().trim();
        String[] args = raw.isEmpty() ? new String[0] : raw.split("\\s+");

        if (args.length == 0 || "menu".equalsIgnoreCase(args[0])) {
            sendMenu(cause);
            return CommandResult.success();
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if ("help".equals(sub)) {
            sendHelp(cause);
            return CommandResult.success();
        }

        if ("status".equals(sub)) {
            sendBlock(cause, service.statusMessage());
            return CommandResult.success();
        }

        if ("diagnostics".equals(sub) || "diag".equals(sub)) {
            sendBlock(cause, service.diagnosticsMessage());
            return CommandResult.success();
        }

        if ("config".equals(sub)) {
            sendBlock(cause, service.configMessage());
            return CommandResult.success();
        }

        if ("reload".equals(sub)) {
            service.reload();
            send(cause, "[Obsidian Watch] Config reloaded. Sync queued.");
            return CommandResult.success();
        }

        if ("sync".equals(sub)) {
            service.syncAllAsync();
            send(cause, "[Obsidian Watch] Sync queued.");
            return CommandResult.success();
        }

        if ("heartbeat".equals(sub)) {
            service.heartbeatAsync();
            send(cause, "[Obsidian Watch] Heartbeat queued.");
            return CommandResult.success();
        }

        if ("check".equals(sub)) {
            if (args.length < 2) {
                send(cause, "Usage: /ow check <uuid|cached-username> [username]");
                return CommandResult.success();
            }
            String username = args.length >= 3 ? args[2] : null;
            sendBlock(cause, service.checkMessage(args[1], username));
            return CommandResult.success();
        }

        if ("testlog".equals(sub)) {
            if (args.length < 3) {
                send(cause, "Usage: /ow testlog <uuid|cached-username> <notify|kick|tempban|ban|ban_dry_run|exempted> [username]");
                return CommandResult.success();
            }
            String username = args.length >= 4 ? args[3] : null;
            sendBlock(cause, service.testActionLog(args[1], args[2], username));
            return CommandResult.success();
        }

        if ("exempt".equals(sub)) {
            handleExempt(cause, args);
            return CommandResult.success();
        }

        if ("blocks".equals(sub) || "bans".equals(sub)) {
            sendBlock(cause, service.localBlocksMessage());
            return CommandResult.success();
        }

        sendHelp(cause);
        return CommandResult.success();
    }

    private void handleExempt(CommandCause cause, String[] args) {
        if (args.length < 2) {
            send(cause, "Usage: /ow exempt <list|add|remove> [uuid]");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            sendBlock(cause, service.listExemptionsMessage());
            return;
        }

        if (args.length < 3) {
            send(cause, "Usage: /ow exempt " + action + " <uuid>");
            return;
        }

        if ("add".equals(action)) {
            sendBlock(cause, service.addExemption(args[2]));
            return;
        }

        if ("remove".equals(action) || "del".equals(action) || "delete".equals(action)) {
            sendBlock(cause, service.removeExemption(args[2]));
            return;
        }

        send(cause, "Usage: /ow exempt <list|add|remove> [uuid]");
    }

    private void sendMenu(CommandCause cause) {
        send(cause, "==== Obsidian Watch ====");
        send(cause, "/ow status - Show cache and sync status");
        send(cause, "/ow diagnostics - Show API/config diagnostics");
        send(cause, "/ow sync - Sync remote config and snapshot");
        send(cause, "/ow check <uuid|name> - Check cached entry");
        send(cause, "/ow exempt list/add/remove - Manage local exemptions");
        send(cause, "/ow blocks - Show Sponge-side UUID blocks");
    }

    private void sendHelp(CommandCause cause) {
        send(cause, "Obsidian Watch commands:");
        send(cause, "/ow");
        send(cause, "/ow help");
        send(cause, "/ow status");
        send(cause, "/ow diagnostics");
        send(cause, "/ow config");
        send(cause, "/ow reload");
        send(cause, "/ow sync");
        send(cause, "/ow heartbeat");
        send(cause, "/ow check <uuid|cached-username> [username]");
        send(cause, "/ow testlog <uuid|cached-username> <action> [username]");
        send(cause, "/ow exempt list");
        send(cause, "/ow exempt add <uuid>");
        send(cause, "/ow exempt remove <uuid>");
        send(cause, "/ow blocks");
    }

    private void sendBlock(CommandCause cause, String block) {
        String[] lines = block == null ? new String[0] : block.split("\\r?\\n");
        for (String line : lines) {
            send(cause, line);
        }
    }

    private void send(CommandCause cause, String line) {
        cause.audience().sendMessage(Component.text(line));
    }

    @Override
    public List<CommandCompletion> complete(CommandCause cause, ArgumentReader.Mutable arguments) throws CommandException {
        String raw = arguments.remaining() == null ? "" : arguments.remaining().trim();
        String[] args = raw.isEmpty() ? new String[0] : raw.split("\\s+");

        if (args.length <= 1) {
            String token = args.length == 0 ? "" : args[0];
            return completions(token, Arrays.asList("help", "status", "diagnostics", "config", "reload", "sync", "heartbeat", "check", "testlog", "exempt", "blocks"));
        }

        if (args.length == 2 && "exempt".equalsIgnoreCase(args[0])) {
            return completions(args[1], Arrays.asList("list", "add", "remove"));
        }

        if (args.length == 3 && "testlog".equalsIgnoreCase(args[0])) {
            return completions(args[2], Arrays.asList("notify", "kick", "tempban", "ban", "ban_dry_run", "exempted"));
        }

        return Collections.emptyList();
    }

    private List<CommandCompletion> completions(String token, List<String> options) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<CommandCompletion> result = new ArrayList<CommandCompletion>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(CommandCompletion.of(option));
            }
        }
        return result;
    }

    @Override
    public boolean canExecute(CommandCause cause) {
        return cause.hasPermission("obsidianwatch.admin");
    }

    @Override
    public Optional<Component> shortDescription(CommandCause cause) {
        return Optional.of(Component.text("Obsidian Watch admin command."));
    }

    @Override
    public Optional<Component> extendedDescription(CommandCause cause) {
        return Optional.empty();
    }

    @Override
    public Component usage(CommandCause cause) {
        return this.usage;
    }
}
