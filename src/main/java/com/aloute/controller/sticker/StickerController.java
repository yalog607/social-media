package com.aloute.controller.sticker;

import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.sticker.LocalStickerLibrary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stickers")
public class StickerController {
    private final LocalStickerLibrary library;
    public StickerController(LocalStickerLibrary library) { this.library = library; }
    @GetMapping("/library")
    public LocalStickerLibrary.Library library(@AuthenticationPrincipal AlouteUserPrincipal me) { return library.library(me.id()); }
    @PostMapping("/favorites/{id}")
    public void favorite(@PathVariable String id, @AuthenticationPrincipal AlouteUserPrincipal me) { library.favorite(me.id(), id, true); }
    @DeleteMapping("/favorites/{id}")
    public void unfavorite(@PathVariable String id, @AuthenticationPrincipal AlouteUserPrincipal me) { library.favorite(me.id(), id, false); }
}
