package com.example.googleoauthspring;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=local-test-client",
    "spring.security.oauth2.client.registration.google.client-secret=local-test-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NormalModeDemoDisabledTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void rejectsDemoEndpointsOutsideDemoProfile() throws Exception {
        mvc.perform(get("/demo").with(oidcLogin()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/demo/login").with(oidcLogin()).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
