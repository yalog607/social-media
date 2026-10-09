package com.aloute.controller.wallet;

import com.aloute.exception.wallet.WalletException;
import com.aloute.service.wallet.WalletService;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

/** Trang ví Xu, nhận Xu hằng ngày, donate cho Creator và mở khóa bài trả phí. */
@Controller
public class WalletController {

    private final WalletService wallet;

    public WalletController(WalletService wallet) {
        this.wallet = wallet;
    }

    @GetMapping("/wallet")
    public String page(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("wallet", wallet.view(me.id()));
        model.addAttribute("dailyBonus", WalletService.DAILY_BONUS);
        return "wallet/index";
    }

    @PostMapping("/wallet/daily")
    public String claimDaily(@AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash) {
        try {
            wallet.claimDaily(me.id());
            flash.addFlashAttribute("notice", "Đã nhận " + WalletService.DAILY_BONUS + " Xu hôm nay!");
        } catch (WalletException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/wallet";
    }

    @PostMapping("/api/donations")
    @ResponseBody
    public Map<String, Object> donate(@AuthenticationPrincipal AlouteUserPrincipal me,
                                      @RequestParam UUID toUserId, @RequestParam long amount) {
        wallet.donate(me.id(), toUserId, amount);
        return Map.of("balance", wallet.balance(me.id()));
    }

    @PostMapping("/api/posts/{id}/unlock")
    @ResponseBody
    public Map<String, Object> unlock(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id) {
        return Map.of("balance", wallet.unlock(me.id(), id));
    }

    @ExceptionHandler(WalletException.class)
    public ResponseEntity<Map<String, String>> invalid(WalletException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
