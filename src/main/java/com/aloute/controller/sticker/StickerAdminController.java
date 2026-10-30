package com.aloute.controller.sticker;

import com.aloute.dto.admin.StaffMember;
import com.aloute.exception.admin.InvalidAdminActionException;
import com.aloute.exception.chat.ChatActionException;
import com.aloute.model.sticker.StickerPack;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.sticker.StickerAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller
@RequestMapping("/admin/stickers")
public class StickerAdminController {
    private final StickerAdminService admin;
    public StickerAdminController(StickerAdminService admin) { this.admin=admin; }
    @GetMapping
    public String list(@AuthenticationPrincipal AlouteUserPrincipal me,Model model) {
        model.addAttribute("packs",admin.list(me.id()));return "admin/stickers";
    }
    @PostMapping
    public String create(@AuthenticationPrincipal AlouteUserPrincipal me,@RequestParam String name,
                         @RequestParam(defaultValue="") String description,@RequestParam StickerPack.AccessType accessType,RedirectAttributes flash) {
        try { return "redirect:/admin/stickers/"+admin.create(me.id(),name,description,accessType); }
        catch(InvalidAdminActionException e) { flash.addFlashAttribute("error",e.getMessage());flash.addFlashAttribute("draftName",name);flash.addFlashAttribute("draftDescription",description);flash.addFlashAttribute("draftType",accessType.name());return "redirect:/admin/stickers"; }
    }
    @GetMapping("/recipients") @ResponseBody
    public List<StaffMember> recipients(@AuthenticationPrincipal AlouteUserPrincipal me,@RequestParam String q) { return admin.searchRecipients(me.id(),q); }
    @GetMapping("/{id}")
    public String edit(@AuthenticationPrincipal AlouteUserPrincipal me,@PathVariable String id,
                       @RequestParam(required=false) Integer granted,Model model) {
        var pack=admin.get(me.id(),id);
        model.addAttribute("pack",pack);model.addAttribute("editableImages",admin.editableImages(me.id(),id));
        model.addAttribute("recipients",pack.accessType().equals("AWARD")?admin.recipients(me.id(),id):List.of());
        if(granted!=null && granted>=0 && granted<=100)model.addAttribute("notice","Đã cấp bộ nhãn dán cho "+granted+" người nhận mới. Người đã có bộ không bị cấp trùng.");
        return "admin/sticker-edit";
    }
    @PostMapping("/{id}/save")
    public String save(@AuthenticationPrincipal AlouteUserPrincipal me,@PathVariable String id,@RequestParam String name,
                       @RequestParam(defaultValue="") String description,@RequestParam StickerPack.AccessType accessType,
                       @RequestParam(required=false) MultipartFile icon,RedirectAttributes flash) {
        return action(id,flash,"Đã lưu thông tin bộ.",()->admin.save(me.id(),id,name,description,accessType,icon));
    }
    @PostMapping("/{id}/upload")
    public String upload(@AuthenticationPrincipal AlouteUserPrincipal me,@PathVariable String id,
                         @RequestParam List<MultipartFile> files,@RequestParam(required=false) List<String> names,RedirectAttributes flash) {
        return action(id,flash,"Đã thêm nhãn dán.",()->admin.upload(me.id(),id,files,names));
    }
    @PostMapping("/{id}/items/{stickerId}")
    public String update(@AuthenticationPrincipal AlouteUserPrincipal me,@PathVariable String id,@PathVariable String stickerId,
                         @RequestParam String name,@RequestParam(defaultValue="") String keywords,@RequestParam int sortOrder,
                         @RequestParam(required=false) MultipartFile image,RedirectAttributes flash) {
        return action(id,flash,"Đã lưu nhãn dán.",()->admin.updateSticker(me.id(),id,stickerId,name,keywords,sortOrder,image));
    }
    @PostMapping("/{id}/items/{stickerId}/delete")
    public String delete(@AuthenticationPrincipal AlouteUserPrincipal me,@PathVariable String id,@PathVariable String stickerId,RedirectAttributes flash) {
        return action(id,flash,"Đã xóa nhãn dán khỏi bộ.",()->admin.deleteSticker(me.id(),id,stickerId));
    }
    @PostMapping("/{id}/publish")
    public String publish(@AuthenticationPrincipal AlouteUserPrincipal me,@PathVariable String id,RedirectAttributes flash) {
        return action(id,flash,"Đã xuất bản bộ nhãn dán.",()->admin.publish(me.id(),id));
    }
    @PostMapping("/{id}/retire")
    public String retire(@AuthenticationPrincipal AlouteUserPrincipal me,@PathVariable String id,RedirectAttributes flash) {
        return action(id,flash,"Đã ngừng cung cấp. Người đã sở hữu vẫn sử dụng được.",()->admin.retire(me.id(),id));
    }
    @PostMapping("/{id}/grants") @ResponseBody
    public ResponseEntity<Map<String,Object>> grant(@AuthenticationPrincipal AlouteUserPrincipal me,@PathVariable String id,
                                @RequestParam List<UUID> recipientIds,@RequestParam String reason) {
        try {
            int count=admin.grant(me.id(),id,recipientIds,reason);
            return ResponseEntity.ok(Map.of("granted",count,"redirectUrl","/admin/stickers/"+id+"?granted="+count));
        } catch(InvalidAdminActionException|ChatActionException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
    }
    @PostMapping("/{id}/revoke/{user}")
    public String revoke(@AuthenticationPrincipal AlouteUserPrincipal me,@PathVariable String id,@PathVariable UUID user,RedirectAttributes flash) {
        return action(id,flash,"Đã thu hồi quyền dùng. Tin nhắn cũ vẫn hiển thị.",()->admin.revoke(me.id(),id,user));
    }
    private String action(String id,RedirectAttributes flash,String success,Runnable action) {
        try { action.run();flash.addFlashAttribute("notice",success); }
        catch(InvalidAdminActionException|ChatActionException e) { flash.addFlashAttribute("error",e.getMessage()); }
        return "redirect:/admin/stickers/"+id;
    }
}
