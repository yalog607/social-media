package com.aloute.guestbook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestbookRepository extends JpaRepository<GuestbookEntry, UUID> {

    @Query("""
            select g from GuestbookEntry g
            join fetch g.author a
            join fetch a.profile
            join fetch g.profileUser u
            where g.profileUser.id = :profileUserId
            order by g.isPinned desc, g.createdAt desc""")
    List<GuestbookEntry> findByProfileUserIdForOwner(@Param("profileUserId") UUID profileUserId);

    @Query("""
            select g from GuestbookEntry g
            join fetch g.author a
            join fetch a.profile
            join fetch g.profileUser u
            where g.profileUser.id = :profileUserId and g.isHidden = false
            order by g.isPinned desc, g.createdAt desc""")
    List<GuestbookEntry> findByProfileUserIdForVisitor(@Param("profileUserId") UUID profileUserId);

    @Query("""
            select g from GuestbookEntry g
            join fetch g.author a
            join fetch g.profileUser u
            where g.id = :id""")
    Optional<GuestbookEntry> findWithUsersById(@Param("id") UUID id);

    long countByProfileUserId(UUID profileUserId);

    long countByProfileUserIdAndIsHiddenFalse(UUID profileUserId);
}
