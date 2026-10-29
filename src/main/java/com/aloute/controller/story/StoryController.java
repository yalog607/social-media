package com.aloute.controller.story;

import com.aloute.dto.story.CreateStoryForm;
import com.aloute.dto.story.StoryItemView;
import com.aloute.dto.story.StoryTrayView;
import com.aloute.dto.story.StoryViewerItem;
import com.aloute.model.story.Story;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.story.StoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @PostMapping("/stories")
    public Object create(@AuthenticationPrincipal AlouteUserPrincipal me,
                         @ModelAttribute CreateStoryForm form,
                         HttpServletRequest request,
                         RedirectAttributes redirect) {
        try {
            Story story = storyService.createStory(
                    me.id(),
                    form.mediaFile(),
                    form.caption(),
                    form.backgroundColor(),
                    form.visibility(),
                    form.storyType(),
                    form.emoji()
            );

            boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
            if (isAjax) {
                return ResponseEntity.ok(Map.of("ok", true, "storyId", story.getId().toString()));
            }

            redirect.addFlashAttribute("notice", "Đã đăng khoảnh khắc mới của bạn!");
            return "redirect:/";
        } catch (Exception e) {
            boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
            }
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/stories/api/tray")
    @ResponseBody
    public StoryTrayView getTray(@AuthenticationPrincipal AlouteUserPrincipal me) {
        return storyService.getStoryTray(me.id());
    }

    @GetMapping("/stories/api/users/{userId}")
    @ResponseBody
    public List<StoryItemView> getStoriesForUser(@PathVariable UUID userId,
                                                @AuthenticationPrincipal AlouteUserPrincipal me) {
        return storyService.getStoriesForUser(userId, me.id());
    }

    @PostMapping("/stories/api/{id}/view")
    @ResponseBody
    public Map<String, Object> recordView(@PathVariable UUID id,
                                          @AuthenticationPrincipal AlouteUserPrincipal me) {
        storyService.recordView(id, me.id());
        return Map.of("ok", true);
    }

    @PostMapping("/stories/api/{id}/react")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> react(@PathVariable UUID id,
                                                     @RequestParam String emoji,
                                                     @AuthenticationPrincipal AlouteUserPrincipal me) {
        try {
            storyService.recordReaction(id, me.id(), emoji);
            return ResponseEntity.ok(Map.of("ok", true, "emoji", emoji));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/stories/api/{id}/viewers")
    @ResponseBody
    public List<StoryViewerItem> getViewers(@PathVariable UUID id,
                                           @AuthenticationPrincipal AlouteUserPrincipal me) {
        return storyService.getViewers(id, me.id());
    }

    @PostMapping("/stories/api/{id}/reply")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reply(@PathVariable UUID id,
                                                     @RequestParam String content,
                                                     @AuthenticationPrincipal AlouteUserPrincipal me) {
        try {
            storyService.replyToStory(id, me.id(), content);
            return ResponseEntity.ok(Map.of("ok", true, "message", "Đã gửi phản hồi vào tin nhắn!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/stories/api/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id,
                                                      @AuthenticationPrincipal AlouteUserPrincipal me) {
        try {
            storyService.deleteStory(id, me.id());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/stories/api/{id}")
    @ResponseBody
    public ResponseEntity<StoryItemView> getStoryById(@PathVariable UUID id,
                                                      @AuthenticationPrincipal AlouteUserPrincipal me) {
        try {
            StoryItemView item = storyService.getStoryById(id, me.id());
            return ResponseEntity.ok(item);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/stories/{id}")
    public String viewStoryDirect(@PathVariable UUID id,
                                  @AuthenticationPrincipal AlouteUserPrincipal me,
                                  RedirectAttributes redirectAttributes) {
        try {
            StoryItemView item = storyService.getStoryById(id, me.id());
            return "redirect:/?viewStory=" + item.id() + "&author=" + item.authorId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Khoảnh khắc này đã biến mất sau 24h hoặc không còn tồn tại.");
            return "redirect:/";
        }
    }
}

