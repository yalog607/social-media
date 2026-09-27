package com.aloute.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    @Query("select b from Block b join fetch b.blocked u join fetch u.profile where b.blocker.id = :userId order by b.createdAt desc")
    List<Block> findBlockedByBlocker(@Param("userId") UUID userId);

    @Query("select count(b) > 0 from Block b where (b.blocker.id = :a and b.blocked.id = :b) or (b.blocker.id = :b and b.blocked.id = :a)")
    boolean existsEitherWay(@Param("a") UUID a, @Param("b") UUID b);
}
