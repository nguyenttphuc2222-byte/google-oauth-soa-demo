package com.example.googleoauthspring;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=local-test-client",
    "spring.security.oauth2.client.registration.google.client-secret=local-test-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoLoginTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StudentApiClient students;

    @Test
    void createsFixedLecturerIdentityAndPersistsSession() throws Exception {
        // Giả lập danh sách rỗng để test không gọi student-service thật.
        when(students.search(any(OidcUser.class), anyString()))
                .thenReturn(List.of());

        // Mở form đăng nhập demo và lấy session ban đầu.
        var form = mvc.perform(get("/demo"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Giảng viên Demo")))
                .andReturn();

        var session = (MockHttpSession) form.getRequest().getSession();
        String oldSessionId = session.getId();

        // Các tham số do trình duyệt gửi không được đổi danh tính demo.
        mvc.perform(post("/demo/login")
                .session(session)
                .with(csrf())
                .param("email", "somebody@gmail.com")
                .param("role", "STUDENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));

        // Đăng nhập phải đổi session ID.
        assertNotEquals(oldSessionId, session.getId());

        // Danh tính được lưu trong session phải là giảng viên demo.
        var context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY);

        assertEquals(
                "local-demo-lecturer",
                context.getAuthentication().getName());

        // Phiên đăng nhập mở được trang home dành cho giảng viên.
        mvc.perform(get("/home").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("lecturer-home"))
                .andExpect(model().attribute("role", "LECTURER"))
                .andExpect(model().attribute(
                        "email", "giangvien.demo@tdtu.edu.vn"))
                .andExpect(model().attribute("localDemo", true));
    }

    @Test
    void requiresCsrfTokenForDemoLogin() throws Exception {
        // Yêu cầu POST thiếu CSRF token phải bị chặn.
        mvc.perform(post("/demo/login"))
                .andExpect(status().isForbidden());
    }
}