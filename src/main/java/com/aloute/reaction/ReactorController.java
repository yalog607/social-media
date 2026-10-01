package com.aloute.reaction;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Mảnh HTML danh sách người đã thả cảm xúc cho một bài, hiện trong hộp thoại "Ai đã thả cảm xúc". */
@Controller
public class ReactorController {

    private final ReactionService reactions;

    public ReactorController(ReactionService reactions) {
        this.reactions = reactions;
    }

    @GetMapping("/api/posts/{id}/reactions")
    public String list(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("reactors", reactions.reactors(id, me.id()));
        return "fragments/reactors :: list";
    }
}
