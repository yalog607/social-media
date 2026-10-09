package com.aloute.controller.reaction;

import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.model.reaction.ReactionType;
import com.aloute.service.reaction.ReactionService;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Đổi/bỏ cảm xúc bằng fetch từ JS; trả JSON số đếm mới để cập nhật thanh cảm xúc ngay không cần tải lại trang. */
@RestController
public class ReactionController {

    private final ReactionService reactions;

    public ReactionController(ReactionService reactions) {
        this.reactions = reactions;
    }

    @PostMapping("/api/posts/{id}/reaction")
    @ResponseBody
    public Map<String, Object> react(@PathVariable UUID id, @RequestParam ReactionType type,
                                     @AuthenticationPrincipal AlouteUserPrincipal me) {
        ReactionSummary summary = reactions.toggle(me.id(), id, type);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ReactionType value : ReactionType.values()) {
            counts.put(value.name(), summary.count(value));
        }
        return Map.of(
                "counts", counts,
                "total", summary.total(),
                "mine", summary.mine() == null ? "" : summary.mine().name());
    }
}
