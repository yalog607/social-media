package com.aloute.service.seo;

import com.aloute.config.AlouteProperties;
import com.aloute.dto.common.OgMeta;
import com.aloute.model.media.MediaKind;
import com.aloute.dto.post.PostView;
import com.aloute.model.user.Profile;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/** Dựng thẻ Open Graph cho web, bài viết, hồ sơ và hashtag. Mọi URL trả về đều là địa chỉ tuyệt đối. */
@Service
public class OgMetaFactory {

    static final String DEFAULT_IMAGE = "/img/og-default.png";
    static final String SITE_TITLE = "ALOUTE · Alô, cả hội đâu rồi?";
    static final String SITE_DESCRIPTION =
            "Đăng ảnh, khoe clip, nhắn nhóm và làm fan cứng của người mình thích. Tất cả ở một chỗ, không cần lịch sự quá.";
    private static final int DESCRIPTION_MAX = 160;

    private final String baseUrl;

    public OgMetaFactory(AlouteProperties props) {
        String base = props.baseUrl() == null ? "" : props.baseUrl().trim();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public OgMeta site() {
        return new OgMeta(SITE_TITLE, SITE_DESCRIPTION, absolute(DEFAULT_IMAGE), baseUrl + "/", "website", true);
    }

    public OgMeta post(PostView post) {
        PostView original = post.sharedPost();
        // Chỉ dùng nội dung/ảnh bài gốc khi nó công khai và không thuộc nhóm (bài gốc có thể đã đổi quyền sau khi được chia sẻ)
        boolean originalExposable = original != null && original.isPublic() && original.groupId() == null;
        String title = post.isAnonymous()
                ? "Một bạn ẩn danh trên ALOUTE"
                : post.author().displayName() + " trên ALOUTE";
        String text = truncate(plainText(post.contentHtml()));
        if (text.isEmpty() && originalExposable) {
            text = truncate(plainText(original.contentHtml()));
        }
        String image = firstImage(post);
        if (image == null && originalExposable) {
            image = firstImage(original);
        }
        return new OgMeta(title, text.isEmpty() ? SITE_DESCRIPTION : text,
                image != null ? absolute(image) : absolute(DEFAULT_IMAGE),
                baseUrl + "/posts/" + post.id(), "article", image == null);
    }

    public OgMeta profile(User owner) {
        Profile profile = owner.getProfile();
        String url = baseUrl + "/u/" + owner.getUsername();
        if (profile.getProfileVisibility() != Visibility.PUBLIC) {
            return new OgMeta(profile.getDisplayName() + " trên ALOUTE", SITE_DESCRIPTION,
                    absolute(DEFAULT_IMAGE), url, "profile", true);
        }
        String bio = truncate(profile.getBio());
        boolean hasAvatar = profile.getAvatarUrl() != null && !profile.getAvatarUrl().isBlank();
        return new OgMeta(profile.getDisplayName() + " trên ALOUTE", bio.isEmpty() ? SITE_DESCRIPTION : bio,
                absolute(hasAvatar ? profile.getAvatarUrl() : DEFAULT_IMAGE), url, "profile", !hasAvatar);
    }

    public OgMeta tag(String tag) {
        return new OgMeta("#" + tag + " trên ALOUTE", "Xem các bài đăng với #" + tag + " trên ALOUTE.",
                absolute(DEFAULT_IMAGE), baseUrl + "/tags/" + UriUtils.encodePathSegment(tag, StandardCharsets.UTF_8), "website", true);
    }

    private static String firstImage(PostView post) {
        return post.media().stream()
                .filter(m -> m.kind() == MediaKind.IMAGE)
                .map(PostView.MediaView::url)
                .filter(u -> u != null && !u.isBlank())
                .findFirst().orElse(null);
    }

    /** {@code contentHtml} đã escape và có thẻ liên kết hashtag/mention; lấy lại chữ thuần cho mô tả OG. */
    static String plainText(String html) {
        return html == null ? "" : HtmlUtils.htmlUnescape(html.replaceAll("<[^>]*>", " "));
    }

    private String absolute(String url) {
        return url.startsWith("http://") || url.startsWith("https://") ? url : baseUrl + (url.startsWith("/") ? url : "/" + url);
    }

    /** Gộp khoảng trắng và cắt theo code point để không làm hỏng emoji. */
    static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.codePointCount(0, flat.length()) <= DESCRIPTION_MAX) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, DESCRIPTION_MAX - 1)) + "…";
    }
}
