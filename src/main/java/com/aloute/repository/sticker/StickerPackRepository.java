package com.aloute.repository.sticker;

import com.aloute.model.sticker.StickerPack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StickerPackRepository extends JpaRepository<StickerPack, String> {
    List<StickerPack> findAllByOrderBySortOrderAsc();
    List<StickerPack> findByIsDefaultTrueOrderBySortOrderAsc();
}

