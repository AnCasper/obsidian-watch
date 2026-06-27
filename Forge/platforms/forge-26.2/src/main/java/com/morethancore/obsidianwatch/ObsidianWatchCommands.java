package com.morethancore.obsidianwatch;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class ObsidianWatchCommands {
    private static ObsidianWatchService service;

    private ObsidianWatchCommands() {
    }

    public static void setService(ObsidianWatchService service) {
        ObsidianWatchCommands.service = service;
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("ow")
                .requires(ObsidianWatchCommands::canUseOw)
                .executes(context -> {
                    context.getSource().sendSuccess(ObsidianWatchCommands::mainMenu, false);
                    return 1;
                })
                .then(Commands.literal("help")
                        .executes(context -> {
                            context.getSource().sendSuccess(ObsidianWatchCommands::mainMenu, false);
                            return 1;
                        })
                        .then(Commands.literal("config")
                                .executes(context -> {
                                    context.getSource().sendSuccess(ObsidianWatchCommands::configHelp, false);
                                    return 1;
                                }))
                        .then(Commands.literal("actions")
                                .executes(context -> {
                                    context.getSource().sendSuccess(ObsidianWatchCommands::actionsHelp, false);
                                    return 1;
                                }))
                        .then(Commands.literal("exempt")
                                .executes(context -> {
                                    context.getSource().sendSuccess(ObsidianWatchCommands::exemptHelp, false);
                                    return 1;
                                })))
                .then(Commands.literal("status")
                        .executes(context -> {
                            sendServiceMessage(context.getSource(), ObsidianWatchService::buildStatusMessage);
                            return 1;
                        }))
                .then(Commands.literal("diagnostics")
                        .executes(context -> {
                            sendServiceMessage(context.getSource(), ObsidianWatchService::buildDiagnosticsMessage);
                            return 1;
                        }))
                .then(Commands.literal("sync")
                        .executes(context -> {
                            if (service != null) {
                                service.syncAllAsync();
                                context.getSource().sendSuccess(() -> ObsidianWatchText.join(
                                        ObsidianWatchText.title("Obsidian Watch"),
                                        ObsidianWatchText.line("Sync", "config and snapshot queued", ChatFormatting.GREEN)
                                ), false);
                            } else {
                                sendUnavailable(context.getSource());
                            }
                            return 1;
                        }))
                .then(Commands.literal("heartbeat")
                        .executes(context -> {
                            if (service != null) {
                                service.sendHeartbeatAsync();
                                context.getSource().sendSuccess(() -> ObsidianWatchText.join(
                                        ObsidianWatchText.title("Obsidian Watch"),
                                        ObsidianWatchText.line("Heartbeat", "queued", ChatFormatting.GREEN)
                                ), false);
                            } else {
                                sendUnavailable(context.getSource());
                            }
                            return 1;
                        }))
                .then(Commands.literal("config")
                        .executes(context -> {
                            if (service != null) {
                                service.syncServerConfigAsync();
                                context.getSource().sendSuccess(() -> ObsidianWatchText.join(
                                        ObsidianWatchText.title("Obsidian Watch"),
                                        ObsidianWatchText.line("Config", "remote config sync queued", ChatFormatting.GREEN)
                                ), false);
                            } else {
                                sendUnavailable(context.getSource());
                            }
                            return 1;
                        }))
                .then(Commands.literal("check")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> {
                                    String player = StringArgumentType.getString(context, "player");
                                    sendCheckResult(context.getSource(), player, "");
                                    return 1;
                                })
                                .then(Commands.argument("username", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String player = StringArgumentType.getString(context, "player");
                                            String username = StringArgumentType.getString(context, "username");
                                            sendCheckResult(context.getSource(), player, username);
                                            return 1;
                                        }))))
                .then(Commands.literal("testlog")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .executes(context -> {
                                            String player = StringArgumentType.getString(context, "player");
                                            String action = StringArgumentType.getString(context, "action");
                                            sendTestLogResult(context.getSource(), player, action, "");
                                            return 1;
                                        })
                                        .then(Commands.argument("username", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String player = StringArgumentType.getString(context, "player");
                                                    String action = StringArgumentType.getString(context, "action");
                                                    String username = StringArgumentType.getString(context, "username");
                                                    sendTestLogResult(context.getSource(), player, action, username);
                                                    return 1;
                                                })))))
                .then(Commands.literal("exempt")
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    sendExemptionList(context.getSource());
                                    return 1;
                                }))
                        .then(Commands.literal("add")
                                .then(Commands.argument("uuid", StringArgumentType.word())
                                        .executes(context -> {
                                            String uuid = StringArgumentType.getString(context, "uuid");
                                            sendExemptionAdd(context.getSource(), uuid);
                                            return 1;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("uuid", StringArgumentType.word())
                                        .executes(context -> {
                                            String uuid = StringArgumentType.getString(context, "uuid");
                                            sendExemptionRemove(context.getSource(), uuid);
                                            return 1;
                                        }))))
        );
    }


