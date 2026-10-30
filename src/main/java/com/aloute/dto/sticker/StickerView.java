package com.aloute.dto.sticker;

public record StickerView(
        String id,
        String packId,
        String code,
        String name,
        String imageUrl,
        String keywords,
        String renderType,
        int sortOrder
) {
    public StickerView(String id, String packId, String code, String name, String imageUrl) {
        this(id, packId, code, name, imageUrl, "", code != null && code.startsWith("atlas-") ? "ATLAS" : "LEGACY", 0);
    }
    public boolean visibleArtwork() { return "ATLAS".equals(renderType) || "IMAGE".equals(renderType); }
    @com.fasterxml.jackson.annotation.JsonProperty
    public Integer spriteIndex() {
        if (!"ATLAS".equals(renderType) || code == null || !code.matches("atlas-(?:[0-9]|1[0-9]|2[0-3])")) return null;
        return Integer.parseInt(code.substring(6));
    }

    @com.fasterxml.jackson.annotation.JsonProperty
    public StickerArtwork.Frame artwork() {
        return StickerArtwork.at(spriteIndex());
    }
}

