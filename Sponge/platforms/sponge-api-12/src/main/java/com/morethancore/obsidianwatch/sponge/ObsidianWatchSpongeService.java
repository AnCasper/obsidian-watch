package com.morethancore.obsidianwatch.sponge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ObsidianWatchSpongeService {
    private static final Gson GSON = new Gson();
    private static final String PLUGIN_VERSION = "1.0.0-pre1+sponge.api12";

    private final Properties config = new Properties();
    private final Map<UUID, WatchEntry> cachedEntries = new ConcurrentHashMap<UUID, WatchEntry>();
    private final Map<UUID, Instant> recentJoinActions = new ConcurrentHashMap<UUID, Instant>();
    private final Set<UUID> localExemptions = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> permanentBlocks = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Map<UUID, Instant> tempBlocks = new ConcurrentHashMap<UUID, Instant>();

    private volatile Server server;
    private volatile Path dataDirectory;
    private volatile String lastStatus = "Not started.";
    private volatile Instant lastHeartbeat;
    private volatile Instant lastSync;
    private volatile Instant lastConfigSync;
    private volatile RemoteSettings remoteSettings;
    private volatile boolean running;
    private Thread workerThread;

    public synchronized void start(Server server) {
        this.server = server;
        this.dataDirectory = Paths.get("config", "obsidianwatch");
        this.running = true;

        try {
            Files.createDirectories(dataDirectory);
            loadConfig();
            loadExemptions();
            loadLocalBlocks();
        } catch (Exception exception) {
            lastStatus = "Startup config load failed: " + exception.getMessage();
            return;
        }

        remoteSettings = RemoteSettings.fromLocal(config);

        if (!getBoolean("enabled", true)) {
            lastStatus = "Disabled in config.";
            return;
        }

        if (isBlank(apiKey())) {
            lastStatus = "Enabled, but apiKey is blank.";
            return;
        }

        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runWorkerLoop();
            }
        }, "ObsidianWatch-Sponge-Sync");
        workerThread.setDaemon(true);
        workerThread.start();

        lastStatus = "Started. First sync scheduled.";
    }

    public synchronized void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        cachedEntries.clear();
        recentJoinActions.clear();
        localExemptions.clear();
        permanentBlocks.clear();
        tempBlocks.clear();
        remoteSettings = null;
        lastStatus = "Stopped.";
    }

    public void reload() {
        Server activeServer = server;
        stop();
        if (activeServer != null) {
            start(activeServer);
            syncAllAsync();
        }
    }

    public void handleJoin(final ServerPlayer player) {
        if (!getBoolean("enabled", true)) {
            return;
        }

        if (isPlayerLocallyBlocked(player)) {
            player.kick(Component.text("You are blocked by Obsidian Watch. Appeal: " + currentSettings().appealUrl));
            return;
        }

        final WatchEntry entry = cachedEntries.get(player.uniqueId());
        if (entry == null) {
            return;
        }

        handleListedPlayer(player, entry);
    }

    public void syncAllAsync() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                syncServerConfig();
                sendHeartbeat();
                syncSnapshot();
            }
        }, "ObsidianWatch-Sponge-ManualSync");
        thread.setDaemon(true);
        thread.start();
    }

    public void heartbeatAsync() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                sendHeartbeat();
            }
        }, "ObsidianWatch-Sponge-Heartbeat");
        thread.setDaemon(true);
        thread.start();
    }

    public String statusMessage() {
        return "==== Obsidian Watch ====\n"
                + line("Status", healthLabel()) + "\n"
                + line("Server", resolveServerName()) + "\n"
                + line("Entries", String.valueOf(cachedEntries.size())) + "\n"
                + line("Exemptions", String.valueOf(localExemptions.size())) + "\n"
                + line("Permanent blocks", String.valueOf(permanentBlocks.size())) + "\n"
                + line("Temporary blocks", String.valueOf(tempBlocks.size())) + "\n"
                + line("Config", ago(lastConfigSync)) + "\n"
                + line("Snapshot", ago(lastSync)) + "\n"
                + line("Heartbeat", ago(lastHeartbeat)) + "\n"
                + line("Last", lastStatus);
    }

    public String diagnosticsMessage() {
        RemoteSettings settings = currentSettings();
        return "==== Obsidian Watch Diagnostics ====\n"
                + "-- API --\n"
                + line("Enabled", yesNo(getBoolean("enabled", true))) + "\n"
                + line("Base URL", sanitizedBaseUrl()) + "\n"
                + line("API key", isBlank(apiKey()) ? "missing" : "configured") + "\n"
                + "-- Cache --\n"
                + line("Entries", String.valueOf(cachedEntries.size())) + "\n"
                + line("Local exemptions", String.valueOf(localExemptions.size())) + "\n"
                + line("Sponge blocks", permanentBlocks.size() + " permanent, " + tempBlocks.size() + " temporary") + "\n"
                + "-- Remote config --\n"
                + line("Loaded", settings.loaded ? "yes" : "local fallback") + "\n"
                + line("Watchlist action", settings.watchlistAction) + "\n"
                + line("Confirmed action", effectiveConfirmedAction(settings)) + "\n"
                + line("Tempban duration", formatDuration(settings.tempbanDurationSeconds)) + "\n"
                + line("Real bans", settings.allowRealBans ? "enabled" : "blocked") + "\n"
                + line("Last status", lastStatus);
    }

    public String configMessage() {
        RemoteSettings settings = currentSettings();
        return "==== Obsidian Watch Config ====\n"
                + line("Local enabled", yesNo(getBoolean("enabled", true))) + "\n"
                + line("Server brand", getString("serverBrand", "sponge")) + "\n"
                + line("Server name", resolveServerName()) + "\n"
                + line("Sync interval", getInt("syncIntervalSeconds", 120, 60, 3600) + "s") + "\n"
                + line("Watchlist action", settings.watchlistAction) + "\n"
                + line("Confirmed action", effectiveConfirmedAction(settings)) + "\n"
                + line("Notify ops", yesNo(settings.notifyOps)) + "\n"
                + line("Post action logs", yesNo(settings.postActionLogs)) + "\n"
                + line("Real bans", settings.allowRealBans ? "enabled" : "blocked");
    }

    public String checkMessage(String playerRaw, String usernameRaw) {
        PlayerLookup lookup = resolvePlayerLookup(playerRaw);
        if (lookup.error != null) {
            return "==== Obsidian Watch ====\n" + line("Check", lookup.error);
        }

        String displayName = isBlank(usernameRaw) ? lookup.displayName : usernameRaw.trim();
        if (lookup.entry == null) {
            return "==== Obsidian Watch: No Match ====\n"
                    + line("Player", displayName) + "\n"
                    + line("UUID", lookup.uuid.toString()) + "\n"
                    + line("Result", "not present in local snapshot");
        }

        WatchEntry entry = lookup.entry;
        return "==== Obsidian Watch: Match ====\n"
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
            return "==== Obsidian Watch: Test Action Log ====\n" + line("Result", lookup.error);
        }

        String action = actionRaw == null ? "" : actionRaw.trim().toLowerCase(Locale.ROOT);
        if (!isAllowedDiagnosticAction(action)) {
            return "==== Obsidian Watch: Test Action Log ====\n"
                    + line("Result", "invalid action") + "\n"
                    + line("Allowed", "notify, kick, tempban, ban, ban_dry_run, exempted");
        }

        String username = isBlank(usernameRaw) ? lookup.displayName : usernameRaw.trim();
        String listType = lookup.entry == null ? "diagnostic" : lookup.entry.normalizedListType();
        final JsonObject payload = new JsonObject();
        payload.addProperty("java_uuid", lookup.uuid.toString());
        payload.addProperty("username", username);
        payload.addProperty("action", action);
        payload.addProperty("reason", "Manual Sponge diagnostic action log from /ow testlog.");
        payload.addProperty("list_type", listType);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    postJson("/api/v1/mod/action-log", payload);
                    lastStatus = "Test action log accepted.";
                } catch (Exception exception) {
                    lastStatus = "Test action log failed: " + exception.getMessage();
                }
            }
        }, "ObsidianWatch-Sponge-TestLog");
        thread.setDaemon(true);
        thread.start();

        return "==== Obsidian Watch: Test Action Log ====\n"
                + line("Player", username) + "\n"
                + line("UUID", lookup.uuid.toString()) + "\n"
                + line("Action", action.toUpperCase(Locale.ROOT)) + "\n"
                + line("State", "queued");
    }

    public String addExemption(String uuidRaw) {
        UUID uuid = parseUuid(uuidRaw);
        if (uuid == null) {
            return "==== Obsidian Watch ====\n" + line("Exemption", "invalid Java UUID");
        }
        boolean added = localExemptions.add(uuid);
        saveUuidSet(dataDirectory.resolve("local-exemptions.txt"), localExemptions);
        return "==== Obsidian Watch: Local Exemption ====\n"
                + line("UUID", uuid.toString()) + "\n"
                + line("State", added ? "added" : "already exempt");
    }

    public String removeExemption(String uuidRaw) {
        UUID uuid = parseUuid(uuidRaw);
        if (uuid == null) {
            return "==== Obsidian Watch ====\n" + line("Exemption", "invalid Java UUID");
        }
        boolean removed = localExemptions.remove(uuid);
        saveUuidSet(dataDirectory.resolve("local-exemptions.txt"), localExemptions);
        return "==== Obsidian Watch: Local Exemption ====\n"
                + line("UUID", uuid.toString()) + "\n"
                + line("State", removed ? "removed" : "not exempt");
    }

    public String listExemptionsMessage() {
        loadExemptions();
        if (localExemptions.isEmpty()) {
            return "==== Obsidian Watch: Local Exemptions ====\n"
                    + line("Count", "0") + "\n"
                    + line("Result", "no local exemptions configured");
        }

        ArrayList<String> list = new ArrayList<String>();
        for (UUID uuid : localExemptions) {
            list.add(uuid.toString());
        }
        Collections.sort(list);

        StringBuilder builder = new StringBuilder();
        builder.append("==== Obsidian Watch: Local Exemptions ====").append("\n");
        builder.append(line("Count", String.valueOf(list.size()))).append("\n");
        for (String uuid : list) {
            builder.append(uuid).append("\n");
        }
        return builder.toString();
    }

    public String localBlocksMessage() {
        pruneExpiredBlocks();

        StringBuilder builder = new StringBuilder();
        builder.append("==== Obsidian Watch: Sponge Blocks ====").append("\n");
        builder.append(line("Permanent", String.valueOf(permanentBlocks.size()))).append("\n");
        builder.append(line("Temporary", String.valueOf(tempBlocks.size()))).append("\n");
        return builder.toString();
    }

    private void runWorkerLoop() {
        while (running) {
            syncServerConfig();
            sendHeartbeat();
            syncSnapshot();

            long seconds = Math.max(60L, getInt("syncIntervalSeconds", 120, 60, 3600));
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void handleListedPlayer(final ServerPlayer player, WatchEntry entry) {
        String reason = buildReason(entry);

        if (isPlayerExempt(player)) {
            if (markJoinActionIfAllowed(player.uniqueId())) {
                postActionLog(player, entry, "exempted", "Local Obsidian Watch exemption skipped configured action: " + reason);
            }
            return;
        }

        if (!markJoinActionIfAllowed(player.uniqueId())) {
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
                player.kick(Component.text(buildEnforcementMessage(player, entry, "kick")));
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
                player.kick(Component.text(buildEnforcementMessage(player, entry, "kick")));
            } else if ("TEMPBAN".equals(action)) {
                postActionLog(player, entry, "tempban", reason + " Temporary Sponge block duration: " + formatDuration(settings.tempbanDurationSeconds) + ".");
                tempBlockPlayer(player, settings.tempbanDurationSeconds);
                player.kick(Component.text(buildEnforcementMessage(player, entry, "tempban")));
            } else if ("BAN_DRY_RUN".equals(action)) {
                postActionLog(player, entry, "ban_dry_run", "BAN DRY-RUN: would Sponge-block this player. " + reason);
            } else if ("BAN".equals(action)) {
                if (!settings.allowRealBans) {
                    postActionLog(player, entry, "ban_dry_run", "Permanent Sponge block blocked by allowRealBans=false. " + reason);
                    return;
                }
                postActionLog(player, entry, "ban", reason);
                permanentBlocks.add(player.uniqueId());
                saveUuidSet(dataDirectory.resolve("local-sponge-bans.txt"), permanentBlocks);
                player.kick(Component.text(buildEnforcementMessage(player, entry, "ban")));
            }
        }
    }

    private boolean isPlayerExempt(ServerPlayer player) {
        loadExemptions();
        if (localExemptions.contains(player.uniqueId())) {
            return true;
        }
        if (player.hasPermission("obsidianwatch.exempt")) {
            return true;
        }
        return currentSettings().exemptOps && player.hasPermission("obsidianwatch.admin");
    }

    private boolean isPlayerLocallyBlocked(ServerPlayer player) {
        pruneExpiredBlocks();
        return permanentBlocks.contains(player.uniqueId()) || tempBlocks.containsKey(player.uniqueId());
    }

    private void tempBlockPlayer(ServerPlayer player, int durationSeconds) {
        int safeDuration = Math.max(60, Math.min(2592000, durationSeconds));
        tempBlocks.put(player.uniqueId(), Instant.now().plusSeconds(safeDuration));
        saveTempBlocks();
    }

    private void pruneExpiredBlocks() {
        Instant now = Instant.now();
        boolean changed = false;
        for (Map.Entry<UUID, Instant> entry : new ArrayList<Map.Entry<UUID, Instant>>(tempBlocks.entrySet())) {
            if (!entry.getValue().isAfter(now)) {
                tempBlocks.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) {
            saveTempBlocks();
        }
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

    private void notifyOps(ServerPlayer player, WatchEntry entry, String reason) {
        if (!currentSettings().notifyOps) {
            return;
        }

        Component message = Component.text("[Obsidian Watch] " + player.name()
                + " is listed as " + entry.normalizedListType()
                + " | " + entry.category()
                + " | " + entry.severity()
                + " | " + reason);

        Server activeServer = server;
        if (activeServer == null) {
            return;
        }

        for (ServerPlayer online : activeServer.onlinePlayers()) {
            if (online.hasPermission("obsidianwatch.notify") || online.hasPermission("obsidianwatch.admin")) {
                online.sendMessage(message);
            }
        }
    }

    private String buildReason(WatchEntry entry) {
        if (!isBlank(entry.reasonPublic())) {
            return entry.reasonPublic();
        }
        return "Listed as " + entry.normalizedListType()
                + " for " + entry.category()
                + " with severity " + entry.severity() + ".";
    }

    private String buildEnforcementMessage(ServerPlayer player, WatchEntry entry, String action) {
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
                .replace("{player}", player.name())
                .replace("{uuid}", player.uniqueId().toString())
                .replace("{list}", entry.normalizedListType())
                .replace("{category}", entry.category())
                .replace("{severity}", entry.severity())
                .replace("{action}", action)
                .replace("{reason}", buildReason(entry))
                .replace("{appeal_url}", settings.appealUrl)
                .replace("{tempban_duration}", formatDuration(settings.tempbanDurationSeconds));
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

            JsonObject root = JsonParser.parseString(response.body).getAsJsonObject();
            if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
                lastStatus = "Server config returned ok=false.";
                return;
            }

            JsonObject configRoot = root.has("config") && root.get("config").isJsonObject()
                    ? root.getAsJsonObject("config")
                    : root;

            JsonObject actions = child(configRoot, "actions");
            JsonObject sync = child(configRoot, "sync");
            JsonObject safety = child(configRoot, "safety");
            JsonObject messages = child(configRoot, "messages");

            RemoteSettings previous = remoteSettings;
            RemoteSettings next = new RemoteSettings(
                    true,
                    getBoolean(actions, "notify_ops", getBoolean(actions, "notifyOps", getBoolean("notifyOps", true))),
                    getString(actions, "watchlist_action", getString(actions, "watchlistAction", getString("watchlistAction", "NOTIFY"))),
                    getString(actions, "confirmed_action", getString(actions, "confirmedAction", getString("confirmedAction", "BAN_DRY_RUN"))),
                    getBoolean(actions, "post_action_logs", getBoolean(actions, "postActionLogs", getBoolean("postActionLogs", true))),
                    getBoolean(actions, "allow_real_bans", getBoolean(actions, "allowRealBans", getBoolean("allowRealBans", false))),
                    getInt(sync, "sync_interval_seconds", getInt("syncIntervalSeconds", 120, 60, 3600), 60, 3600),
                    getInt(safety, "join_action_cooldown_seconds", getInt("joinActionCooldownSeconds", 300, 0, 86400), 0, 86400),
                    getInt(safety, "tempban_duration_seconds", getInt("tempbanDurationSeconds", 604800, 60, 2592000), 60, 2592000),
                    getBoolean(safety, "exempt_ops", getBoolean(safety, "exemptOps", getBoolean("exemptOps", true))),
                    getString(messages, "appeal_url", getString(messages, "appealUrl", getString("appealUrl", ""))),
                    getString(messages, "watchlist_kick_message", getString(messages, "watchlistKickMessage", getString("watchlistKickMessage", ""))),
                    getString(messages, "confirmed_kick_message", getString(messages, "confirmedKickMessage", getString("confirmedKickMessage", ""))),
                    getString(messages, "tempban_message", getString(messages, "tempbanMessage", getString("tempbanMessage", ""))),
                    getString(messages, "ban_message", getString(messages, "banMessage", getString("banMessage", "")))
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
            payload.addProperty("minecraft_version", "sponge-api-12");
            payload.addProperty("server_brand", getString("serverBrand", "sponge"));
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

            JsonObject root = JsonParser.parseString(response.body).getAsJsonObject();
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
        int severityScore = "critical".equals(severity) ? 40 : "high".equals(severity) ? 30 : "medium".equals(severity) ? 20 : "low".equals(severity) ? 10 : 0;
        String action = safe(entry.actionRecommended()).toLowerCase(Locale.ROOT);
        int actionScore = "ban".equals(action) ? 5 : "tempban".equals(action) ? 4 : "kick".equals(action) ? 3 : "notify".equals(action) ? 2 : 0;
        return listScore + severityScore + actionScore;
    }

    private void postActionLog(final ServerPlayer player, final WatchEntry entry, final String action, final String reason) {
        if (!currentSettings().postActionLogs || !isApiReady()) {
            return;
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("java_uuid", player.uniqueId().toString());
                    payload.addProperty("username", player.name());
                    payload.addProperty("action", action.toLowerCase(Locale.ROOT));
                    payload.addProperty("reason", reason);
                    payload.addProperty("list_type", entry == null ? "unknown" : entry.normalizedListType());
                    postJson("/api/v1/mod/action-log", payload);
                } catch (Exception exception) {
                    lastStatus = "Action log failed: " + exception.getMessage();
                }
            }
        }, "ObsidianWatch-Sponge-ActionLog");
        thread.setDaemon(true);
        thread.start();
    }

    private void postDiagnostic(String eventType, String status, String message, Integer snapshotEntryCount) {
        if (!getBoolean("enabled", true) || isBlank(apiKey())) {
            return;
        }

        try {
            RemoteSettings settings = currentSettings();
            JsonObject payload = new JsonObject();
            payload.addProperty("event_type", eventType);
            payload.addProperty("status", status);
            payload.addProperty("message", message == null ? "" : message);
            payload.addProperty("mod_version", PLUGIN_VERSION);
            payload.addProperty("minecraft_version", "sponge-api-12");
            payload.addProperty("server_brand", getString("serverBrand", "sponge"));
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

    private void loadConfig() throws Exception {
        Files.createDirectories(dataDirectory);
        Path file = dataDirectory.resolve("obsidianwatch.properties");
        if (!Files.exists(file)) {
            InputStream input = getClass().getClassLoader().getResourceAsStream("obsidianwatch.properties");
            try {
                if (input != null) {
                    Files.copy(input, file);
                }
            } finally {
                if (input != null) {
                    input.close();
                }
            }
        }

        config.clear();
        InputStream input = Files.newInputStream(file);
        try {
            config.load(input);
        } finally {
            input.close();
        }
    }

    private void loadExemptions() {
        localExemptions.clear();
        localExemptions.addAll(loadUuidSet(dataDirectory.resolve("local-exemptions.txt")));
    }

    private void loadLocalBlocks() {
        permanentBlocks.clear();
        permanentBlocks.addAll(loadUuidSet(dataDirectory.resolve("local-sponge-bans.txt")));
        loadTempBlocks();
    }

    private Set<UUID> loadUuidSet(Path file) {
        Set<UUID> values = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
        try {
            if (!Files.exists(file)) {
                return values;
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                UUID uuid = parseUuid(line);
                if (uuid != null) {
                    values.add(uuid);
                }
            }
        } catch (Exception ignored) {
        }
        return values;
    }

    private void saveUuidSet(Path file, Set<UUID> values) {
        try {
            Files.createDirectories(dataDirectory);
            ArrayList<String> lines = new ArrayList<String>();
            for (UUID uuid : values) {
                lines.add(uuid.toString());
            }
            Collections.sort(lines);
            BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            try {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            } finally {
                writer.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void loadTempBlocks() {
        tempBlocks.clear();
        Path file = dataDirectory.resolve("local-sponge-tempbans.tsv");
        try {
            if (!Files.exists(file)) {
                return;
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\t");
                if (parts.length < 2) {
                    continue;
                }
                UUID uuid = parseUuid(parts[0]);
                if (uuid == null) {
                    continue;
                }
                Instant expires = Instant.parse(parts[1]);
                if (expires.isAfter(Instant.now())) {
                    tempBlocks.put(uuid, expires);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveTempBlocks() {
        Path file = dataDirectory.resolve("local-sponge-tempbans.tsv");
        try {
            Files.createDirectories(dataDirectory);
            ArrayList<String> lines = new ArrayList<String>();
            for (Map.Entry<UUID, Instant> entry : tempBlocks.entrySet()) {
                lines.add(entry.getKey().toString() + "\t" + entry.getValue().toString());
            }
            Collections.sort(lines);
            BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            try {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            } finally {
                writer.close();
            }
        } catch (Exception ignored) {
        }
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

        Server activeServer = server;
        if (activeServer != null) {
            Optional<ServerPlayer> player = activeServer.player(query);
            if (player.isPresent()) {
                UUID uuid = player.get().uniqueId();
                return new PlayerLookup(uuid, cachedEntries.get(uuid), player.get().name(), null);
            }
        }

        return PlayerLookup.error("use a Java UUID, an online username, or a username already present in the local snapshot");
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

    private RemoteSettings currentSettings() {
        RemoteSettings settings = remoteSettings;
        if (settings == null) {
            settings = RemoteSettings.fromLocal(config);
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
        return getBoolean("enabled", true) && !isBlank(apiKey()) && !isBlank(apiBaseUrl());
    }

    private String apiBaseUrl() {
        return trimTrailingSlash(getString("apiBaseUrl", ""));
    }

    private String apiKey() {
        return getString("apiKey", "");
    }

    private String resolveServerName() {
        String configured = getString("serverName", "");
        if (!isBlank(configured)) {
            return configured.trim();
        }
        return "Sponge Server";
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
        connection.setRequestProperty("User-Agent", "ObsidianWatch-Sponge/" + PLUGIN_VERSION);
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

    private String getString(String key, String fallback) {
        String value = config.getProperty(key);
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean getBoolean(String key, boolean fallback) {
        String value = config.getProperty(key);
        if (isBlank(value)) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value.trim()) || "yes".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    private int getInt(String key, int fallback, int min, int max) {
        String value = config.getProperty(key);
        if (isBlank(value)) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException exception) {
            return fallback;
        }
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
        if (!getBoolean("enabled", true)) {
            return "disabled";
        }
        if (isBlank(apiKey())) {
            return "missing api key";
        }
        if (lastSync != null && Duration.between(lastSync, Instant.now()).getSeconds() <= Math.max(180, getInt("syncIntervalSeconds", 120, 60, 3600) * 3L)) {
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

    private String line(String key, String value) {
        return key + ": " + value;
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
        private final String appealUrl;
        private final String watchlistKickMessage;
        private final String confirmedKickMessage;
        private final String tempbanMessage;
        private final String banMessage;

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
                String appealUrl,
                String watchlistKickMessage,
                String confirmedKickMessage,
                String tempbanMessage,
                String banMessage
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
            this.appealUrl = appealUrl;
            this.watchlistKickMessage = watchlistKickMessage;
            this.confirmedKickMessage = confirmedKickMessage;
            this.tempbanMessage = tempbanMessage;
            this.banMessage = banMessage;
        }

        private static RemoteSettings fromLocal(Properties config) {
            return new RemoteSettings(
                    false,
                    booleanProperty(config, "notifyOps", true),
                    stringProperty(config, "watchlistAction", "NOTIFY"),
                    stringProperty(config, "confirmedAction", "BAN_DRY_RUN"),
                    booleanProperty(config, "postActionLogs", true),
                    booleanProperty(config, "allowRealBans", false),
                    intProperty(config, "syncIntervalSeconds", 120, 60, 3600),
                    intProperty(config, "joinActionCooldownSeconds", 300, 0, 86400),
                    intProperty(config, "tempbanDurationSeconds", 604800, 60, 2592000),
                    booleanProperty(config, "exemptOps", true),
                    stringProperty(config, "appealUrl", ""),
                    stringProperty(config, "watchlistKickMessage", ""),
                    stringProperty(config, "confirmedKickMessage", ""),
                    stringProperty(config, "tempbanMessage", ""),
                    stringProperty(config, "banMessage", "")
            );
        }

        private String enforcementSignature() {
            return watchlistAction + "|" + confirmedAction + "|" + allowRealBans + "|" + joinActionCooldownSeconds + "|" + tempbanDurationSeconds + "|" + exemptOps;
        }

        private static String stringProperty(Properties properties, String key, String fallback) {
            String value = properties.getProperty(key);
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        }

        private static boolean booleanProperty(Properties properties, String key, boolean fallback) {
            String value = properties.getProperty(key);
            if (value == null || value.trim().isEmpty()) {
                return fallback;
            }
            return "true".equalsIgnoreCase(value.trim()) || "yes".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
        }

        private static int intProperty(Properties properties, String key, int fallback, int min, int max) {
            String value = properties.getProperty(key);
            if (value == null || value.trim().isEmpty()) {
                return fallback;
            }
            try {
                int parsed = Integer.parseInt(value.trim());
                return Math.max(min, Math.min(max, parsed));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
    }
}
