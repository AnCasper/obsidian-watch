package com.morethancore.obsidianwatch.fabric;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class FabricWatchCommands {
    private FabricWatchCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, ObsidianWatchFabricService service) {
        dispatcher.register(literal("ow")
                .requires(FabricWatchCommands::canUse)
                .executes(context -> {
                    sendMenu(context.getSource());
                    return 1;
                })
                .then(literal("help").executes(context -> {
                    sendHelp(context.getSource());
                    return 1;
                }))
                .then(literal("status").executes(context -> {
                    sendBlock(context.getSource(), service.statusMessage());
                    return 1;
                }))
                .then(literal("diagnostics").executes(context -> {
                    sendBlock(context.getSource(), service.diagnosticsMessage());
                    return 1;
                }))
                .then(literal("diag").executes(context -> {
                    sendBlock(context.getSource(), service.diagnosticsMessage());
                    return 1;
                }))
                .then(literal("config").executes(context -> {
                    sendBlock(context.getSource(), service.configMessage());
                    return 1;
                }))
                .then(literal("reload").executes(context -> {
                    service.reload();
                    context.getSource().sendFeedback(() -> Text.literal("[Obsidian Watch] Config reloaded. Sync queued."), false);
                    return 1;
                }))
                .then(literal("sync").executes(context -> {
                    service.syncAllAsync();
                    context.getSource().sendFeedback(() -> Text.literal("[Obsidian Watch] Sync queued."), false);
                    return 1;
                }))
                .then(literal("heartbeat").executes(context -> {
                    service.heartbeatAsync();
                    context.getSource().sendFeedback(() -> Text.literal("[Obsidian Watch] Heartbeat queued."), false);
                    return 1;
                }))
                .then(literal("check")
                        .then(argument("uuid_or_name", word()).executes(context -> {
                            sendBlock(context.getSource(), service.checkMessage(getString(context, "uuid_or_name"), null));
                            return 1;
                        })
                        .then(argument("username", word()).executes(context -> {
                            sendBlock(context.getSource(), service.checkMessage(getString(context, "uuid_or_name"), getString(context, "username")));
                            return 1;
                        }))))
                .then(literal("testlog")
                        .then(argument("uuid_or_name", word())
                                .then(argument("action", word()).executes(context -> {
                                    sendBlock(context.getSource(), service.testActionLog(getString(context, "uuid_or_name"), getString(context, "action"), null));
                                    return 1;
                                })
                                .then(argument("username", word()).executes(context -> {
                                    sendBlock(context.getSource(), service.testActionLog(getString(context, "uuid_or_name"), getString(context, "action"), getString(context, "username")));
                                    return 1;
                                })))))
                .then(literal("exempt")
                        .then(literal("list").executes(context -> {
                            sendBlock(context.getSource(), service.listExemptionsMessage());
                            return 1;
                        }))
                        .then(literal("add").then(argument("uuid", word()).executes(context -> {
                            sendBlock(context.getSource(), service.addExemption(getString(context, "uuid")));
                            return 1;
                        })))
                        .then(literal("remove").then(argument("uuid", word()).executes(context -> {
                            sendBlock(context.getSource(), service.removeExemption(getString(context, "uuid")));
                            return 1;
                        })))));
    }

    private static boolean canUse(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }

    private static void sendMenu(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("==== Obsidian Watch ===="), false);
        source.sendFeedback(() -> Text.literal("/ow status - Show cache and sync status"), false);
        source.sendFeedback(() -> Text.literal("/ow diagnostics - Show API/config diagnostics"), false);
        source.sendFeedback(() -> Text.literal("/ow sync - Sync remote config and snapshot"), false);
        source.sendFeedback(() -> Text.literal("/ow check <uuid|name> - Check cached entry"), false);
        source.sendFeedback(() -> Text.literal("/ow exempt list/add/remove - Manage local exemptions"), false);
    }

    private static void sendHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Obsidian Watch commands:"), false);
        source.sendFeedback(() -> Text.literal("/ow"), false);
        source.sendFeedback(() -> Text.literal("/ow help"), false);
        source.sendFeedback(() -> Text.literal("/ow status"), false);
        source.sendFeedback(() -> Text.literal("/ow diagnostics"), false);
        source.sendFeedback(() -> Text.literal("/ow config"), false);
        source.sendFeedback(() -> Text.literal("/ow reload"), false);
        source.sendFeedback(() -> Text.literal("/ow sync"), false);
        source.sendFeedback(() -> Text.literal("/ow heartbeat"), false);
        source.sendFeedback(() -> Text.literal("/ow check <uuid|cached-username> [username]"), false);
        source.sendFeedback(() -> Text.literal("/ow testlog <uuid|cached-username> <action> [username]"), false);
        source.sendFeedback(() -> Text.literal("/ow exempt list"), false);
        source.sendFeedback(() -> Text.literal("/ow exempt add <uuid>"), false);
        source.sendFeedback(() -> Text.literal("/ow exempt remove <uuid>"), false);
    }

    private static void sendBlock(ServerCommandSource source, String block) {
        String[] lines = block == null ? new String[0] : block.split("\\r?\\n");
        for (String line : lines) {
            source.sendFeedback(() -> Text.literal(line), false);
        }
    }
}
