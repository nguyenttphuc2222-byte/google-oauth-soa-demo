package com.example.googleoauthspring;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=local-test-client",
    "spring.security.oauth2.client.registration.google.client-secret=local-test-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class StudentAccessTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    StudentApiClient students;

    @Test
    void lecturerCannotUpdateProfiles() throws Exception {
        mvc.perform(post("/students/me")
                .with(oidcLogin().authorities(
                        new SimpleGrantedAuthority("ROLE_LECTURER")))
                .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(students);
    }

    @Test
    void studentCannotOpenAnotherProfile() throws Exception {
        mvc.perform(get("/students/demo002")
                .with(oidcLogin().authorities(
                        new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(students);
    }

    @Test
    void rejectsChangesToFixedFields() throws Exception {
        var profile = new StudentProfile(
                "demo001", "Demo", 20, "Khác",
                "0900000000", "Địa chỉ",
                "demo001@student.tdtu.edu.vn", null, "2023");

        when(students.ownProfile(any(OidcUser.class)))
                .thenReturn(profile);

        mvc.perform(post("/students/me")
                .with(oidcLogin().authorities(
                        new SimpleGrantedAuthority("ROLE_STUDENT")))
                .with(csrf())
                .param("age", "20")
                .param("mssv", "hacked")
                .param("enrollment_year", "2020"))
                .andExpect(status().isBadRequest());

        verify(students, never())
                .updateProfile(any(OidcUser.class), anyMap());
    }

    @Test
    void editsProfileWithoutChangingLoginIdentity() throws Exception {
        mvc.perform(post("/students/me")
                .with(oidcLogin()
                        .idToken(token -> token.claim(
                                "email", "demo001@student.tdtu.edu.vn"))
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_STUDENT")))
                .with(csrf())
                .param("full_name", "Demo")
                .param("age", "21")
                .param("gender", "Nam")
                .param("phone", "0901234567")
                .param("address", "Địa chỉ mới")
                .param("student_email", "new@example.com")
                .param("personal_email", ""))
                .andExpect(redirectedUrl("/home?saved"));

        verify(students).updateProfile(
                argThat(user ->
                        "demo001@student.tdtu.edu.vn"
                                .equals(user.getEmail())),
                argThat(payload ->
                        Integer.valueOf(21).equals(payload.get("age"))
                        && "new@example.com".equals(
                                payload.get("student_email"))
                        && payload.get("personal_email") == null
                        && !payload.containsKey("mssv")
                        && !payload.containsKey("enrollment_year")
                        && !payload.containsKey("_csrf")));
    }
}