package com.morethancore.obsidianwatch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ObsidianWatchService {
    private static final Gson GSON = new Gson();
    private static final String MOD_VERSION = "0.2.0";
    private static final String MINECRAFT_VERSION = "1.21.1";
    private static final String DEFAULT_WATCHLIST_KICK_MESSAGE = "You were removed by Obsidian Watch.\n\nStatus: {list}\nCategory: {category}\nSeverity: {severity}\nReason: {reason}\nAppeal: {appeal_url}";
    private static final String DEFAULT_CONFIRMED_KICK_MESSAGE = "You were removed by Obsidian Watch.\n\nStatus: {list}\nCategory: {category}\nSeverity: {severity}\nReason: {reason}\nAppeal: {appeal_url}";
    private static final String DEFAULT_TEMPBAN_MESSAGE = "You are temporarily banned by Obsidian Watch.\n\nStatus: {list}\nCategory: {category}\nSeverity: {severity}\nDuration: {tempban_duration}\nReason: {reason}\nAppeal: {appeal_url}";
    private static final String DEFAULT_BAN_MESSAGE = "You are banned by Obsidian Watch.\n\nStatus: {list}\nCategory: {category}\nSeverity: {severity}\nReason: {reason}\nAppeal: {appeal_url}";

    private final Map<UUID, WatchEntry> cachedEntries = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> recentJoinActions = new ConcurrentHashMap<>();
    private final Set<UUID> localExemptions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> disconnectsInProgress = ConcurrentHashMap.newKeySet();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final AtomicReference<String> lastStatus = new AtomicReference<>("Not started.");
    private final AtomicReference<Instant> lastHeartbeat = new AtomicReference<>();
    private final AtomicReference<Instant> lastSync = new AtomicReference<>();
    private final AtomicReference<Instant> lastConfigSync = new AtomicReference<>();
    private final AtomicReference<RemoteSettings> remoteSettings = new AtomicReference<>();

    private ScheduledExecutorService executor;
    private MinecraftServer server;

    public void onServerStarted(ServerStartedEvent event) {
        this.server = event.getServer();
        remoteSettings.set(RemoteSettings.fromLocal());
        loadExemptionsFromConfig();

        if (!OwConfig.ENABLED.get()) {
            lastStatus.set("Disabled in config.");
            return;
        }

        if (OwConfig.API_KEY.get().isBlank()) {
            lastStatus.set("Enabled, but apiKey is blank.");
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ObsidianWatch-Api");
            thread.setDaemon(true);
            return thread;
        });

        int interval = Math.max(60, OwConfig.SYNC_INTERVAL_SECONDS.get());
        executor.scheduleWithFixedDelay(() -> {
            syncServerConfig();
            sendHeartbeat();
            syncSnapshot();
        }, 5, interval, TimeUnit.SECONDS);

        lastStatus.set("Started. First sync scheduled.");
    }

    public void onServerStopping(ServerStoppingEvent event) {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        server = null;
        cachedEntries.clear();
        recentJoinActions.clear();
        localExemptions.clear();
        disconnectsInProgress.clear();
        remoteSettings.set(null);
        lastConfigSync.set(null);
        lastStatus.set("Stopped.");
    }

    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!OwConfig.ENABLED.get()) {
            return;
        }

        WatchEntry entry = cachedEntries.get(player.getUUID());
        if (entry == null) {
            return;
        }

        MinecraftServer activeServer = player.server;
        activeServer.execute(() -> handleListedPlayer(activeServer, player, entry));
    }

    public void sendHeartbeatAsync() {
        runAsync(this::sendHeartbeat);
    }

    public void syncSnapshotAsync() {
        runAsync(this::syncSnapshot);
    }

    public void syncAllAsync() {
        runAsync(() -> {
            syncServerConfig();
            syncSnapshot();
        });
    }

    public void syncServerConfigAsync() {
        runAsync(this::syncServerConfig);
    }

    public String getStatusSummary() {
        return "Status=" + lastStatus.get()
                + ", entries=" + cachedEntries.size()
                + ", exemptions=" + localExemptions.size();
    }

    public Component buildStatusMessage() {
        RemoteSettings settings = currentSettings();
        String health = healthLabel();
        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch"),
                ObsidianWatchText.line("Status", health, ObsidianWatchText.statusColor(health)),
                ObsidianWatchText.line("Server", resolveServerName()),
                ObsidianWatchText.line("Entries", String.valueOf(cachedEntries.size()), ChatFormatting.AQUA),
                ObsidianWatchText.line("Config", ObsidianWatchText.ago(lastConfigSync.get())),
                ObsidianWatchText.line("Snapshot", ObsidianWatchText.ago(lastSync.get())),
                ObsidianWatchText.line("Heartbeat", ObsidianWatchText.ago(lastHeartbeat.get())),
                ObsidianWatchText.line("Actions", settings.watchlistAction().name() + " / " + effectiveConfirmedAction(settings).name()),
                ObsidianWatchText.line("Remote config", settings.loaded() ? "synced" : "local fallback", settings.loaded() ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
        );
    }

    public Component buildDiagnosticsMessage() {
        RemoteSettings settings = currentSettings();
        String apiKeyState = OwConfig.API_KEY.get().isBlank() ? "missing" : "configured";
        String apiState = OwConfig.ENABLED.get() && !OwConfig.API_KEY.get().isBlank() ? "ready" : "not ready";
        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch Diagnostics"),
                ObsidianWatchText.section("API"),
                ObsidianWatchText.line("Enabled", OwConfig.ENABLED.get() ? "yes" : "no", OwConfig.ENABLED.get() ? ChatFormatting.GREEN : ChatFormatting.RED),
                ObsidianWatchText.line("State", apiState, "ready".equals(apiState) ? ChatFormatting.GREEN : ChatFormatting.RED),
                ObsidianWatchText.line("Base URL", sanitizedBaseUrl()),
                ObsidianWatchText.line("API key", apiKeyState, "configured".equals(apiKeyState) ? ChatFormatting.GREEN : ChatFormatting.RED),
                ObsidianWatchText.section("Cache"),
                ObsidianWatchText.line("Entries", String.valueOf(cachedEntries.size()), ChatFormatting.AQUA),
                ObsidianWatchText.line("Local exemptions", String.valueOf(localExemptions.size())),
                ObsidianWatchText.line("Join cooldown", settings.joinActionCooldownSeconds() + "s"),
                ObsidianWatchText.section("Remote config"),
                ObsidianWatchText.line("Loaded", settings.loaded() ? "yes" : "local fallback", settings.loaded() ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                ObsidianWatchText.line("Watchlist action", settings.watchlistAction().name()),
                ObsidianWatchText.line("Confirmed action", effectiveConfirmedAction(settings).name()),
                ObsidianWatchText.line("Tempban duration", formatDuration(settings.tempbanDurationSeconds())),
                ObsidianWatchText.line("Real bans", settings.allowRealBans() ? "enabled" : "blocked", settings.allowRealBans() ? ChatFormatting.RED : ChatFormatting.GREEN),
                ObsidianWatchText.line("Last status", lastStatus.get())
        );
    }

    public Component buildCheckMessage(String playerRaw, String usernameRaw) {
        PlayerLookup lookup = resolvePlayerLookup(playerRaw);
        if (lookup.error() != null) {
            return ObsidianWatchText.join(
                    ObsidianWatchText.title("Obsidian Watch"),
                    ObsidianWatchText.line("Check", lookup.error(), ChatFormatting.RED)
            );
        }

        UUID uuid = lookup.uuid();
        WatchEntry entry = lookup.entry();
        String displayName = usernameRaw == null || usernameRaw.isBlank()
                ? lookup.displayName()
                : usernameRaw.trim();

        if (entry == null) {
            return ObsidianWatchText.join(
                    ObsidianWatchText.title("Obsidian Watch: No Match"),
                    ObsidianWatchText.line("Player", displayName),
                    ObsidianWatchText.line("UUID", uuid == null ? "unknown" : uuid.toString()),
                    ObsidianWatchText.line("Snapshot entries", String.valueOf(cachedEntries.size()), ChatFormatting.AQUA),
                    ObsidianWatchText.line("Result", "not found in local snapshot", ChatFormatting.GREEN)
            );
        }

        String action = configuredActionFor(entry);
        String reason = buildReason(entry);
        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch: Player Match"),
                ObsidianWatchText.line("Player", displayName),
                ObsidianWatchText.line("UUID", uuid == null ? entry.javaUuid() : uuid.toString()),
                ObsidianWatchText.line("Status", ObsidianWatchText.titleCase(entry.normalizedListType()), ObsidianWatchText.listColor(entry)),
                ObsidianWatchText.line("Category", ObsidianWatchText.titleCase(entry.category()), ChatFormatting.AQUA),
                ObsidianWatchText.line("Severity", ObsidianWatchText.titleCase(entry.severity()), ChatFormatting.GOLD),
                ObsidianWatchText.line("Action", action, ChatFormatting.GREEN),
                ObsidianWatchText.line("Reason", reason)
        );
    }

    public int cachedEntryCount() {
        return cachedEntries.size();
    }

    public Component queueTestActionLog(String playerRaw, String actionRaw, String usernameRaw) {
        PlayerLookup lookup = resolvePlayerLookup(playerRaw);
        if (lookup.error() != null) {
            return ObsidianWatchText.join(
                    ObsidianWatchText.title("Obsidian Watch"),
                    ObsidianWatchText.line("Test log", lookup.error(), ChatFormatting.RED)
            );
        }

        UUID uuid = lookup.uuid();
        WatchEntry entry = lookup.entry();
        String action = actionRaw == null ? "" : actionRaw.trim().toLowerCase(Locale.ROOT);
        if (!isAllowedDiagnosticAction(action)) {
            return ObsidianWatchText.join(
                    ObsidianWatchText.title("Obsidian Watch"),
                    ObsidianWatchText.line("Invalid action", "notify, kick, tempban, ban_dry_run, ban, exempted", ChatFormatting.RED)
            );
        }

        if (!isApiReady()) {
            return ObsidianWatchText.join(
                    ObsidianWatchText.title("Obsidian Watch"),
                    ObsidianWatchText.line("API", "not ready", ChatFormatting.RED),
                    ObsidianWatchText.line("Status", lastStatus.get())
            );
        }

        String username = usernameRaw == null || usernameRaw.isBlank() ? lookup.displayName() : usernameRaw.trim();
        String listType = entry == null ? "diagnostic" : entry.normalizedListType();
        String reason = entry == null
                ? "Diagnostic Obsidian Watch test action. Player was not in local snapshot."
                : "Diagnostic Obsidian Watch test action for " + entry.normalizedListType() + " entry: " + buildReason(entry);

        runAsync(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("java_uuid", uuid.toString());
                payload.addProperty("username", username);
                payload.addProperty("action", action);
                payload.addProperty("reason", reason);
                payload.addProperty("list_type", listType);

                HttpResponse<String> response = postJson("/api/v1/mod/action-log", payload);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    lastStatus.set("Test action log accepted for " + username + ".");
                } else {
                    lastStatus.set("Test action log failed HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception exception) {
                lastStatus.set("Test action log failed: " + exception.getMessage());
            }
        });

        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch: Test Action Log"),
                ObsidianWatchText.line("Player", username),
                ObsidianWatchText.line("UUID", uuid.toString()),
                ObsidianWatchText.line("Action", action.toUpperCase(Locale.ROOT), ChatFormatting.GREEN),
                ObsidianWatchText.line("List type", listType),
                ObsidianWatchText.line("State", "queued", ChatFormatting.GREEN)
        );
    }

    public Component addLocalExemption(String uuidRaw) {
        Optional<UUID> parsed = parseUuid(uuidRaw);
        if (parsed.isEmpty()) {
            return ObsidianWatchText.join(
                    ObsidianWatchText.title("Obsidian Watch"),
                    ObsidianWatchText.line("Exemption", "invalid Java UUID", ChatFormatting.RED),
                    ObsidianWatchText.line("Input", String.valueOf(uuidRaw))
            );
        }

        UUID uuid = parsed.get();
        boolean added = localExemptions.add(uuid);
        saveExemptionsToConfig();

        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch: Local Exemption"),
                ObsidianWatchText.line("UUID", uuid.toString()),
                ObsidianWatchText.line("State", added ? "added" : "already exempt", added ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                ObsidianWatchText.line("Stored in", "localExemptUuids")
        );
    }

    public Component removeLocalExemption(String uuidRaw) {
        Optional<UUID> parsed = parseUuid(uuidRaw);
        if (parsed.isEmpty()) {
            return ObsidianWatchText.join(
                    ObsidianWatchText.title("Obsidian Watch"),
                    ObsidianWatchText.line("Exemption", "invalid Java UUID", ChatFormatting.RED),
                    ObsidianWatchText.line("Input", String.valueOf(uuidRaw))
            );
        }

        UUID uuid = parsed.get();
        boolean removed = localExemptions.remove(uuid);
        saveExemptionsToConfig();

        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch: Local Exemption"),
                ObsidianWatchText.line("UUID", uuid.toString()),
                ObsidianWatchText.line("State", removed ? "removed" : "not exempt", removed ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
        );
    }

    public Component listLocalExemptions() {
        loadExemptionsFromConfig();
        if (localExemptions.isEmpty()) {
            return ObsidianWatchText.join(
                    ObsidianWatchText.title("Obsidian Watch: Local Exemptions"),
                    ObsidianWatchText.line("Count", "0"),
                    ObsidianWatchText.line("Result", "no local exemptions configured")
            );
        }

        String list = localExemptions.stream()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining("\n"));

        return ObsidianWatchText.join(
                ObsidianWatchText.title("Obsidian Watch: Local Exemptions"),
                ObsidianWatchText.line("Count", String.valueOf(localExemptions.size()), ChatFormatting.AQUA),
                Component.literal(list).withStyle(ChatFormatting.GRAY)
        );
    }

    private PlayerLookup resolvePlayerLookup(String playerRaw) {
        if (playerRaw == null || playerRaw.isBlank()) {
            return PlayerLookup.error("missing player UUID or username");
        }

        String query = playerRaw.trim();
        Optional<UUID> parsed = parseUuid(query);
        if (parsed.isPresent()) {
            UUID uuid = parsed.get();
            WatchEntry entry = cachedEntries.get(uuid);
            String displayName = entry != null && entry.currentUsername() != null && !entry.currentUsername().isBlank()
                    ? entry.currentUsername()
                    : uuid.toString();
            return new PlayerLookup(uuid, entry, displayName, null);
        }

        for (Map.Entry<UUID, WatchEntry> cacheEntry : cachedEntries.entrySet()) {
            WatchEntry entry = cacheEntry.getValue();
            if (entry.currentUsername() != null && entry.currentUsername().equalsIgnoreCase(query)) {
                return new PlayerLookup(cacheEntry.getKey(), entry, entry.currentUsername(), null);
            }
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
            return settings.watchlistAction().name();
        }
        if (entry.isConfirmed()) {
            return effectiveConfirmedAction(settings).name();
        }
        return "UNKNOWN";
    }

    private void runAsync(Runnable task) {
        ScheduledExecutorService activeExecutor = executor;
        if (activeExecutor != null && !activeExecutor.isShutdown()) {
            activeExecutor.execute(task);
            return;
        }

        Thread fallback = new Thread(task, "ObsidianWatch-ManualApi");
        fallback.setDaemon(true);
        fallback.start();
    }

    private void handleListedPlayer(MinecraftServer activeServer, ServerPlayer player, WatchEntry entry) {
        String listType = entry.normalizedListType();
        String reason = buildReason(entry);

        if (isPlayerExempt(player)) {
            if (markJoinActionIfAllowed(player.getUUID())) {
                postActionLog(player, entry, "exempted", "Local Obsidian Watch exemption skipped configured action: " + reason);
            }
            return;
        }

        if (!markJoinActionIfAllowed(player.getUUID())) {
            return;
        }

        RemoteSettings settings = currentSettings();

        if (entry.isWatchlist()) {
            OwConfig.WatchlistAction action = settings.watchlistAction();
            if (action == OwConfig.WatchlistAction.NONE) {
                return;
            }
            notifyOps(activeServer, player, entry, reason);
            postActionLog(player, entry, action == OwConfig.WatchlistAction.KICK ? "kick" : "notify", reason);
            if (action == OwConfig.WatchlistAction.KICK) {
                disconnectPlayer(activeServer, player.getUUID(), buildEnforcementMessage(player, entry, "kick"));
            }
            return;
        }

        if (entry.isConfirmed()) {
            OwConfig.ConfirmedAction action = effectiveConfirmedAction(settings);
            if (action == OwConfig.ConfirmedAction.NONE) {
                return;
            }
            notifyOps(activeServer, player, entry, reason);

            switch (action) {
                case NOTIFY -> postActionLog(player, entry, "notify", reason);
                case KICK -> {
                    postActionLog(player, entry, "kick", reason);
                    disconnectPlayer(activeServer, player.getUUID(), buildEnforcementMessage(player, entry, "kick"));
                }
                case TEMPBAN -> {
                    postActionLog(player, entry, "tempban", reason + " Temporary ban duration: " + formatDuration(settings.tempbanDurationSeconds()) + ".");
                    tempBanPlayer(activeServer, player, buildEnforcementMessage(player, entry, "tempban"), settings.tempbanDurationSeconds());
                }
                case BAN_DRY_RUN -> postActionLog(player, entry, "ban_dry_run", "BAN DRY-RUN: would permanently ban this player. " + reason);
                case BAN -> {
                    postActionLog(player, entry, "ban", reason);
                    banPlayer(activeServer, player, buildEnforcementMessage(player, entry, "ban"));
                }
                default -> {
                }
            }
            return;
        }

        notifyOps(activeServer, player, entry, "Obsidian Watch listed player joined with unknown list type: " + listType);
    }

    private boolean isPlayerExempt(ServerPlayer player) {
        loadExemptionsFromConfig();
        if (localExemptions.contains(player.getUUID())) {
            return true;
        }
        RemoteSettings settings = currentSettings();
        if (settings.exemptOps() && player.hasPermissions(2)) {
            return true;
        }
        return settings.exemptCreative() && player.isCreative();
    }

    private boolean markJoinActionIfAllowed(UUID uuid) {
        int cooldownSeconds = Math.max(0, currentSettings().joinActionCooldownSeconds());
        if (cooldownSeconds <= 0) {
            return true;
        }

        Instant now = Instant.now();
        Instant previous = recentJoinActions.get(uuid);
        if (previous != null && Duration.between(previous, now).getSeconds() < cooldownSeconds) {
            long remaining = cooldownSeconds - Duration.between(previous, now).getSeconds();
            lastStatus.set("Join action cooldown skipped for " + uuid + " (" + remaining + "s remaining).");
            return false;
        }

        recentJoinActions.put(uuid, now);
        recentJoinActions.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).getSeconds() > Math.max(cooldownSeconds * 4L, 3600L));
        return true;
    }

    private void disconnectPlayer(MinecraftServer activeServer, UUID uuid, String message) {
        String safeMessage = normalizeDisconnectMessage(message);
        if (!disconnectsInProgress.add(uuid)) {
            lastStatus.set("Disconnect already queued for " + uuid + ".");
            return;
        }

        Runnable disconnectTask = () -> activeServer.execute(() -> {
            try {
                ServerPlayer target = activeServer.getPlayerList().getPlayer(uuid);
                if (target != null && target.connection != null) {
                    target.connection.disconnect(Component.literal(safeMessage));
                    lastStatus.set("Disconnect sent for " + target.getGameProfile().getName() + ".");
                } else {
                    lastStatus.set("Disconnect skipped because player was already offline: " + uuid + ".");
                }
            } catch (Exception exception) {
                lastStatus.set("Disconnect failed for " + uuid + ": " + exception.getMessage());
            } finally {
                releaseDisconnectGuardLater(uuid);
            }
        });

        ScheduledExecutorService activeExecutor = executor;
        if (activeExecutor != null && !activeExecutor.isShutdown()) {
            activeExecutor.schedule(disconnectTask, 750, TimeUnit.MILLISECONDS);
        } else {
            activeServer.execute(disconnectTask);
        }
    }

    private void releaseDisconnectGuardLater(UUID uuid) {
        ScheduledExecutorService activeExecutor = executor;
        if (activeExecutor != null && !activeExecutor.isShutdown()) {
            activeExecutor.schedule(() -> disconnectsInProgress.remove(uuid), 5, TimeUnit.SECONDS);
        } else {
            disconnectsInProgress.remove(uuid);
        }
    }

    private String normalizeDisconnectMessage(String message) {
        String normalized = message == null ? "" : message
                .replace("\\n", "\n")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();

        if (normalized.isBlank()) {
            normalized = "You were removed by Obsidian Watch.\n\nAppeal: " + defaultAppealUrl();
        }

        if (normalized.length() > 1800) {
            normalized = normalized.substring(0, 1800).trim() + "\n\nAppeal: " + defaultAppealUrl();
        }

        return normalized;
    }

    private void banPlayer(MinecraftServer activeServer, ServerPlayer player, String reason) {
        GameProfile profile = player.getGameProfile();
        UserBanList banList = activeServer.getPlayerList().getBans();
        UserBanListEntry banEntry = new UserBanListEntry(profile, new Date(), "Obsidian Watch", null, reason);
        banList.add(banEntry);
        disconnectPlayer(activeServer, player.getUUID(), reason);
    }

    private void tempBanPlayer(MinecraftServer activeServer, ServerPlayer player, String reason, int durationSeconds) {
        int safeDuration = Math.max(60, Math.min(2592000, durationSeconds));
        Date expiresAt = new Date(System.currentTimeMillis() + safeDuration * 1000L);
        GameProfile profile = player.getGameProfile();
        UserBanList banList = activeServer.getPlayerList().getBans();
        UserBanListEntry banEntry = new UserBanListEntry(profile, new Date(), "Obsidian Watch", expiresAt, reason);
        banList.add(banEntry);
        disconnectPlayer(activeServer, player.getUUID(), reason);
    }

    private void notifyOps(MinecraftServer activeServer, ServerPlayer player, WatchEntry entry, String reason) {
        if (!currentSettings().notifyOps()) {
            return;
        }

        Component message = Component.literal("[Obsidian Watch] ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" is listed as ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(entry.normalizedListType()).withStyle(entry.isConfirmed() ? ChatFormatting.RED : ChatFormatting.YELLOW))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(entry.category()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(entry.severity()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(reason).withStyle(ChatFormatting.GRAY));

        for (ServerPlayer onlinePlayer : activeServer.getPlayerList().getPlayers()) {
            if (onlinePlayer.hasPermissions(2)) {
                onlinePlayer.sendSystemMessage(message);
            }
        }
    }

    private String buildReason(WatchEntry entry) {
        if (entry.reasonPublic() != null && !entry.reasonPublic().isBlank()) {
            return entry.reasonPublic();
        }
        String listType = entry.normalizedListType().isBlank() ? "listed" : entry.normalizedListType();
        String category = entry.category() == null ? "unknown" : entry.category();
        return "Obsidian Watch " + listType + " entry: " + category;
    }

    private String buildEnforcementMessage(ServerPlayer player, WatchEntry entry, String action) {
        RemoteSettings settings = currentSettings();
        String template;
        if ("ban".equalsIgnoreCase(action)) {
            template = settings.banMessage();
        } else if ("tempban".equalsIgnoreCase(action)) {
            template = settings.tempbanMessage();
        } else if (entry.isConfirmed()) {
            template = settings.confirmedKickMessage();
        } else {
            template = settings.watchlistKickMessage();
        }

        if (template == null || template.isBlank()) {
            template = "ban".equalsIgnoreCase(action) ? DEFAULT_BAN_MESSAGE
                    : "tempban".equalsIgnoreCase(action) ? DEFAULT_TEMPBAN_MESSAGE
                    : entry.isConfirmed() ? DEFAULT_CONFIRMED_KICK_MESSAGE
                    : DEFAULT_WATCHLIST_KICK_MESSAGE;
        }

        String category = settings.showCategorySeverity() ? safeValue(entry.category(), "unknown") : "hidden";
        String severity = settings.showCategorySeverity() ? safeValue(entry.severity(), "unknown") : "hidden";
        String reason = settings.showPublicReason() ? buildReason(entry) : "Details are available through the appeal process.";
        String serverName = resolveServerName();

        String message = template
                .replace("\\n", "\n")
                .replace("{player}", player.getGameProfile().getName())
                .replace("{uuid}", player.getUUID().toString())
                .replace("{list}", safeValue(entry.normalizedListType(), "listed"))
                .replace("{category}", category)
                .replace("{severity}", severity)
                .replace("{reason}", reason)
                .replace("{appeal_url}", safeValue(settings.appealUrl(), defaultAppealUrl()))
                .replace("{server}", safeValue(serverName, "Minecraft Server"))
                .replace("{tempban_duration}", formatDuration(settings.tempbanDurationSeconds()))
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();

        if (message.isBlank()) {
            return "Removed by Obsidian Watch. Appeal: " + defaultAppealUrl();
        }
        return message;
    }

    private String defaultAppealUrl() {
        String base = OwConfig.BASE_URL.get();
        if (base == null || base.isBlank()) {
            return "http://127.0.0.1:8080/appeals";
        }
        base = base.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/appeals";
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void syncServerConfig() {
        if (!isApiReady()) {
            return;
        }

        try {
            HttpResponse<String> response = getJson("/api/v1/server/config");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                lastStatus.set("Server config failed HTTP " + response.statusCode() + ": " + response.body());
                postDiagnostic("config", "error", "Server config failed HTTP " + response.statusCode(), null);
                return;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
                lastStatus.set("Server config returned ok=false.");
                postDiagnostic("config", "error", "Server config returned ok=false.", null);
                return;
            }

            JsonObject actions = root.has("actions") && root.get("actions").isJsonObject()
                    ? root.getAsJsonObject("actions")
                    : new JsonObject();
            JsonObject safety = root.has("safety") && root.get("safety").isJsonObject()
                    ? root.getAsJsonObject("safety")
                    : new JsonObject();
            JsonObject sync = root.has("sync") && root.get("sync").isJsonObject()
                    ? root.getAsJsonObject("sync")
                    : new JsonObject();
            JsonObject messages = root.has("messages") && root.get("messages").isJsonObject()
                    ? root.getAsJsonObject("messages")
                    : new JsonObject();

            RemoteSettings previous = remoteSettings.get();
            RemoteSettings next = new RemoteSettings(
                    true,
                    getBoolean(actions, "notify_ops", OwConfig.NOTIFY_OPS.get()),
                    parseWatchlistAction(getString(actions, "watchlist_action"), OwConfig.WATCHLIST_ACTION.get()),
                    parseConfirmedAction(getString(actions, "confirmed_action"), OwConfig.CONFIRMED_ACTION.get()),
                    getBoolean(actions, "post_action_logs", OwConfig.POST_ACTION_LOGS.get()),
                    getBoolean(actions, "allow_real_bans", OwConfig.ALLOW_REAL_BANS.get()),
                    getInt(sync, "sync_interval_seconds", Math.max(60, OwConfig.SYNC_INTERVAL_SECONDS.get()), 60, 3600),
                    getInt(safety, "join_action_cooldown_seconds", OwConfig.JOIN_ACTION_COOLDOWN_SECONDS.get(), 0, 86400),
                    getInt(safety, "tempban_duration_seconds", OwConfig.TEMPBAN_DURATION_SECONDS.get(), 60, 2592000),
                    getBoolean(safety, "exempt_ops", OwConfig.EXEMPT_OPS.get()),
                    getBoolean(safety, "exempt_creative", OwConfig.EXEMPT_CREATIVE.get()),
                    getString(messages, "appeal_url", defaultAppealUrl()),
                    getString(messages, "watchlist_kick_message", DEFAULT_WATCHLIST_KICK_MESSAGE),
                    getString(messages, "confirmed_kick_message", DEFAULT_CONFIRMED_KICK_MESSAGE),
                    getString(messages, "tempban_message", DEFAULT_TEMPBAN_MESSAGE),
                    getString(messages, "ban_message", DEFAULT_BAN_MESSAGE),
                    getBoolean(messages, "show_category_severity", true),
                    getBoolean(messages, "show_public_reason", true)
            );

            remoteSettings.set(next);
            if (previous == null || !previous.enforcementSignature().equals(next.enforcementSignature())) {
                recentJoinActions.clear();
            }
            lastConfigSync.set(Instant.now());
            lastStatus.set("Server config synced: " + next.watchlistAction().name() + "/" + effectiveConfirmedAction(next).name() + ", cooldown=" + next.joinActionCooldownSeconds() + "s, tempban=" + formatDuration(next.tempbanDurationSeconds()) + ", realBans=" + (next.allowRealBans() ? "enabled" : "blocked") + ".");
            postDiagnostic("config", "ok", "Server config synced: " + next.watchlistAction().name() + "/" + effectiveConfirmedAction(next).name() + ", cooldown=" + next.joinActionCooldownSeconds() + "s, tempban=" + formatDuration(next.tempbanDurationSeconds()) + ", realBans=" + (next.allowRealBans() ? "enabled" : "blocked") + ".", null);
        } catch (Exception exception) {
            lastStatus.set("Server config failed: " + exception.getMessage());
            postDiagnostic("config", "error", "Server config failed: " + exception.getMessage(), null);
        }
    }

    private void sendHeartbeat() {
        if (!isApiReady()) {
            return;
        }

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("mod_version", MOD_VERSION);
            payload.addProperty("minecraft_version", MINECRAFT_VERSION);
            payload.addProperty("server_brand", OwConfig.SERVER_BRAND.get());
            payload.addProperty("server_name", resolveServerName());

            HttpResponse<String> response = postJson("/api/v1/server/heartbeat", payload);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                lastHeartbeat.set(Instant.now());
                lastStatus.set("Heartbeat accepted.");
            } else {
                lastStatus.set("Heartbeat failed HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception exception) {
            lastStatus.set("Heartbeat failed: " + exception.getMessage());
        }
    }

    private void syncSnapshot() {
        if (!isApiReady()) {
            return;
        }

        try {
            HttpResponse<String> response = getJson("/api/v1/lists/snapshot");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                lastStatus.set("Snapshot failed HTTP " + response.statusCode() + ": " + response.body());
                postDiagnostic("snapshot", "error", "Snapshot failed HTTP " + response.statusCode(), null);
                return;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
                lastStatus.set("Snapshot returned ok=false.");
                postDiagnostic("snapshot", "error", "Snapshot returned ok=false.", null);
                return;
            }

            JsonArray entries = root.has("entries") && root.get("entries").isJsonArray()
                    ? root.getAsJsonArray("entries")
                    : new JsonArray();

            Map<UUID, WatchEntry> next = new ConcurrentHashMap<>();
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                String uuidRaw = getString(obj, "java_uuid");
                if (uuidRaw.isBlank()) {
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(uuidRaw);
                    WatchEntry entry = new WatchEntry(
                            uuidRaw,
                            getString(obj, "current_username"),
                            getString(obj, "list_type"),
                            getString(obj, "category"),
                            getString(obj, "severity"),
                            getString(obj, "action_recommended"),
                            getString(obj, "reason_public")
                    );
                    WatchEntry existing = next.get(uuid);
                    if (existing == null || shouldReplaceEntry(existing, entry)) {
                        next.put(uuid, entry);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            cachedEntries.clear();
            cachedEntries.putAll(next);
            lastSync.set(Instant.now());
            lastStatus.set("Snapshot synced " + cachedEntries.size() + " entries.");
            postDiagnostic("snapshot", "ok", "Snapshot synced " + cachedEntries.size() + " entries.", cachedEntries.size());
        } catch (Exception exception) {
            lastStatus.set("Snapshot failed: " + exception.getMessage());
            postDiagnostic("snapshot", "error", "Snapshot failed: " + exception.getMessage(), null);
        }
    }

    private boolean shouldReplaceEntry(WatchEntry existing, WatchEntry candidate) {
        return entryPriority(candidate) > entryPriority(existing);
    }

    private int entryPriority(WatchEntry entry) {
        int listScore = entry.isConfirmed() ? 1000 : entry.isWatchlist() ? 500 : 0;
        int severityScore = switch (safeValue(entry.severity(), "").toLowerCase(Locale.ROOT)) {
            case "critical" -> 40;
            case "high" -> 30;
            case "medium" -> 20;
            case "low" -> 10;
            default -> 0;
        };
        int actionScore = switch (safeValue(entry.actionRecommended(), "").toLowerCase(Locale.ROOT)) {
            case "ban" -> 5;
            case "tempban" -> 4;
            case "kick" -> 3;
            case "notify" -> 2;
            default -> 0;
        };
        return listScore + severityScore + actionScore;
    }

    private void postActionLog(ServerPlayer player, WatchEntry entry, String action, String reason) {
        if (!currentSettings().postActionLogs() || !isApiReady()) {
            return;
        }

        runAsync(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("java_uuid", player.getUUID().toString());
                payload.addProperty("username", player.getGameProfile().getName());
                payload.addProperty("action", action.toLowerCase(Locale.ROOT));
                payload.addProperty("reason", reason);
                payload.addProperty("list_type", entry.normalizedListType());
                postJson("/api/v1/mod/action-log", payload);
            } catch (Exception exception) {
                lastStatus.set("Action log failed: " + exception.getMessage());
            }
        });
    }

    private void postDiagnostic(String eventType, String status, String message, Integer snapshotEntryCount) {
        if (!OwConfig.ENABLED.get() || OwConfig.API_KEY.get().isBlank()) {
            return;
        }

        try {
            RemoteSettings settings = currentSettings();
            JsonObject payload = new JsonObject();
            payload.addProperty("event_type", eventType);
            payload.addProperty("status", status);
            payload.addProperty("message", message == null ? "" : message);
            payload.addProperty("mod_version", MOD_VERSION);
            payload.addProperty("minecraft_version", MINECRAFT_VERSION);
            payload.addProperty("server_brand", OwConfig.SERVER_BRAND.get());
            payload.addProperty("watchlist_action", settings.watchlistAction().name());
            payload.addProperty("confirmed_action", effectiveConfirmedAction(settings).name());
            payload.addProperty("join_action_cooldown_seconds", settings.joinActionCooldownSeconds());
            if (snapshotEntryCount != null) {
                payload.addProperty("snapshot_entry_count", snapshotEntryCount);
            }
            postJson("/api/v1/mod/diagnostics", payload);
        } catch (Exception ignored) {
        }
    }

    private void loadExemptionsFromConfig() {
        localExemptions.clear();
        String raw = OwConfig.LOCAL_EXEMPT_UUIDS.get();
        if (raw == null || raw.isBlank()) {
            return;
        }

        for (String token : raw.split("[,\\s]+")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            parseUuid(token).ifPresent(localExemptions::add);
        }
    }

    private void saveExemptionsToConfig() {
        String value = localExemptions.stream()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining(","));
        OwConfig.LOCAL_EXEMPT_UUIDS.set(value);
        OwConfig.SPEC.save();
    }

    private Optional<UUID> parseUuid(String uuidRaw) {
        if (uuidRaw == null || uuidRaw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(uuidRaw.trim()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean isApiReady() {
        if (!OwConfig.ENABLED.get()) {
            lastStatus.set("Disabled in config.");
            return false;
        }
        if (OwConfig.API_KEY.get().isBlank()) {
            lastStatus.set("apiKey is blank.");
            return false;
        }
        return true;
    }

    private String resolveServerName() {
        String override = OwConfig.SERVER_NAME_OVERRIDE.get();
        if (override != null && !override.isBlank()) {
            return override;
        }
        MinecraftServer activeServer = server;
        if (activeServer != null) {
            return activeServer.getServerModName();
        }
        return "Minecraft Server";
    }

    private HttpResponse<String> getJson(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(resolveUri(path))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + OwConfig.API_KEY.get().trim())
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, JsonObject payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(resolveUri(path))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + OwConfig.API_KEY.get().trim())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI resolveUri(String path) {
        String base = OwConfig.BASE_URL.get().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + path);
    }

    private static OwConfig.WatchlistAction parseWatchlistAction(String raw, OwConfig.WatchlistAction fallback) {
        try {
            return OwConfig.WatchlistAction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static OwConfig.ConfirmedAction parseConfirmedAction(String raw, OwConfig.ConfirmedAction fallback) {
        try {
            return OwConfig.ConfirmedAction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject object, String name, boolean fallback) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(name).getAsBoolean();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static int getInt(JsonObject object, String name, int fallback, int min, int max) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            int value = object.get(name).getAsInt();
            return Math.max(min, Math.min(max, value));
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String getString(JsonObject object, String name) {
        return getString(object, name, "");
    }

    private static String getString(JsonObject object, String name, String fallback) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            String value = object.get(name).getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception exception) {
            return fallback;
        }
    }


    private OwConfig.ConfirmedAction effectiveConfirmedAction(RemoteSettings settings) {
        OwConfig.ConfirmedAction action = settings.confirmedAction();
        if (action == OwConfig.ConfirmedAction.BAN && !settings.allowRealBans()) {
            return OwConfig.ConfirmedAction.BAN_DRY_RUN;
        }
        return action;
    }

    private String formatDuration(int seconds) {
        int safeSeconds = Math.max(60, seconds);
        long days = safeSeconds / 86400L;
        long hours = (safeSeconds % 86400L) / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String healthLabel() {
        if (!OwConfig.ENABLED.get()) {
            return "Disabled";
        }
        if (OwConfig.API_KEY.get().isBlank()) {
            return "API key missing";
        }
        String status = lastStatus.get();
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (normalized.contains("failed") || normalized.contains("error")) {
            return "Attention required";
        }
        if (lastHeartbeat.get() != null || lastSync.get() != null) {
            return "Connected";
        }
        return "Starting";
    }

    private String sanitizedBaseUrl() {
        String base = OwConfig.BASE_URL.get();
        if (base == null || base.isBlank()) {
            return "not configured";
        }
        return base.trim();
    }

    private RemoteSettings currentSettings() {
        RemoteSettings settings = remoteSettings.get();
        return settings != null ? settings : RemoteSettings.fromLocal();
    }

    private record PlayerLookup(UUID uuid, WatchEntry entry, String displayName, String error) {
        static PlayerLookup error(String error) {
            return new PlayerLookup(null, null, "", error);
        }
    }

    private record RemoteSettings(
            boolean loaded,
            boolean notifyOps,
            OwConfig.WatchlistAction watchlistAction,
            OwConfig.ConfirmedAction confirmedAction,
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
        String enforcementSignature() {
            return watchlistAction.name() + "|"
                    + confirmedAction.name() + "|"
                    + allowRealBans + "|"
                    + joinActionCooldownSeconds + "|"
                    + tempbanDurationSeconds + "|"
                    + exemptOps + "|"
                    + exemptCreative + "|"
                    + safeSignature(watchlistKickMessage) + "|"
                    + safeSignature(confirmedKickMessage) + "|"
                    + safeSignature(tempbanMessage) + "|"
                    + safeSignature(banMessage);
        }

        static String safeSignature(String value) {
            return value == null ? "" : value.trim();
        }

        static RemoteSettings fromLocal() {
            String appealUrl = "http://127.0.0.1:8080/appeals";
            try {
                String base = OwConfig.BASE_URL.get();
                if (base != null && !base.isBlank()) {
                    while (base.endsWith("/")) {
                        base = base.substring(0, base.length() - 1);
                    }
                    appealUrl = base + "/appeals";
                }
            } catch (Exception ignored) {
            }

            return new RemoteSettings(
                    false,
                    OwConfig.NOTIFY_OPS.get(),
                    OwConfig.WATCHLIST_ACTION.get(),
                    OwConfig.CONFIRMED_ACTION.get(),
                    OwConfig.POST_ACTION_LOGS.get(),
                    OwConfig.ALLOW_REAL_BANS.get(),
                    Math.max(60, OwConfig.SYNC_INTERVAL_SECONDS.get()),
                    OwConfig.JOIN_ACTION_COOLDOWN_SECONDS.get(),
                    OwConfig.TEMPBAN_DURATION_SECONDS.get(),
                    OwConfig.EXEMPT_OPS.get(),
                    OwConfig.EXEMPT_CREATIVE.get(),
                    appealUrl,
                    DEFAULT_WATCHLIST_KICK_MESSAGE,
                    DEFAULT_CONFIRMED_KICK_MESSAGE,
                    DEFAULT_TEMPBAN_MESSAGE,
                    DEFAULT_BAN_MESSAGE,
                    true,
                    true
            );
        }
    }

}
