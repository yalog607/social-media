package com.aloute.post;

import com.aloute.user.User;
import com.aloute.user.Visibility;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
public class Post {

    public static final int MAX_CONTENT_LENGTH = 2000;
    public static final int MAX_SHARE_CAPTION_LENGTH = 500;

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility = Visibility.PUBLIC;

    /** Nội dung đã bỏ dấu, chữ thường, phục vụ tìm kiếm. Luôn được {@code PostService} cập nhật cùng {@code content}. */
    @Column(name = "search_text", nullable = false)
    private String searchText = "";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Giờ hẹn đăng; khác null nghĩa là bài chưa đăng, chỉ chủ bài thấy. Xem {@code ScheduledPostPublisher}. */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /** Giá (Xu) để mở khóa nội dung; null nghĩa là bài miễn phí. Chỉ Creator đặt được. */
    @Column(name = "unlock_price")
    private Integer unlockPrice;

    /**
     * Khác null nghĩa là bài này là một lượt CHIA SẺ của bài gốc (luôn trỏ thẳng tới bài gốc thật sự, không bao
     * giờ trỏ qua một bài chia sẻ khác — {@code PostService#share} tự rút gọn). {@code content} khi đó là lời
     * nhắn tùy chọn của người chia sẻ, không phải nội dung bài gốc.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_post_id", updatable = false)
    private Post sharedPost;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PostMedia> media = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "post_hashtags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag", nullable = false, length = 50)
    private Set<String> hashtags = new LinkedHashSet<>();

    public boolean isScheduled() {
        return scheduledAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isShare() {
        return sharedPost != null;
    }

    public void addMedia(PostMedia item) {
        item.setPost(this);
        item.setSortOrder((short) media.size());
        media.add(item);
    }
}
