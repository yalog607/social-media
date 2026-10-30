package com.aloute.controller.sticker;

import com.aloute.exception.chat.ChatActionException;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.sticker.LocalStickerLibrary;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class StickerCollectionController {
    private final LocalStickerLibrary library;
    public StickerCollectionController(LocalStickerLibrary library) { this.library=library; }
    @GetMapping("/stickers/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public String owned(@AuthenticationPrincipal AlouteUserPrincipal me,@PathVariable String id,Model model) {
        try { model.addAttribute("pack",library.ownedPack(me.id(),id));return "sticker/owned"; }
        catch(ChatActionException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }
}
