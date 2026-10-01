package com.aloute.social;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/** Danh sách bạn bè dạng JSON, cho ô chọn người để gắn thẻ khi sửa bài. */
@Controller
public class FriendApiController {

    private final FriendService friends;

    public FriendApiController(FriendService friends) {
        this.friends = friends;
    }

    @GetMapping("/api/friends")
    @ResponseBody
    public List<Map<String, String>> list(@AuthenticationPrincipal AlouteUserPrincipal me) {
        return friends.friendsOf(me.id()).stream()
                .map(f -> Map.of("id", f.id().toString(), "displayName", f.displayName()))
                .toList();
    }
}
