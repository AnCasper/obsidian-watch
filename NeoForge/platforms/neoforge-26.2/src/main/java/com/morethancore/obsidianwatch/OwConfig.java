package com.morethancore.obsidianwatch;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OwConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> API_KEY;
    public static final ModConfigSpec.IntValue SYNC_INTERVAL_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> SERVER_BRAND;
    public static final ModConfigSpec.ConfigValue<String> SERVER_NAME_OVERRIDE;
    public static final ModConfigSpec.BooleanValue NOTIFY_OPS;
    public static final ModConfigSpec.EnumValue<ConfirmedAction> CONFIRMED_ACTION;
    public static final ModConfigSpec.EnumValue<WatchlistAction> WATCHLIST_ACTION;
    public static final ModConfigSpec.BooleanValue POST_ACTION_LOGS;
    public static final ModConfigSpec.BooleanValue ALLOW_REAL_BANS;
    public static final ModConfigSpec.IntValue JOIN_ACTION_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue TEMPBAN_DURATION_SECONDS;
    public static final ModConfigSpec.BooleanValue EXEMPT_OPS;
    public static final ModConfigSpec.BooleanValue EXEMPT_CREATIVE;
    public static final ModConfigSpec.ConfigValue<String> LOCAL_EXEMPT_UUIDS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("api");
        ENABLED = BUILDER
                .comment("Enable Obsidian Watch integration.")
                .define("enabled", false);
        BASE_URL = BUILDER
                .comment("Base URL of the Obsidian Watch web app. Do not include a trailing slash.")
                .define("baseUrl", "http://127.0.0.1:8080");
        API_KEY = BUILDER
                .comment("Server API key from Owner > Servers. Keep this private.")
                .define("apiKey", "");
        SYNC_INTERVAL_SECONDS = BUILDER
                .comment("How often to send heartbeat and refresh the list snapshot. Minimum 60 seconds.")
                .defineInRange("syncIntervalSeconds", 300, 60, 3600);
        BUILDER.pop();

        BUILDER.push("server");
        SERVER_BRAND = BUILDER
                .comment("Loader/platform label sent to the website activity page.")
                .define("serverBrand", "NeoForge");
        SERVER_NAME_OVERRIDE = BUILDER
                .comment("Optional public server name override. Empty uses Minecraft's server name when available.")
                .define("serverNameOverride", "");
        BUILDER.pop();

        BUILDER.push("actions");
        NOTIFY_OPS = BUILDER
                .comment("Notify online operators when a listed player joins.")
                .define("notifyOps", true);
        WATCHLIST_ACTION = BUILDER
                .comment("Action for watchlisted players. Use NOTIFY for MVP safety.")
                .defineEnum("watchlistAction", WatchlistAction.NOTIFY);
        CONFIRMED_ACTION = BUILDER
                .comment("Action for confirmed players. Keep NOTIFY until you trust the list and appeals process.")
                .defineEnum("confirmedAction", ConfirmedAction.NOTIFY);
        POST_ACTION_LOGS = BUILDER
                .comment("Post notify/kick/ban events back to the website activity log.")
                .define("postActionLogs", true);
        ALLOW_REAL_BANS = BUILDER
                .comment("Allow real permanent bans. If false, BAN becomes BAN_DRY_RUN even when remote config asks for BAN.")
                .define("allowRealBans", false);
        BUILDER.pop();

        BUILDER.push("safety");
        JOIN_ACTION_COOLDOWN_SECONDS = BUILDER
                .comment("Per-player cooldown for join notifications/enforcement/action logs. Prevents relog spam. Set 0 to disable.")
                .defineInRange("joinActionCooldownSeconds", 300, 0, 86400);
        TEMPBAN_DURATION_SECONDS = BUILDER
                .comment("Duration for temporary bans in seconds. Used only by TEMPBAN.")
                .defineInRange("tempbanDurationSeconds", 86400, 60, 2592000);
        EXEMPT_OPS = BUILDER
                .comment("Skip Obsidian Watch join actions for online operators. Default false keeps staff account compromise visible.")
                .define("exemptOps", false);
        EXEMPT_CREATIVE = BUILDER
                .comment("Skip Obsidian Watch join actions for players currently in creative mode.")
                .define("exemptCreative", false);
        LOCAL_EXEMPT_UUIDS = BUILDER
                .comment("Comma-separated Java UUIDs exempt from join actions. Managed by /ow exempt add/remove.")
                .define("localExemptUuids", "");
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private OwConfig() {
    }

    public enum WatchlistAction {
        NONE,
        NOTIFY,
        KICK
    }

    public enum ConfirmedAction {
        NONE,
        NOTIFY,
        KICK,
        TEMPBAN,
        BAN_DRY_RUN,
        BAN
    }
}