private static boolean canUseOw(CommandSourceStack source) {
    try {
        ServerPlayer player = source.getPlayer();
        return source.getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    } catch (Exception ignored) {
        return true;
    }
}

    private static Component mainMenu() {
        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch Commands"),
                ObsidianWatchText.command("/ow status", "health summary"),
                ObsidianWatchText.command("/ow check <uuid|name>", "check the local synced snapshot"),
                ObsidianWatchText.command("/ow sync", "refresh config and snapshot"),
                ObsidianWatchText.command("/ow config", "refresh remote server settings"),
                ObsidianWatchText.command("/ow diagnostics", "API, config, cache, and safety state"),
                ObsidianWatchText.command("/ow exempt add|remove|list", "manage local exemptions"),
                ObsidianWatchText.command("/ow testlog <uuid|name> <action> [username]", "send a diagnostic action log"),
                ObsidianWatchText.command("/ow help config|actions|exempt", "show focused help")
        );
    }

    private static Component configHelp() {
        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch: Config Help"),
                ObsidianWatchText.line("Local config", "config/obsidian_watch-server.toml"),
                ObsidianWatchText.line("Remote config", "Owner > Servers > Config"),
                ObsidianWatchText.command("/ow config", "pull remote server settings"),
                ObsidianWatchText.command("/ow sync", "pull config and snapshot"),
                ObsidianWatchText.command("/ow diagnostics", "verify API key, URL, cache, actions, and safety")
        );
    }

    private static Component actionsHelp() {
        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch: Action Help"),
                ObsidianWatchText.line("Watchlist", "NONE, NOTIFY, KICK"),
                ObsidianWatchText.line("Confirmed", "NONE, NOTIFY, KICK, TEMPBAN, BAN_DRY_RUN, BAN"),
                ObsidianWatchText.line("Recommended", "watchlist NOTIFY, confirmed BAN_DRY_RUN or TEMPBAN"),
                ObsidianWatchText.line("Permanent bans", "require Allow real permanent bans on the website")
        );
    }

    private static Component exemptHelp() {
        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch: Exemption Help"),
                ObsidianWatchText.command("/ow exempt list", "show local exemptions"),
                ObsidianWatchText.command("/ow exempt add <uuid>", "skip join actions for a player UUID"),
                ObsidianWatchText.command("/ow exempt remove <uuid>", "remove a local exemption"),
                ObsidianWatchText.line("Stored in", "localExemptUuids in the server config")
        );
    }

    private static void sendServiceMessage(CommandSourceStack source, ServiceComponentSupplier supplier) {
        if (service == null) {
            sendUnavailable(source);
            return;
        }
        source.sendSuccess(() -> supplier.get(service), false);
    }

    private static void sendUnavailable(CommandSourceStack source) {
        source.sendFailure(ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch"),
                ObsidianWatchText.line("Status", "service unavailable", ChatFormatting.RED)
        ));
    }

    private static void sendExemptionAdd(CommandSourceStack source, String uuid) {
        if (service == null) {
            sendUnavailable(source);
            return;
        }
        source.sendSuccess(() -> service.addLocalExemption(uuid), false);
    }

    private static void sendExemptionRemove(CommandSourceStack source, String uuid) {
        if (service == null) {
            sendUnavailable(source);
            return;
        }
        source.sendSuccess(() -> service.removeLocalExemption(uuid), false);
    }

    private static void sendExemptionList(CommandSourceStack source) {
        if (service == null) {
            sendUnavailable(source);
            return;
        }
        source.sendSuccess(service::listLocalExemptions, false);
    }

    private static void sendTestLogResult(CommandSourceStack source, String player, String action, String username) {
        if (service == null) {
            sendUnavailable(source);
            return;
        }
        source.sendSuccess(() -> service.queueTestActionLog(player, action, username), false);
    }

    private static void sendCheckResult(CommandSourceStack source, String player, String username) {
        if (service == null) {
            sendUnavailable(source);
            return;
        }
        source.sendSuccess(() -> service.buildCheckMessage(player, username), false);
    }

    @FunctionalInterface
    private interface ServiceComponentSupplier {
        Component get(ObsidianWatchService service);
    }
}
