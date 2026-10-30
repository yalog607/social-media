package com.aloute.dto.moderation;

/** One-based, bounded pagination for Manager lists. */
public record ManagerPage(long number, int size, long total, long pages) {
    public static ManagerPage of(String page, String size, long total) {
        long requested = positive(page);
        long parsedSize = positive(size);
        int limit = parsedSize == 50 || parsedSize == 100 ? (int) parsedSize : 20;
        long last = total == 0 ? 1 : 1 + (total - 1) / limit;
        return new ManagerPage(Math.min(requested, last), limit, total, last);
    }

    private static long positive(String value) {
        try { return Math.max(1, Long.parseLong(value)); }
        catch (NumberFormatException e) { return 1; }
    }

    public long offset() { return (number - 1) * size; }
    public String redirect(String path) {
        return "redirect:" + path + "?page=" + number + "&size=" + size;
    }
}
