package com.aloute.seo;

import com.aloute.config.AlouteProperties;
import com.aloute.dto.common.OgMeta;
import com.aloute.model.media.MediaKind;
import com.aloute.dto.post.PostView;
import com.aloute.model.user.Role;
import com.aloute.model.user.Profile;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.service.seo.OgMetaFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OgMetaFactoryTest {

    private final OgMetaFactory factory = new OgMetaFactory(
            new AlouteProperties("https://aloute.example/", null, null, null, null, null, null, null));

    private User user(String name) {
        User user = new User();
        user.setUsername(name);
        Profile profile = new Profile();
        profile.setDisplayName("Bạn " + name);
        user.setProfile(profile);
        return user;
    }

    private PostView.AuthorView author(String name) {
        return new PostView.AuthorView(UUID.randomUUID(), name, "Bạn " + name, null, Role.USER);
    }

    private PostView post(String name, String contentHtml, boolean anonymous, PostView shared,
                          PostView.MediaView... media) {
        return new PostView(UUID.randomUUID(), author(name), contentHtml, Visibility.PUBLIC, Instant.now(), false,
                List.of(media), false, null, null, 0, 0, shared == null ? null : shared.id(), shared, List.of(), null,
                false, anonymous, null, null);
    }

    private PostView post(String name, String contentHtml, PostView.MediaView... media) {
        return post(name, contentHtml, false, null, media);
    }

    private PostView.MediaView media(MediaKind kind, String url) {
        return new PostView.MediaView(kind, url, null);
    }

    @Test
    void siteUsesDefaultImage() {
        OgMeta og = factory.site();
        assertThat(og.image()).isEqualTo("https://aloute.example/img/og-default.png");
        assertThat(og.defaultImage()).isTrue();
    }

    @Test
    void postWithImageUsesItAsAbsoluteUrl() {
        OgMeta og = factory.post(post("lan", "xin chào", media(MediaKind.IMAGE, "/uploads/posts/a.jpg")));
        assertThat(og.image()).isEqualTo("https://aloute.example/uploads/posts/a.jpg");
        assertThat(og.defaultImage()).isFalse();
        assertThat(og.title()).isEqualTo("Bạn lan trên ALOUTE");
    }

    @Test
    void cloudinaryUrlIsKept() {
        OgMeta og = factory.post(post("lan", "x", media(MediaKind.IMAGE, "https://res.cloudinary.com/x/a.jpg")));
        assertThat(og.image()).isEqualTo("https://res.cloudinary.com/x/a.jpg");
    }

    @Test
    void textOnlyAndVideoOnlyPostsFallBackToDefaultImage() {
        assertThat(factory.post(post("lan", "chỉ chữ")).image()).endsWith("/img/og-default.png");
        OgMeta video = factory.post(post("lan", "clip", media(MediaKind.VIDEO, "/uploads/posts/v.mp4")));
        assertThat(video.image()).endsWith("/img/og-default.png");
        assertThat(video.defaultImage()).isTrue();
    }

    @Test
    void anonymousPostHidesAuthor() {
        PostView p = post("lan", "bí mật", true, null);
        assertThat(factory.post(p).title()).doesNotContain("lan");
    }

    @Test
    void sharedPostUsesOriginalContentAndImage() {
        PostView original = post("a", "gốc", media(MediaKind.IMAGE, "/uploads/posts/o.jpg"));
        PostView share = post("b", "", false, original);
        OgMeta og = factory.post(share);
        assertThat(og.image()).endsWith("/uploads/posts/o.jpg");
        assertThat(og.description()).isEqualTo("gốc");
    }

    @Test
    void sharedPostDoesNotExposeOriginalThatIsNoLongerPublic() {
        PostView original = new PostView(UUID.randomUUID(), author("a"), "bí mật", Visibility.PRIVATE, Instant.now(), false,
                List.of(media(MediaKind.IMAGE, "/uploads/posts/o.jpg")), false, null, null, 0, 0, null, null, List.of(),
                null, false, false, null, null);
        PostView share = post("b", "", false, original);
        OgMeta og = factory.post(share);
        assertThat(og.image()).endsWith("/img/og-default.png");
        assertThat(og.description()).doesNotContain("bí mật");
    }

    @Test
    void descriptionStripsHtmlAndUnescapes() {
        String d = factory.post(post("lan", "Mê <a href=\"/tags/x\">#x</a> &amp; bạn")).description();
        assertThat(d).isEqualTo("Mê #x & bạn".replaceAll("\s+", " ")).doesNotContain("<");
    }

    @Test
    void descriptionIsFlattenedAndTruncatedByCodePoint() {
        String text = "😀 a\n\n b ".repeat(60);
        String d = factory.post(post("lan", text)).description();
        assertThat(d.codePointCount(0, d.length())).isEqualTo(160);
        assertThat(d).doesNotContain("\n").endsWith("…");
    }

    @Test
    void privateProfileDoesNotExposeBioOrAvatar() {
        User u = user("lan");
        u.getProfile().setBio("tiểu sử riêng");
        u.getProfile().setAvatarUrl("/uploads/avatars/x.jpg");
        u.getProfile().setProfileVisibility(Visibility.PRIVATE);
        OgMeta og = factory.profile(u);
        assertThat(og.description()).doesNotContain("riêng");
        assertThat(og.image()).endsWith("/img/og-default.png");
        u.getProfile().setProfileVisibility(Visibility.PUBLIC);
        assertThat(factory.profile(u).image()).endsWith("/uploads/avatars/x.jpg");
    }
}
