package com.aloute.controller.season;

import com.aloute.service.season.SeasonService;
import com.aloute.service.post.PostService;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Controller
public class SeasonController {
    private final SeasonService seasons;
    private final PostService posts;
    public SeasonController(SeasonService seasons, PostService posts) { this.seasons = seasons; this.posts = posts; }

    @GetMapping("/seasons")
    public String hall(Model model) {
        model.addAttribute("seasons", seasons.list().stream().filter(s -> s.status().equals("SETTLED")).toList());
        return "season/hall";
    }

    @GetMapping("/seasons/{id}")
    public String detail(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        SeasonService.Season season;
        try { season = seasons.get(id); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        if (season.status().equals("DRAFT")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("season", season);
        model.addAttribute("standings", seasons.standings(id));
        model.addAttribute("entries", seasons.entries(id, false).stream().filter(entry -> {
            try { posts.getVisible(entry.id(), me.id()); return true; }
            catch (PostNotFoundException e) { return false; }
        }).toList());
        return "season/detail";
    }
}
