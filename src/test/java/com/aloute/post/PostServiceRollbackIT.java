package com.aloute.post;

import com.aloute.support.IntegrationTest;
import com.aloute.support.TestMedia;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Tách riêng vì phải "theo dõi" PostRepository, việc này làm Spring tạo một context khác. */
class PostServiceRollbackIT extends IntegrationTest {

    @Autowired PostService service;
    @MockitoSpyBean PostRepository posts;

    @Test
    void removesStoredFilesWhenSavingThePostFails() {
        User author = createUser();
        long before = TestMedia.storedFileCount();
        doThrow(new IllegalStateException("cơ sở dữ liệu tạm thời hỏng")).when(posts).save(any(Post.class));

        assertThatThrownBy(() -> service.create(author.getId(), "lưu lỗi", Visibility.PUBLIC,
                List.of(TestMedia.image("a.jpg", TestMedia.jpeg(80, 80)), TestMedia.image("b.jpg", TestMedia.jpeg(90, 90))), null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(TestMedia.storedFileCount()).as("không để lại file mồ côi").isEqualTo(before);
    }
}
