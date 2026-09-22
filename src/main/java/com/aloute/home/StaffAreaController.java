package com.aloute.home;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Cổng vào các khu vực theo vai trò. Ở GĐ1 chỉ là trang khung để kiểm chứng phân quyền
 * (quyền truy cập được chặn ở SecurityConfig); nội dung thật làm ở GĐ4–5.
 */
@Controller
public class StaffAreaController {

    @GetMapping("/creator")
    public String creator(Model model) {
        return area(model, "creator", "Creator Studio",
                "Thống kê lượt tiếp cận, hẹn giờ đăng bài, nhận donate và tặng huy hiệu cho fan cứng.", "4");
    }

    @GetMapping("/manage")
    public String manage(Model model) {
        return area(model, "manage", "Kiểm duyệt",
                "Xử lý báo cáo, ẩn/xóa nội dung vi phạm, cảnh báo hoặc khóa tài khoản và hỗ trợ người dùng.", "5");
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        return area(model, "admin", "Quản trị",
                "Tạo và phân quyền Manager, cấu hình hệ thống, xem nhật ký và thống kê tổng thể.", "5");
    }

    private static String area(Model model, String active, String title, String description, String phase) {
        model.addAttribute("active", active);
        model.addAttribute("areaTitle", title);
        model.addAttribute("areaDescription", description);
        model.addAttribute("phase", phase);
        return "staff/area";
    }
}
