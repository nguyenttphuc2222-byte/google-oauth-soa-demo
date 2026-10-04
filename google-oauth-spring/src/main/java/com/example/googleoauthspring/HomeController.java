package com.example.googleoauthspring;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // Trang đăng nhập.
    @GetMapping({"/", "/login"})
    public String home() {
        return "home";
    }

    // Trang chính sau khi đăng nhập.
    @GetMapping("/home")
    public String userHome(
            @AuthenticationPrincipal OidcUser user,
            Model model) {

        model.addAttribute("name", user.getFullName());
        model.addAttribute("email", user.getEmail());

        return "user-home";
    }

    // GET /logout chỉ hiển thị trang xác nhận.
    // POST /logout do Spring Security xử lý để đăng xuất.
    @GetMapping("/logout")
    public String logoutConfirmation() {
        return "logout-confirm";
    }
}