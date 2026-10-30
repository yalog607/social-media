package com.aloute.dto.sticker;

import java.util.List;

public record StickerPackView(
        String id,
        String name,
        String description,
        String iconUrl,
        String author,
        int totalStickers,
        boolean installed,
        List<StickerView> stickers,
        String accessType,
        String status
) {
    public StickerPackView(String id, String name, String description, String iconUrl, String author, int totalStickers,
                           boolean installed, List<StickerView> stickers) {
        this(id, name, description, iconUrl, author, totalStickers, installed, stickers, "FREE", "PUBLISHED");
    }
}

