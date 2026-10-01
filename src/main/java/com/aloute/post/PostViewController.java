package com.aloute.post;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/** Nhận tín hiệu "bài này vừa hiện trên màn hình" từ feed.js. Luôn trả 204, kể cả khi đã đếm rồi. */
@Controller
public class PostViewController {

    private final PostViewService views;

    public PostViewController(PostViewService views) {
        this.views = views;
    }

    @PostMapping("/api/posts/{id}/view")
    public ResponseEntity<Void> view(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me) {
        views.record(me.id(), id);
        return ResponseEntity.noContent().build();
    }
}
