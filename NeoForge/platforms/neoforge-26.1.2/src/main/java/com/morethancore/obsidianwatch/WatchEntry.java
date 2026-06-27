package com.morethancore.obsidianwatch;

import java.util.Locale;

public record WatchEntry(
        String javaUuid,
        String currentUsername,
        String listType,
        String category,
        String severity,
        String actionRecommended,
        String reasonPublic
) {
    public boolean isConfirmed() {
        return "confirmed".equals(normalizedListType());
    }

    public boolean isWatchlist() {
        return "watchlist".equals(normalizedListType());
    }

    public String normalizedListType() {
        return listType == null ? "" : listType.toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        if (currentUsername != null && !currentUsername.isBlank()) {
            return currentUsername;
        }
        return javaUuid;
    }
}
