package com.aloute.repository.sticker;

import com.aloute.model.sticker.Sticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StickerRepository extends JpaRepository<Sticker, String> {
    @org.springframework.data.jpa.repository.Query("select s from Sticker s where s.pack.id = :packId and s.removed = false order by s.sortOrder, s.id")
    List<Sticker> findByPackIdOrderBySortOrderAsc(String packId);

    boolean existsByImageUrl(String imageUrl);
}

