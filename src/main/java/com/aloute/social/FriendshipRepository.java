package com.aloute.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code userAId}/{@code userBId} luôn được người gọi chuẩn hóa (nhỏ hơn đứng trước) trước khi gọi. */
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("""
            select f from Friendship f
            join fetch f.userA a join fetch a.profile
            join fetch f.userB b join fetch b.profile
            join fetch f.requestedBy
            where f.userA.id = :userAId and f.userB.id = :userBId""")
    Optional<Friendship> findPair(@Param("userAId") UUID userAId, @Param("userBId") UUID userBId);

    @Query("""
            select f from Friendship f
            join fetch f.userA a join fetch a.profile
            join fetch f.userB b join fetch b.profile
            where (f.userA.id = :userId or f.userB.id = :userId) and f.status = com.aloute.social.FriendshipStatus.ACCEPTED
            order by f.respondedAt desc""")
    List<Friendship> findAcceptedFor(@Param("userId") UUID userId);

    @Query("""
            select f from Friendship f
            join fetch f.userA a join fetch a.profile
            join fetch f.userB b join fetch b.profile
            join fetch f.requestedBy
            where (f.userA.id = :userId or f.userB.id = :userId) and f.status = com.aloute.social.FriendshipStatus.PENDING
            order by f.createdAt desc""")
    List<Friendship> findPendingFor(@Param("userId") UUID userId);

    @Query("""
            select count(f) > 0 from Friendship f
            where f.userA.id = :userAId and f.userB.id = :userBId and f.status = com.aloute.social.FriendshipStatus.ACCEPTED""")
    boolean areFriends(@Param("userAId") UUID userAId, @Param("userBId") UUID userBId);

    @Query("""
            select count(f) from Friendship f
            where (f.userA.id = :userId or f.userB.id = :userId) and f.status = com.aloute.social.FriendshipStatus.ACCEPTED""")
    long countAcceptedFor(@Param("userId") UUID userId);
}
