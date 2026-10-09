package com.aloute.post;

import com.aloute.model.post.Post;
import com.aloute.model.post.PostMedia;
import com.aloute.repository.post.PostMediaRepository;
import com.aloute.repository.post.PostRepository;

import com.aloute.model.media.MediaKind;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phần 2a, task 5: migration V2 và ánh xạ thực thể. */
class PostSchemaIT extends IntegrationTest {

    @Autowired PostRepository posts;
    @Autowired PostMediaRepository mediaRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    private Post newPost(User author, String content, Visibility visibility) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent(content);
        post.setVisibility(visibility);
        post.setSearchText(content.toLowerCase());
        return post;
    }

    private PostMedia media(MediaKind kind, String url) {
        PostMedia item = new PostMedia();
        item.setKind(kind);
        item.setUrl(url);
        item.setContentType(kind == MediaKind.IMAGE ? "image/jpeg" : "video/mp4");
        item.setSizeBytes(1234);
        return item;
    }

    @Test
    void migrationV2IsApplied() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '2' and success", Integer.class);

        assertThat(applied).isEqualTo(1);
    }

    @Test
    void savesAPostWithOrderedMediaAndHashtagsAndReadsThemBack() {
        User author = createUser();
        Post post = newPost(author, "Chào #Nhân", Visibility.PUBLIC);
        post.addMedia(media(MediaKind.IMAGE, "/uploads/posts/a.jpg"));
        post.addMedia(media(MediaKind.IMAGE, "/uploads/posts/b.jpg"));
        post.addMedia(media(MediaKind.IMAGE, "/uploads/posts/c.jpg"));
        post.setHashtags(new LinkedHashSet<>(List.of("nhan", "vui")));
        UUID id = posts.saveAndFlush(post).getId();

        tx.executeWithoutResult(status -> {
            Post loaded = posts.findLive(id).orElseThrow();
            assertThat(loaded.getAuthor().getId()).isEqualTo(author.getId());
            assertThat(loaded.getAuthor().getProfile().getDisplayName()).isEqualTo(author.getProfile().getDisplayName());
            assertThat(loaded.getContent()).isEqualTo("Chào #Nhân");
            assertThat(loaded.getVisibility()).isEqualTo(Visibility.PUBLIC);
            assertThat(loaded.getCreatedAt()).isNotNull();
            assertThat(loaded.getMedia()).extracting(PostMedia::getUrl)
                    .containsExactly("/uploads/posts/a.jpg", "/uploads/posts/b.jpg", "/uploads/posts/c.jpg");
            assertThat(loaded.getMedia()).extracting(PostMedia::getSortOrder).containsExactly((short) 0, (short) 1, (short) 2);
            assertThat(loaded.getHashtags()).containsExactlyInAnyOrder("nhan", "vui");
        });
    }

    @Test
    void findLiveHidesSoftDeletedPosts() {
        Post post = posts.saveAndFlush(newPost(createUser(), "sẽ bị xóa", Visibility.PUBLIC));
        assertThat(posts.findLive(post.getId())).isPresent();

        jdbc.update("update posts set deleted_at = now() where id = ?", post.getId());

        assertThat(posts.findLive(post.getId())).isEmpty();
    }

    @Test
    void loadsMediaOfManyPostsInOneQueryInTheRightOrder() {
        User author = createUser();
        Post first = newPost(author, "một", Visibility.PUBLIC);
        first.addMedia(media(MediaKind.IMAGE, "/uploads/posts/1a.jpg"));
        first.addMedia(media(MediaKind.IMAGE, "/uploads/posts/1b.jpg"));
        Post second = newPost(author, "hai", Visibility.PUBLIC);
        second.addMedia(media(MediaKind.VIDEO, "/uploads/posts/2.mp4"));
        posts.saveAndFlush(first);
        posts.saveAndFlush(second);

        List<PostMedia> found = mediaRepo.findByPostIds(List.of(first.getId(), second.getId()));

        assertThat(found).hasSize(3);
        assertThat(found.stream().filter(m -> m.getPost().getId().equals(first.getId())).map(PostMedia::getUrl))
                .containsExactly("/uploads/posts/1a.jpg", "/uploads/posts/1b.jpg");
    }

    // ---------- Ràng buộc của schema ----------

    @Test
    void rejectsUnknownVisibilityAtTheDatabaseLevel() {
        User author = createUser();

        assertThatThrownBy(() -> jdbc.update(
                "insert into posts (id, author_id, visibility) values (?, ?, 'EVERYONE')", UUID.randomUUID(), author.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsContentOverTwoThousandCharacters() {
        User author = createUser();

        assertThatThrownBy(() -> jdbc.update("insert into posts (id, author_id, content) values (?, ?, ?)",
                UUID.randomUUID(), author.getId(), "x".repeat(Post.MAX_CONTENT_LENGTH + 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("insert into posts (id, author_id, content) values (?, ?, ?)",
                UUID.randomUUID(), author.getId(), "x".repeat(Post.MAX_CONTENT_LENGTH))).isEqualTo(1);
    }

    @Test
    void mediaOrderIsUniquePerPostAndKindIsValidated() {
        Post post = posts.saveAndFlush(newPost(createUser(), "có media", Visibility.PUBLIC));
        String insert = "insert into post_media (id, post_id, kind, url, content_type, size_bytes, sort_order) "
                + "values (?, ?, ?, '/uploads/posts/x.jpg', 'image/jpeg', 1, ?)";
        jdbc.update(insert, UUID.randomUUID(), post.getId(), "IMAGE", 0);

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), post.getId(), "IMAGE", 0))
                .as("trùng sort_order trong cùng một bài").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), post.getId(), "AUDIO", 1))
                .as("kind lạ").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAPostRemovesItsMediaAndHashtags() {
        Post post = newPost(createUser(), "xóa hẳn", Visibility.PUBLIC);
        post.addMedia(media(MediaKind.IMAGE, "/uploads/posts/z.jpg"));
        post.setHashtags(Set.of("xoa"));
        UUID id = posts.saveAndFlush(post).getId();

        jdbc.update("delete from posts where id = ?", id);

        assertThat(jdbc.queryForObject("select count(*) from post_media where post_id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from post_hashtags where post_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void aUserWithPostsCannotBeHardDeleted() {
        User author = createUser();
        posts.saveAndFlush(newPost(author, "còn bài", Visibility.PUBLIC));

        assertThatThrownBy(() -> jdbc.update("delete from users where id = ?", author.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- Chỉ mục ----------

    @Test
    void createsTheTrigramAndPagingIndexes() {
        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname = 'pg_trgm'", Integer.class)).isEqualTo(1);

        String searchIndex = jdbc.queryForObject(
                "select indexdef from pg_indexes where indexname = 'ix_posts_search'", String.class);
        assertThat(searchIndex).contains("gin").contains("gin_trgm_ops");

        List<String> names = jdbc.queryForList(
                "select indexname from pg_indexes where tablename = 'posts'", String.class);
        assertThat(names).contains("ix_posts_feed", "ix_posts_author", "ix_posts_search");
        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'ix_posts_feed'", String.class))
                .contains("created_at DESC").contains("id DESC");
    }
}
