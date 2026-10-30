package com.aloute.model.sticker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stickers")
@Getter
@Setter
@NoArgsConstructor
public class Sticker {
    public enum RenderType { LEGACY, ATLAS, IMAGE }

    @Enumerated(EnumType.STRING)
    @Column(name = "render_type", nullable = false)
    private RenderType renderType = RenderType.LEGACY;

    @Column(nullable = false, length = 300)
    private String keywords = "";

    @Column(nullable = false)
    private boolean removed = false;

    @Id
    @Column(length = 80)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pack_id", nullable = false)
    private StickerPack pack;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}

