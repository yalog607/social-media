package com.aloute.util.common;

/** Escape {@code %}, {@code _} và {@code \} trước khi nhét vào một mẫu {@code ILIKE '%...%' ESCAPE '\'}. */
public final class LikeEscape {

    private LikeEscape() {
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
