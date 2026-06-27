package com.morethancore.obsidianwatch;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public final class ObsidianWatchText {
    private static final ChatFormatting BRAND = ChatFormatting.DARK_AQUA;
    private static final ChatFormatting LABEL = ChatFormatting.GRAY;
    private static final ChatFormatting VALUE = ChatFormatting.WHITE;
    private static final ChatFormatting MUTED = ChatFormatting.DARK_GRAY;

    private ObsidianWatchText() {
    }

    public static MutableComponent title(String value) {
        return Component.literal(value).withStyle(BRAND, ChatFormatting.BOLD);
    }

    public static MutableComponent subtitle(String value) {
        return Component.literal(value).withStyle(ChatFormatting.AQUA);
    }

    public static MutableComponent line(String label, String value) {
        return line(label, value, VALUE);
    }

    public static MutableComponent line(String label, String value, ChatFormatting valueColor) {
        return Component.literal(label + ": ").withStyle(LABEL)
                .append(Component.literal(value == null || value.isBlank() ? "-" : value).withStyle(valueColor));
    }

    public static MutableComponent command(String command, String description) {
        return Component.literal(command).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - ").withStyle(MUTED))
                .append(Component.literal(description).withStyle(LABEL));
    }

    public static MutableComponent section(String value) {
        return Component.literal(value).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    public static MutableComponent spacer() {
        return Component.literal("\n");
    }

    public static MutableComponent join(Component... components) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                out.append(Component.literal("\n"));
            }
            out.append(components[i]);
        }
        return out;
    }

    public static ChatFormatting statusColor(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (normalized.contains("failed") || normalized.contains("error") || normalized.contains("blank") || normalized.contains("disabled")) {
            return ChatFormatting.RED;
        }
        if (normalized.contains("fallback") || normalized.contains("scheduled") || normalized.contains("queued") || normalized.contains("cooldown")) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.GREEN;
    }

    public static ChatFormatting listColor(WatchEntry entry) {
        if (entry != null && entry.isConfirmed()) {
            return ChatFormatting.RED;
        }
        if (entry != null && entry.isWatchlist()) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.GRAY;
    }

    public static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String normalized = value.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? "Unknown" : builder.toString();
    }

    public static String ago(Instant instant) {
        if (instant == null) {
            return "never";
        }
        long seconds = Math.max(0L, Duration.between(instant, Instant.now()).getSeconds());
        if (seconds < 5) {
            return "just now";
        }
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60L;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60L;
        if (hours < 48) {
            return hours + "h ago";
        }
        long days = hours / 24L;
        return days + "d ago";
    }
}
