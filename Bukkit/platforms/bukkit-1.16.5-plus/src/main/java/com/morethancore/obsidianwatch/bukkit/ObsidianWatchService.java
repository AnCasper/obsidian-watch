package com.morethancore.obsidianwatch.bukkit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ObsidianWatchService {
    private static final Gson GSON = new Gson();
    private static final String PLUGIN_VERSION = "1.0.0-pre1+bukkit.1.16.5-plus";
    private static final String PLATFORM_VERSION = "bukkit-1.16.5-plus";

    private final JavaPlugin plugin;
    private final Map<UUID, WatchEntry> cachedEntries = new ConcurrentHashMap<UUID, WatchEntry>();
    private final Map<UUID, Instant> recentJoinActions = new ConcurrentHashMap<UUID, Instant>();
    private final Set<UUID> localExemptions = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> disconnectsInProgress = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    private volatile String lastStatus = "Not started.";
    private volatile Instant lastHeartbeat;
    private volatile Instant lastSync;
    private volatile Instant lastConfigSync;
    private volatile RemoteSettings remoteSettings;

    private int repeatingTaskId = -1;

    public ObsidianWatchService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        loadExemptionsFromConfig();
        remoteSettings = RemoteSettings.fromLocal(plugin.getConfig());

        if (!plugin.getConfig().getBoolean("enabled", true)) {
            lastStatus = "Disabled in config.";
            return;
        }

        if (isBlank(apiKey())) {
            lastStatus = "Enabled, but apiKey is blank.";
            return;
        }

        int intervalTicks = Math.max(60, plugin.getConfig().getInt("syncIntervalSeconds", 120)) * 20;
        repeatingTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                syncServerConfig();
                sendHeartbeat();
                syncSnapshot();
            }
        }, 100L, intervalTicks).getTaskId();

        lastStatus = "Started. First sync scheduled.";
    }

    public void stop() {
        if (repeatingTaskId != -1) {
            Bukkit.getScheduler().cancelTask(repeatingTaskId);
            repeatingTaskId = -1;
        }
        cachedEntries.clear();
        recentJoinActions.clear();
        localExemptions.clear();
        disconnectsInProgress.clear();
        remoteSettings = null;
        lastStatus = "Stopped.";
    }

    public void reload() {
        stop();
        start();
        syncAllAsync();
    }

    public void handleJoin(final Player player) {
        if (!plugin.getConfig().getBoolean("enabled", true)) {
            return;
        }

        final WatchEntry entry = cachedEntries.get(player.getUniqueId());
        if (entry == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                handleListedPlayer(player, entry);
            }
        });
    }

    public void syncAllAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                syncServerConfig();
                sendHeartbeat();
                syncSnapshot();
            }
        });
    }

    public void heartbeatAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                sendHeartbeat();
            }
        });
    }

    public String statusMessage() {
        return title("Obsidian Watch") + "\n"
                + line("Status", healthLabel()) + "\n"
                + line("Server", resolveServerName()) + "\n"
                + line("Entries", String.valueOf(cachedEntries.size())) + "\n"
                + line("Exemptions", String.valueOf(localExemptions.size())) + "\n"
                + line("Config", ago(lastConfigSync)) + "\n"
                + line("Snapshot", ago(lastSync)) + "\n"
                + line("Heartbeat", ago(lastHeartbeat)) + "\n"
                + line("Last", lastStatus);
    }

    public String diagnosticsMessage() {
        RemoteSettings settings = currentSettings();
        return title("Obsidian Watch Diagnostics") + "\n"
                + section("API") + "\n"
                + line("Enabled", yesNo(plugin.getConfig().getBoolean("enabled", true))) + "\n"
                + line("Base URL", sanitizedBaseUrl()) + "\n"
                + line("API key", isBlank(apiKey()) ? "missing" : "configured") + "\n"
                + section("Cache") + "\n"
                + line("Entries", String.valueOf(cachedEntries.size())) + "\n"
                + line("Local exemptions", String.valueOf(localExemptions.size())) + "\n"
                + section("Remote config") + "\n"
                + line("Loaded", settings.loaded ? "yes" : "local fallback") + "\n"
                + line("Watchlist action", settings.watchlistAction) + "\n"
                + line("Confirmed action", effectiveConfirmedAction(settings)) + "\n"
                + line("Tempban duration", formatDuration(settings.tempbanDurationSeconds)) + "\n"
                + line("Real bans", settings.allowRealBans ? "enabled" : "blocked") + "\n"
                + line("Last status", lastStatus);
    }

    public String configMessage() {
        RemoteSettings settings = currentSettings();
        return title("Obsidian Watch Config") + "\n"
                + line("Local enabled", yesNo(plugin.getConfig().getBoolean("enabled", true))) + "\n"
                + line("Server brand", plugin.getConfig().getString("serverBrand", "bukkit")) + "\n"
                + line("Server name", resolveServerName()) + "\n"
                + line("Sync interval", plugin.getConfig().getInt("syncIntervalSeconds", 120) + "s") + "\n"
                + line("Watchlist action", settings.watchlistAction) + "\n"
                + line("Confirmed action", effectiveConfirmedAction(settings)) + "\n"
                + line("Notify ops", yesNo(settings.notifyOps)) + "\n"
                + line("Post action logs", yesNo(settings.postActionLogs)) + "\n"
                + line("Real bans", settings.allowRealBans ? "enabled" : "blocked");
    }

    public String checkMessage(String playerRaw, String usernameRaw) {
        PlayerLookup lookup = resolvePlayerLookup(playerRaw);
        if (lookup.error != null) {
            return title("Obsidian Watch") + "\n" + line("Check", lookup.error);
        }

        String displayName = isBlank(usernameRaw) ? lookup.displayName : usernameRaw.trim();
        if (lookup.entry == null) {
            return title("Obsidian Watch: No Match") + "\n"
                    + line("Player", displayName) + "\n"
                    + line("UUID", lookup.uuid.toString()) + "\n"
                    + line("Result", "not present in local snapshot");
        }

        WatchEntry entry = lookup.entry;
        return title("Obsidian Watch: Match") + "\n"
                + line("Player", displayName) + "\n"
                + line("UUID", lookup.uuid.toString()) + "\n"
                + line("List", entry.normalizedListType()) + "\n"
                + line("Category", entry.category()) + "\n"
                + line("Severity", entry.severity()) + "\n"
                + line("Action", configuredActionFor(entry)) + "\n"
                + line("Reason", buildReason(entry));
    }

    public String testActionLog(String playerRaw, String actionRaw, String usernameRaw) {
        PlayerLookup lookup = resolvePlayerLookup(playerRaw);
        if (lookup.error != null) {
            return title("Obsidian Watch: Test Action Log") + "\n" + line("Result", lookup.error);
        }

        String action = actionRaw == null ? "" : actionRaw.trim().toLowerCase(Locale.ROOT);
        if (!isAllowedDiagnosticAction(action)) {
            return title("Obsidian Watch: Test Action Log") + "\n"
                    + line("Result", "invalid action") + "\n"
                    + line("Allowed", "notify, kick, tempban, ban, ban_dry_run, exempted");
        }

        String username = isBlank(usernameRaw) ? lookup.displayName : usernameRaw.trim();
        String listType = lookup.entry == null ? "diagnostic" : lookup.entry.normalizedListType();
        final JsonObject payload = new JsonObject();
        payload.addProperty("java_uuid", lookup.uuid.toString());
        payload.addProperty("username", username);
        payload.addProperty("action", action);
        payload.addProperty("reason", "Manual Bukkit/Spigot/Paper diagnostic action log from /ow testlog.");
        payload.addProperty("list_type", listType);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    postJson("/api/v1/mod/action-log", payload);
                    lastStatus = "Test action log accepted.";
                } catch (Exception exception) {
                    lastStatus = "Test action log failed: " + exception.getMessage();
                }
            }
        });

        return title("Obsidian Watch: Test Action Log") + "\n"
                + line("Player", username) + "\n"
                + line("UUID", lookup.uuid.toString()) + "\n"
                + line("Action", action.toUpperCase(Locale.ROOT)) + "\n"
                + line("State", "queued");
    }

    public String addExemption(String uuidRaw) {
        UUID uuid = parseUuid(uuidRaw);
        if (uuid == null) {
            return title("Obsidian Watch") + "\n" + line("Exemption", "invalid Java UUID");
        }
        boolean added = localExemptions.add(uuid);
        saveExemptionsToConfig();
        return title("Obsidian Watch: Local Exemption") + "\n"
                + line("UUID", uuid.toString()) + "\n"
                + line("State", added ? "added" : "already exempt");
    }

    public String removeExemption(String uuidRaw) {
        UUID uuid = parseUuid(uuidRaw);
        if (uuid == null) {
            return title("Obsidian Watch") + "\n" + line("Exemption", "invalid Java UUID");
        }
        boolean removed = localExemptions.remove(uuid);
        saveExemptionsToConfig();
        return title("Obsidian Watch: Local Exemption") + "\n"
                + line("UUID", uuid.toString()) + "\n"
                + line("State", removed ? "removed" : "not exempt");
    }

    public String listExemptionsMessage() {
        loadExemptionsFromConfig();
        if (localExemptions.isEmpty()) {
            return title("Obsidian Watch: Local Exemptions") + "\n"
                    + line("Count", "0") + "\n"
                    + line("Result", "no local exemptions configured");
        }

        ArrayList<String> list = new ArrayList<String>();
        for (UUID uuid : localExemptions) {
            list.add(uuid.toString());
        }
        Collections.sort(list);

        StringBuilder builder = new StringBuilder();
        builder.append(title("Obsidian Watch: Local Exemptions")).append("\\n");
        builder.append(line("Count", String.valueOf(list.size()))).append("\\n");
        for (String uuid : list) {
            builder.append(ChatColor.GRAY).append(uuid).append("\\n");
        }
        return builder.toString();
    }

    private void handleListedPlayer(final Player player, WatchEntry entry) {
        String reason = buildReason(entry);

        if (isPlayerExempt(player)) {
            if (markJoinActionIfAllowed(player.getUniqueId())) {
                postActionLog(player, entry, "exempted", "Local Obsidian Watch exemption skipped configured action: " + reason);
            }
            return;
        }

        if (!markJoinActionIfAllowed(player.getUniqueId())) {
            return;
        }

        RemoteSettings settings = currentSettings();

        if (entry.isWatchlist()) {
            String action = normalizeAction(settings.watchlistAction);
            if ("NONE".equals(action)) {
                return;
            }
            notifyOps(player, entry, reason);
            postActionLog(player, entry, "KICK".equals(action) ? "kick" : "notify", reason);
            if ("KICK".equals(action)) {
                disconnectPlayer(player.getUniqueId(), buildEnforcementMessage(player, entry, "kick"));
            }
            return;
        }

        if (entry.isConfirmed()) {
            String action = normalizeAction(effectiveConfirmedAction(settings));
            if ("NONE".equals(action)) {
                return;
            }
            notifyOps(player, entry, reason);

            if ("NOTIFY".equals(action)) {
                postActionLog(player, entry, "notify", reason);
            } else if ("KICK".equals(action)) {
                postActionLog(player, entry, "kick", reason);
                disconnectPlayer(player.getUniqueId(), buildEnforcementMessage(player, entry, "kick"));
            } else if ("TEMPBAN".equals(action)) {
                postActionLog(player, entry, "tempban", reason + " Temporary ban duration: " + formatDuration(settings.tempbanDurationSeconds) + ".");
                tempBanPlayer(player, buildEnforcementMessage(player, entry, "tempban"), settings.tempbanDurationSeconds);
            } else if ("BAN_DRY_RUN".equals(action)) {
                postActionLog(player, entry, "ban_dry_run", "BAN DRY-RUN: would permanently ban this player. " + reason);
            } else if ("BAN".equals(action)) {
                postActionLog(player, entry, "ban", reason);
                banPlayer(player, buildEnforcementMessage(player, entry, "ban"));
            }
            return;
        }

        notifyOps(player, entry, "Obsidian Watch listed player joined with unknown list type: " + entry.normalizedListType());
    }

    private boolean isPlayerExempt(Player player) {
        loadExemptionsFromConfig();
        if (localExemptions.contains(player.getUniqueId())) {
            return true;
        }
        if (player.hasPermission("obsidianwatch.exempt")) {
            return true;
        }
        RemoteSettings settings = currentSettings();
        if (settings.exemptOps && player.isOp()) {
            return true;
        }
        return settings.exemptCreative && player.getGameMode() == GameMode.CREATIVE;
    }

    private boolean markJoinActionIfAllowed(UUID uuid) {
        int cooldownSeconds = Math.max(0, currentSettings().joinActionCooldownSeconds);
        if (cooldownSeconds <= 0) {
            return true;
        }

        Instant now = Instant.now();
        Instant previous = recentJoinActions.get(uuid);
        if (previous != null && Duration.between(previous, now).getSeconds() < cooldownSeconds) {
            long remaining = cooldownSeconds - Duration.between(previous, now).getSeconds();
            lastStatus = "Join action cooldown skipped for " + uuid + " (" + remaining + "s remaining).";
            return false;
        }

        recentJoinActions.put(uuid, now);
        return true;
    }

    private void disconnectPlayer(final UUID uuid, final String message) {
        if (!disconnectsInProgress.add(uuid)) {
            lastStatus = "Disconnect already queued for " + uuid + ".";
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    Player target = Bukkit.getPlayer(uuid);
                    if (target != null && target.isOnline()) {
                        target.kickPlayer(normalizeDisconnectMessage(message));
                        lastStatus = "Disconnect sent for " + target.getName() + ".";
                    } else {
                        lastStatus = "Disconnect skipped because player was already offline: " + uuid + ".";
                    }
                } catch (Exception exception) {
                    lastStatus = "Disconnect failed for " + uuid + ": " + exception.getMessage();
                } finally {
                    Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                        @Override
                        public void run() {
                            disconnectsInProgress.remove(uuid);
                        }
                    }, 100L);
                }
            }
        }, 15L);
    }

    private void banPlayer(Player player, String reason) {
        if (!currentSettings().allowRealBans) {
            lastStatus = "Permanent ban blocked by allowRealBans=false.";
            postActionLog(player, entryFor(player.getUniqueId()), "ban_dry_run", "Permanent ban blocked by local/remote safety gate.");
            return;
        }

        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), normalizeDisconnectMessage(reason), null, "Obsidian Watch");
        disconnectPlayer(player.getUniqueId(), reason);
    }

    private void tempBanPlayer(Player player, String reason, int durationSeconds) {
        int safeDuration = Math.max(60, Math.min(2592000, durationSeconds));
        Date expiresAt = new Date(System.currentTimeMillis() + safeDuration * 1000L);
        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), normalizeDisconnectMessage(reason), expiresAt, "Obsidian Watch");
        disconnectPlayer(player.getUniqueId(), reason);
    }

    private void notifyOps(Player player, WatchEntry entry, String reason) {
        if (!currentSettings().notifyOps) {
            return;
        }

        String message = ChatColor.DARK_AQUA + "[Obsidian Watch] "
                + ChatColor.WHITE + player.getName()
                + ChatColor.GRAY + " is listed as "
                + (entry.isConfirmed() ? ChatColor.RED : ChatColor.YELLOW) + entry.normalizedListType()
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.AQUA + entry.category()
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GOLD + entry.severity()
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.GRAY + reason;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp() || online.hasPermission("obsidianwatch.notify")) {
                online.sendMessage(message);
            }
        }
        plugin.getLogger().warning(ChatColor.stripColor(message));
    }

    private String buildReason(WatchEntry entry) {
        if (!isBlank(entry.reasonPublic())) {
            return entry.reasonPublic();
        }
        return "Listed as " + entry.normalizedListType()
                + " for " + entry.category()
                + " with severity " + entry.severity() + ".";
    }

    private String buildEnforcementMessage(Player player, WatchEntry entry, String action) {
        RemoteSettings settings = currentSettings();
        String template;
        if ("tempban".equalsIgnoreCase(action)) {
            template = settings.tempbanMessage;
        } else if ("ban".equalsIgnoreCase(action)) {
            template = settings.banMessage;
        } else if (entry.isConfirmed()) {
            template = settings.confirmedKickMessage;
        } else {
            template = settings.watchlistKickMessage;
        }

        return template
                .replace("\\n", "\n")
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{list}", entry.normalizedListType())
                .replace("{category}", entry.category())
                .replace("{severity}", entry.severity())
                .replace("{action}", action)
                .replace("{reason}", buildReason(entry))
                .replace("{appeal_url}", settings.appealUrl)
                .replace("{tempban_duration}", formatDuration(settings.tempbanDurationSeconds));
    }

    private String normalizeDisconnectMessage(String message) {
        String normalized = message == null ? "" : message.replace("\\n", "\n").replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() == 0) {
            normalized = "You were removed by Obsidian Watch.\n\nAppeal: " + currentSettings().appealUrl;
        }
        if (normalized.length() > 1800) {
            normalized = normalized.substring(0, 1800).trim() + "\n\nAppeal: " + currentSettings().appealUrl;
        }
        return normalized;
    }

    private void syncServerConfig() {
        if (!isApiReady()) {
            return;
        }

        try {
            HttpResult response = getJson("/api/v1/server/config");
            if (response.statusCode < 200 || response.statusCode >= 300) {
                lastStatus = "Server config failed HTTP " + response.statusCode + ": " + response.body;
                postDiagnostic("config", "error", "Server config failed HTTP " + response.statusCode, null);
                return;
            }

            JsonObject root = new JsonParser().parse(response.body).getAsJsonObject();
            if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
                lastStatus = "Server config returned ok=false.";
                return;
            }

            JsonObject config = root.has("config") && root.get("config").isJsonObject()
                    ? root.getAsJsonObject("config")
                    : root;

            JsonObject actions = child(config, "actions");
            JsonObject sync = child(config, "sync");
            JsonObject safety = child(config, "safety");
            JsonObject messages = child(config, "messages");

            RemoteSettings previous = remoteSettings;
            RemoteSettings next = new RemoteSettings(
                    true,
                    getBoolean(actions, "notify_ops", getBoolean(actions, "notifyOps", localNotifyOps())),
                    getString(actions, "watchlist_action", getString(actions, "watchlistAction", localWatchlistAction())),
                    getString(actions, "confirmed_action", getString(actions, "confirmedAction", localConfirmedAction())),
                    getBoolean(actions, "post_action_logs", getBoolean(actions, "postActionLogs", localPostActionLogs())),
                    getBoolean(actions, "allow_real_bans", getBoolean(actions, "allowRealBans", localAllowRealBans())),
                    getInt(sync, "sync_interval_seconds", plugin.getConfig().getInt("syncIntervalSeconds", 120), 60, 3600),
                    getInt(safety, "join_action_cooldown_seconds", plugin.getConfig().getInt("safety.joinActionCooldownSeconds", 300), 0, 86400),
                    getInt(safety, "tempban_duration_seconds", plugin.getConfig().getInt("safety.tempbanDurationSeconds", 604800), 60, 2592000),
                    getBoolean(safety, "exempt_ops", getBoolean(safety, "exemptOps", plugin.getConfig().getBoolean("safety.exemptOps", true))),
                    getBoolean(safety, "exempt_creative", getBoolean(safety, "exemptCreative", plugin.getConfig().getBoolean("safety.exemptCreative", true))),
                    getString(messages, "appeal_url", getString(messages, "appealUrl", plugin.getConfig().getString("messages.appealUrl", ""))),
                    getString(messages, "watchlist_kick_message", getString(messages, "watchlistKickMessage", plugin.getConfig().getString("messages.watchlistKickMessage", ""))),
                    getString(messages, "confirmed_kick_message", getString(messages, "confirmedKickMessage", plugin.getConfig().getString("messages.confirmedKickMessage", ""))),
                    getString(messages, "tempban_message", getString(messages, "tempbanMessage", plugin.getConfig().getString("messages.tempbanMessage", ""))),
                    getString(messages, "ban_message", getString(messages, "banMessage", plugin.getConfig().getString("messages.banMessage", ""))),
                    getBoolean(messages, "show_category_severity", true),
                    getBoolean(messages, "show_public_reason", true)
            );

            remoteSettings = next;
            if (previous == null || !previous.enforcementSignature().equals(next.enforcementSignature())) {
                recentJoinActions.clear();
            }

            lastConfigSync = Instant.now();
            lastStatus = "Server config synced: " + next.watchlistAction + "/" + effectiveConfirmedAction(next) + ".";
            postDiagnostic("config", "ok", lastStatus, null);
        } catch (Exception exception) {
            lastStatus = "Server config failed: " + exception.getMessage();
            postDiagnostic("config", "error", lastStatus, null);
        }
    }

    private void sendHeartbeat() {
        if (!isApiReady()) {
            return;
        }

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("mod_version", PLUGIN_VERSION);
            payload.addProperty("minecraft_version", Bukkit.getBukkitVersion());
            payload.addProperty("server_brand", plugin.getConfig().getString("serverBrand", "bukkit"));
            payload.addProperty("server_name", resolveServerName());

            HttpResult response = postJson("/api/v1/server/heartbeat", payload);
            if (response.statusCode >= 200 && response.statusCode < 300) {
                lastHeartbeat = Instant.now();
                lastStatus = "Heartbeat accepted.";
            } else {
                lastStatus = "Heartbeat failed HTTP " + response.statusCode + ": " + response.body;
            }
        } catch (Exception exception) {
            lastStatus = "Heartbeat failed: " + exception.getMessage();
        }
    }

    private void syncSnapshot() {
        if (!isApiReady()) {
            return;
        }

        try {
            HttpResult response = getJson("/api/v1/lists/snapshot");
            if (response.statusCode < 200 || response.statusCode >= 300) {
                lastStatus = "Snapshot failed HTTP " + response.statusCode + ": " + response.body;
                postDiagnostic("snapshot", "error", "Snapshot failed HTTP " + response.statusCode, null);
                return;
            }

            JsonObject root = new JsonParser().parse(response.body).getAsJsonObject();
            if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
                lastStatus = "Snapshot returned ok=false.";
                postDiagnostic("snapshot", "error", "Snapshot returned ok=false.", null);
                return;
            }

            JsonArray entries = root.has("entries") && root.get("entries").isJsonArray()
                    ? root.getAsJsonArray("entries")
                    : new JsonArray();

            Map<UUID, WatchEntry> next = new ConcurrentHashMap<UUID, WatchEntry>();
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject obj = element.getAsJsonObject();
                String uuidRaw = getString(obj, "java_uuid", "");
                if (isBlank(uuidRaw)) {
                    continue;
                }

                UUID uuid = parseUuid(uuidRaw);
                if (uuid == null) {
                    continue;
                }

                WatchEntry entry = new WatchEntry(
                        uuidRaw,
                        getString(obj, "current_username", ""),
                        getString(obj, "list_type", ""),
                        getString(obj, "category", ""),
                        getString(obj, "severity", ""),
                        getString(obj, "action_recommended", ""),
                        getString(obj, "reason_public", "")
                );

                WatchEntry existing = next.get(uuid);
                if (existing == null || entryPriority(entry) > entryPriority(existing)) {
                    next.put(uuid, entry);
                }
            }

            cachedEntries.clear();
            cachedEntries.putAll(next);
            lastSync = Instant.now();
            lastStatus = "Snapshot synced " + cachedEntries.size() + " entries.";
            postDiagnostic("snapshot", "ok", lastStatus, Integer.valueOf(cachedEntries.size()));
        } catch (Exception exception) {
            lastStatus = "Snapshot failed: " + exception.getMessage();
            postDiagnostic("snapshot", "error", lastStatus, null);
        }
    }

    private int entryPriority(WatchEntry entry) {
        int listScore = entry.isConfirmed() ? 1000 : entry.isWatchlist() ? 500 : 0;
        String severity = safe(entry.severity()).toLowerCase(Locale.ROOT);
        int severityScore;
        if ("critical".equals(severity)) {
            severityScore = 40;
        } else if ("high".equals(severity)) {
            severityScore = 30;
        } else if ("medium".equals(severity)) {
            severityScore = 20;
        } else if ("low".equals(severity)) {
            severityScore = 10;
        } else {
            severityScore = 0;
        }

        String action = safe(entry.actionRecommended()).toLowerCase(Locale.ROOT);
        int actionScore;
        if ("ban".equals(action)) {
            actionScore = 5;
        } else if ("tempban".equals(action)) {
            actionScore = 4;
        } else if ("kick".equals(action)) {
            actionScore = 3;
        } else if ("notify".equals(action)) {
            actionScore = 2;
        } else {
            actionScore = 0;
        }

        return listScore + severityScore + actionScore;
    }

    private void postActionLog(final Player player, final WatchEntry entry, final String action, final String reason) {
        if (!currentSettings().postActionLogs || !isApiReady()) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    WatchEntry safeEntry = entry == null ? entryFor(player.getUniqueId()) : entry;
                    JsonObject payload = new JsonObject();
                    payload.addProperty("java_uuid", player.getUniqueId().toString());
                    payload.addProperty("username", player.getName());
                    payload.addProperty("action", action.toLowerCase(Locale.ROOT));
                    payload.addProperty("reason", reason);
                    payload.addProperty("list_type", safeEntry == null ? "unknown" : safeEntry.normalizedListType());
                    postJson("/api/v1/mod/action-log", payload);
                } catch (Exception exception) {
                    lastStatus = "Action log failed: " + exception.getMessage();
                }
            }
        });
    }

    private void postDiagnostic(String eventType, String status, String message, Integer snapshotEntryCount) {
        if (!plugin.getConfig().getBoolean("enabled", true) || isBlank(apiKey())) {
            return;
        }

        try {
            RemoteSettings settings = currentSettings();
            JsonObject payload = new JsonObject();
            payload.addProperty("event_type", eventType);
            payload.addProperty("status", status);
            payload.addProperty("message", message == null ? "" : message);
            payload.addProperty("mod_version", PLUGIN_VERSION);
            payload.addProperty("minecraft_version", Bukkit.getBukkitVersion());
            payload.addProperty("server_brand", plugin.getConfig().getString("serverBrand", "bukkit"));
            payload.addProperty("watchlist_action", settings.watchlistAction);
            payload.addProperty("confirmed_action", effectiveConfirmedAction(settings));
            payload.addProperty("join_action_cooldown_seconds", settings.joinActionCooldownSeconds);
            if (snapshotEntryCount != null) {
                payload.addProperty("snapshot_entry_count", snapshotEntryCount.intValue());
            }
            postJson("/api/v1/mod/diagnostics", payload);
        } catch (Exception ignored) {
        }
    }

    private void loadExemptionsFromConfig() {
        localExemptions.clear();
        for (String raw : plugin.getConfig().getStringList("localExemptUuids")) {
            UUID uuid = parseUuid(raw);
            if (uuid != null) {
                localExemptions.add(uuid);
            }
        }
    }

    private void saveExemptionsToConfig() {
        ArrayList<String> values = new ArrayList<String>();
        for (UUID uuid : localExemptions) {
            values.add(uuid.toString());
        }
        Collections.sort(values);
        plugin.getConfig().set("localExemptUuids", values);
        plugin.saveConfig();
    }

    private PlayerLookup resolvePlayerLookup(String playerRaw) {
        if (isBlank(playerRaw)) {
            return PlayerLookup.error("missing player UUID or username");
        }

        String query = playerRaw.trim();
        UUID parsed = parseUuid(query);
        if (parsed != null) {
            WatchEntry entry = cachedEntries.get(parsed);
            String displayName = entry != null && !isBlank(entry.currentUsername()) ? entry.currentUsername() : parsed.toString();
            return new PlayerLookup(parsed, entry, displayName, null);
        }

        for (Map.Entry<UUID, WatchEntry> cacheEntry : cachedEntries.entrySet()) {
            WatchEntry entry = cacheEntry.getValue();
            if (!isBlank(entry.currentUsername()) && entry.currentUsername().equalsIgnoreCase(query)) {
                return new PlayerLookup(cacheEntry.getKey(), entry, entry.currentUsername(), null);
            }
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(query);
        if (offline != null && offline.getUniqueId() != null && offline.hasPlayedBefore()) {
            UUID uuid = offline.getUniqueId();
            return new PlayerLookup(uuid, cachedEntries.get(uuid), query, null);
        }

        return PlayerLookup.error("use a Java UUID or a username already present in the local snapshot");
    }

    private boolean isAllowedDiagnosticAction(String action) {
        return "notify".equals(action)
                || "kick".equals(action)
                || "ban".equals(action)
                || "ban_dry_run".equals(action)
                || "tempban".equals(action)
                || "exempted".equals(action);
    }

    private String configuredActionFor(WatchEntry entry) {
        RemoteSettings settings = currentSettings();
        if (entry.isWatchlist()) {
            return normalizeAction(settings.watchlistAction);
        }
        if (entry.isConfirmed()) {
            return effectiveConfirmedAction(settings);
        }
        return "UNKNOWN";
    }

    private WatchEntry entryFor(UUID uuid) {
        return uuid == null ? null : cachedEntries.get(uuid);
    }

    private RemoteSettings currentSettings() {
        RemoteSettings settings = remoteSettings;
        if (settings == null) {
            settings = RemoteSettings.fromLocal(plugin.getConfig());
            remoteSettings = settings;
        }
        return settings;
    }

    private String effectiveConfirmedAction(RemoteSettings settings) {
        String action = normalizeAction(settings.confirmedAction);
        if ("BAN".equals(action) && !settings.allowRealBans) {
            return "BAN_DRY_RUN";
        }
        return action;
    }

    private String normalizeAction(String action) {
        if (isBlank(action)) {
            return "NONE";
        }
        return action.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private boolean isApiReady() {
        return plugin.getConfig().getBoolean("enabled", true) && !isBlank(apiKey()) && !isBlank(apiBaseUrl());
    }

    private String apiBaseUrl() {
        return trimTrailingSlash(plugin.getConfig().getString("apiBaseUrl", ""));
    }

    private String apiKey() {
        return plugin.getConfig().getString("apiKey", "");
    }

    private String resolveServerName() {
        String configured = plugin.getConfig().getString("serverName", "");
        if (!isBlank(configured)) {
            return configured.trim();
        }
        return Bukkit.getServer().getName();
    }

    private String sanitizedBaseUrl() {
        String value = apiBaseUrl();
        return isBlank(value) ? "missing" : value;
    }

    private HttpResult getJson(String path) throws Exception {
        HttpURLConnection connection = openConnection(path, "GET");
        return readResponse(connection);
    }

    private HttpResult postJson(String path, JsonObject payload) throws Exception {
        HttpURLConnection connection = openConnection(path, "POST");
        connection.setDoOutput(true);
        byte[] body = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        OutputStream output = connection.getOutputStream();
        try {
            output.write(body);
        } finally {
            output.close();
        }
        return readResponse(connection);
    }

    private HttpURLConnection openConnection(String path, String method) throws Exception {
        URI uri = new URI(apiBaseUrl() + path);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ObsidianWatch-Bukkit/" + PLUGIN_VERSION);
        connection.setRequestProperty("X-Obsidian-Key", apiKey());
        if ("POST".equals(method)) {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        return connection;
    }

    private HttpResult readResponse(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
        String body = readFully(stream);
        connection.disconnect();
        return new HttpResult(code, body);
    }

    private String readFully(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        try {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString().trim();
        } finally {
            reader.close();
        }
    }

    private JsonObject child(JsonObject object, String name) {
        return object != null && object.has(name) && object.get(name).isJsonObject() ? object.getAsJsonObject(name) : new JsonObject();
    }

    private String getString(JsonObject object, String name, String fallback) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(name).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean getBoolean(JsonObject object, String name, boolean fallback) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(name).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int getInt(JsonObject object, String name, int fallback, int min, int max) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            int value = object.get(name).getAsInt();
            return Math.max(min, Math.min(max, value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean localNotifyOps() {
        return plugin.getConfig().getBoolean("actions.notifyOps", true);
    }

    private String localWatchlistAction() {
        return plugin.getConfig().getString("actions.watchlistAction", "NOTIFY");
    }

    private String localConfirmedAction() {
        return plugin.getConfig().getString("actions.confirmedAction", "BAN_DRY_RUN");
    }

    private boolean localPostActionLogs() {
        return plugin.getConfig().getBoolean("actions.postActionLogs", true);
    }

    private boolean localAllowRealBans() {
        return plugin.getConfig().getBoolean("actions.allowRealBans", false);
    }

    private UUID parseUuid(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String healthLabel() {
        if (!plugin.getConfig().getBoolean("enabled", true)) {
            return "disabled";
        }
        if (isBlank(apiKey())) {
            return "missing api key";
        }
        if (lastSync != null && Duration.between(lastSync, Instant.now()).getSeconds() <= Math.max(180, plugin.getConfig().getInt("syncIntervalSeconds", 120) * 3L)) {
            return "healthy";
        }
        return "waiting";
    }

    private String ago(Instant instant) {
        if (instant == null) {
            return "never";
        }
        long seconds = Duration.between(instant, Instant.now()).getSeconds();
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        return hours + "h ago";
    }

    private String formatDuration(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + "h";
        }
        return (seconds / 86400) + "d";
    }

    private String title(String text) {
        return ChatColor.DARK_AQUA + "==== " + ChatColor.AQUA + text + ChatColor.DARK_AQUA + " ====" + ChatColor.RESET;
    }

    private String section(String text) {
        return ChatColor.DARK_GRAY + "-- " + ChatColor.GRAY + text + ChatColor.DARK_GRAY + " --";
    }

    private String line(String key, String value) {
        return ChatColor.GRAY + key + ": " + ChatColor.WHITE + value;
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static final class HttpResult {
        private final int statusCode;
        private final String body;

        private HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private static final class PlayerLookup {
        private final UUID uuid;
        private final WatchEntry entry;
        private final String displayName;
        private final String error;

        private PlayerLookup(UUID uuid, WatchEntry entry, String displayName, String error) {
            this.uuid = uuid;
            this.entry = entry;
            this.displayName = displayName;
            this.error = error;
        }

        private static PlayerLookup error(String error) {
            return new PlayerLookup(null, null, null, error);
        }
    }

    private static final class RemoteSettings {
        private final boolean loaded;
        private final boolean notifyOps;
        private final String watchlistAction;
        private final String confirmedAction;
        private final boolean postActionLogs;
        private final boolean allowRealBans;
        private final int syncIntervalSeconds;
        private final int joinActionCooldownSeconds;
        private final int tempbanDurationSeconds;
        private final boolean exemptOps;
        private final boolean exemptCreative;
        private final String appealUrl;
        private final String watchlistKickMessage;
        private final String confirmedKickMessage;
        private final String tempbanMessage;
        private final String banMessage;
        private final boolean showCategorySeverity;
        private final boolean showPublicReason;

        private RemoteSettings(
                boolean loaded,
                boolean notifyOps,
                String watchlistAction,
                String confirmedAction,
                boolean postActionLogs,
                boolean allowRealBans,
                int syncIntervalSeconds,
                int joinActionCooldownSeconds,
                int tempbanDurationSeconds,
                boolean exemptOps,
                boolean exemptCreative,
                String appealUrl,
                String watchlistKickMessage,
                String confirmedKickMessage,
                String tempbanMessage,
                String banMessage,
                boolean showCategorySeverity,
                boolean showPublicReason
        ) {
            this.loaded = loaded;
            this.notifyOps = notifyOps;
            this.watchlistAction = watchlistAction;
            this.confirmedAction = confirmedAction;
            this.postActionLogs = postActionLogs;
            this.allowRealBans = allowRealBans;
            this.syncIntervalSeconds = syncIntervalSeconds;
            this.joinActionCooldownSeconds = joinActionCooldownSeconds;
            this.tempbanDurationSeconds = tempbanDurationSeconds;
            this.exemptOps = exemptOps;
            this.exemptCreative = exemptCreative;
            this.appealUrl = appealUrl;
            this.watchlistKickMessage = watchlistKickMessage;
            this.confirmedKickMessage = confirmedKickMessage;
            this.tempbanMessage = tempbanMessage;
            this.banMessage = banMessage;
            this.showCategorySeverity = showCategorySeverity;
            this.showPublicReason = showPublicReason;
        }

        private static RemoteSettings fromLocal(FileConfiguration config) {
            return new RemoteSettings(
                    false,
                    config.getBoolean("actions.notifyOps", true),
                    config.getString("actions.watchlistAction", "NOTIFY"),
                    config.getString("actions.confirmedAction", "BAN_DRY_RUN"),
                    config.getBoolean("actions.postActionLogs", true),
                    config.getBoolean("actions.allowRealBans", false),
                    Math.max(60, config.getInt("syncIntervalSeconds", 120)),
                    Math.max(0, config.getInt("safety.joinActionCooldownSeconds", 300)),
                    Math.max(60, Math.min(2592000, config.getInt("safety.tempbanDurationSeconds", 604800))),
                    config.getBoolean("safety.exemptOps", true),
                    config.getBoolean("safety.exemptCreative", true),
                    config.getString("messages.appealUrl", ""),
                    config.getString("messages.watchlistKickMessage", ""),
                    config.getString("messages.confirmedKickMessage", ""),
                    config.getString("messages.tempbanMessage", ""),
                    config.getString("messages.banMessage", ""),
                    true,
                    true
            );
        }

        private String enforcementSignature() {
            return watchlistAction + "|" + confirmedAction + "|" + allowRealBans + "|" + joinActionCooldownSeconds + "|" + tempbanDurationSeconds + "|" + exemptOps + "|" + exemptCreative;
        }
    }
}
