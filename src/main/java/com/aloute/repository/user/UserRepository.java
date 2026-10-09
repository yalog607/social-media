package com.aloute.repository.user;

import com.aloute.model.user.User;
import com.aloute.model.user.UserStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsername(@Param("username") String username);

    Optional<User> findByFirebaseUid(String firebaseUid);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsername(@Param("username") String username);

    /** Người dùng còn hoạt động kèm hồ sơ, dùng để dựng lại kết quả tìm kiếm theo đúng thứ tự đã xếp hạng. */
    @Query("select u from User u join fetch u.profile where u.id in :ids and u.status = com.aloute.model.user.UserStatus.ACTIVE")
    List<User> findActiveByIds(@Param("ids") Collection<UUID> ids);

    /**
     * ID người dùng còn hoạt động có tên/username khớp {@code escapedQuery} (đã escape ký tự đặc biệt của
     * ILIKE), gần đúng nhất trước. Chỉ trả ID: nạp lại đủ dữ liệu bằng {@link #findActiveByIds}.
     */
    @Query(value = """
            select u.id from users u join profiles p on p.user_id = u.id
            where u.status = 'ACTIVE' and p.search_name ilike '%' || :escapedQuery || '%' escape '\\'
            order by similarity(p.search_name, :rawQuery) desc
            limit :limit""", nativeQuery = true)
    List<UUID> searchActiveIds(@Param("escapedQuery") String escapedQuery, @Param("rawQuery") String rawQuery,
                               @Param("limit") int limit);
}
