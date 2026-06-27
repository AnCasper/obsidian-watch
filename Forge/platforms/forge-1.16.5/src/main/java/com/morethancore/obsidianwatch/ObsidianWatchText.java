package com.morethancore.obsidianwatch;

import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.IFormattableTextComponent;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public final class ObsidianWatchText {
    private static final TextFormatting BRAND = TextFormatting.DARK_AQUA;
    private static final TextFormatting LABEL = TextFormatting.GRAY;
    private static final TextFormatting VALUE = TextFormatting.WHITE;
    private static final TextFormatting MUTED = TextFormatting.DARK_GRAY;

    private ObsidianWatchText() {
    }

    public static IFormattableTextComponent title(String value) {
        return new StringTextComponent(value).withStyle(BRAND, TextFormatting.BOLD);
    }

    public static IFormattableTextComponent subtitle(String value) {
        return new StringTextComponent(value).withStyle(TextFormatting.AQUA);
    }

    public static IFormattableTextComponent line(String label, String value) {
        return line(label, value, VALUE);
    }

    public static IFormattableTextComponent line(String label, String value, TextFormatting valueColor) {
        return new StringTextComponent(label + ": ").withStyle(LABEL)
                .append(new StringTextComponent(isBlank(value) ? "-" : value).withStyle(valueColor));
    }

    public static IFormattableTextComponent command(String command, String description) {
        return new StringTextComponent(command).withStyle(TextFormatting.AQUA)
                .append(new StringTextComponent(" - ").withStyle(MUTED))
                .append(new StringTextComponent(description).withStyle(LABEL));
    }

    public static IFormattableTextComponent section(String value) {
        return new StringTextComponent(value).withStyle(TextFormatting.GOLD, TextFormatting.BOLD);
    }

    public static IFormattableTextComponent spacer() {
        return new StringTextComponent("\n");
    }

    public static IFormattableTextComponent join(ITextComponent... components) {
        IFormattableTextComponent out = new StringTextComponent("");
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                out.append(new StringTextComponent("\n"));
            }
            out.append(components[i]);
        }
        return out;
    }

    public static TextFormatting statusColor(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (normalized.contains("failed") || normalized.contains("error") || normalized.contains("blank") || normalized.contains("disabled")) {
            return TextFormatting.RED;
        }
        if (normalized.contains("fallback") || normalized.contains("scheduled") || normalized.contains("queued") || normalized.contains("cooldown")) {
            return TextFormatting.YELLOW;
        }
        return TextFormatting.GREEN;
    }

    public static TextFormatting listColor(WatchEntry entry) {
        if (entry != null && entry.isConfirmed()) {
            return TextFormatting.RED;
        }
        if (entry != null && entry.isWatchlist()) {
            return TextFormatting.YELLOW;
        }
        return TextFormatting.GRAY;
    }

    public static String titleCase(String value) {
        if (isBlank(value)) {
            return "Unknown";
        }
        String normalized = value.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (isBlank(part)) {
                continue;
            }
            if (builder.length() != 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? "Unknown" : builder.toString();
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


    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
