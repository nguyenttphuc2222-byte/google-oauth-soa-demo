package com.example.googleoauthspring;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** Danh tính giả lập cục bộ; controller chỉ tồn tại khi bật profile demo. */
@Controller
@Profile("demo")
public class DemoLoginController {

    private final SecurityContextRepository contextRepository;

    public DemoLoginController(SecurityContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    @GetMapping("/demo")
    public String demoLoginPage() {
        return "demo-login";
    }

    @PostMapping("/demo/login")
    public String loginLecturer(
            HttpServletRequest request, HttpServletResponse response) {
        Instant now = Instant.now();
        OidcIdToken token = OidcIdToken.withTokenValue("LOCAL_DEMO_NOT_A_GOOGLE_TOKEN")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject("local-demo-lecturer")
                .claim("name", "Giảng viên Demo")
                .claim("email", "giangvien.demo@tdtu.edu.vn")
                .claim("local_demo", true)
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_LECTURER"));
        var user = new DefaultOidcUser(authorities, token);
        var authentication = new OAuth2AuthenticationToken(user, authorities, "local-demo");

        // Giữ CSRF mặc định và đổi session ID giống một lần đăng nhập thông thường.
        new ChangeSessionIdAuthenticationStrategy()
                .onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return "redirect:/home";
    }
}
