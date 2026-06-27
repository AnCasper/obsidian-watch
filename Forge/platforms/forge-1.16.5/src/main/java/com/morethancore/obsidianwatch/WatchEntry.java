package com.morethancore.obsidianwatch;

import java.util.Locale;

public final class WatchEntry {
    private final String javaUuid;
    private final String currentUsername;
    private final String listType;
    private final String category;
    private final String severity;
    private final String actionRecommended;
    private final String reasonPublic;

    public WatchEntry(
            String javaUuid,
            String currentUsername,
            String listType,
            String category,
            String severity,
            String actionRecommended,
            String reasonPublic
    ) {
        this.javaUuid = javaUuid;
        this.currentUsername = currentUsername;
        this.listType = listType;
        this.category = category;
        this.severity = severity;
        this.actionRecommended = actionRecommended;
        this.reasonPublic = reasonPublic;
    }

    public String javaUuid() {
        return javaUuid;
    }

    public String currentUsername() {
        return currentUsername;
    }

    public String listType() {
        return listType;
    }

    public String category() {
        return category;
    }

    public String severity() {
        return severity;
    }

    public String actionRecommended() {
        return actionRecommended;
    }

    public String reasonPublic() {
        return reasonPublic;
    }

    public boolean isConfirmed() {
        return "confirmed".equals(normalizedListType());
    }

    public boolean isWatchlist() {
        return "watchlist".equals(normalizedListType());
    }

    public String normalizedListType() {
        return listType == null ? "" : listType.trim().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        if (!isBlank(currentUsername)) {
            return currentUsername.trim();
        }
        return javaUuid == null ? "Unknown" : javaUuid;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
