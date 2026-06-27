package com.morethancore.obsidianwatch.fabric;

public final class WatchEntry {
    private final String javaUuid;
    private final String currentUsername;
    private final String listType;
    private final String category;
    private final String severity;
    private final String actionRecommended;
    private final String reasonPublic;

    public WatchEntry(String javaUuid, String currentUsername, String listType, String category, String severity, String actionRecommended, String reasonPublic) {
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
        return safe(category, "unknown");
    }

    public String severity() {
        return safe(severity, "unknown");
    }

    public String actionRecommended() {
        return safe(actionRecommended, "none");
    }

    public String reasonPublic() {
        return reasonPublic;
    }

    public String normalizedListType() {
        String normalized = safe(listType, "unknown").trim().toLowerCase();
        if ("confirmed".equals(normalized)) {
            return "confirmed";
        }
        if ("watchlist".equals(normalized) || "watch".equals(normalized)) {
            return "watchlist";
        }
        return normalized;
    }

    public boolean isWatchlist() {
        return "watchlist".equals(normalizedListType());
    }

    public boolean isConfirmed() {
        return "confirmed".equals(normalizedListType());
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
