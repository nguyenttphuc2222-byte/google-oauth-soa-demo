package com.example.googleoauthspring;

import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class HomeController {

    private final boolean demoEnabled;
    private final StudentApiClient students;

    public HomeController(
            Environment environment,
            StudentApiClient students) {

        demoEnabled = environment.acceptsProfiles(
                Profiles.of("demo"));

        this.students = students;
    }

    @ModelAttribute("demoEnabled")
    public boolean demoEnabled() {
        return demoEnabled;
    }

    @GetMapping({"/", "/login"})
    public String home() {
        return "home";
    }

    private void account(OidcUser user, Model model) {
        model.addAttribute("name", user.getFullName());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("role", StudentApiClient.roleOf(user));
        model.addAttribute(
                "localDemo",
                Boolean.TRUE.equals(user.getClaim("local_demo")));
    }

    @GetMapping("/home")
    public String userHome(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam(name = "q", defaultValue = "") String q,
            Model model) {

        account(user, model);

        if ("STUDENT".equals(StudentApiClient.roleOf(user))) {
            StudentProfile profile = students.ensureProfile(user);

            model.addAttribute("student", profile);
            model.addAttribute("form", profile.editableFields());
            model.addAttribute("canEdit", true);

            return "student-profile";
        }

        model.addAttribute("students", students.search(user, q));
        model.addAttribute("q", q);

        return "lecturer-home";
    }

    @GetMapping("/students/{mssv}")
    public String studentDetails(
            @PathVariable("mssv") String mssv,
            @AuthenticationPrincipal OidcUser user,
            Model model) {

        account(user, model);

        StudentProfile profile = students.details(user, mssv);

        model.addAttribute("student", profile);
        model.addAttribute("form", profile.editableFields());
        model.addAttribute("canEdit", false);

        return "student-profile";
    }

    @PostMapping("/students/me")
    public String updateOwnProfile(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam Map<String, String> form,
            Model model,
            HttpServletResponse response) {

        try {
            students.updateProfile(
                    user,
                    StudentProfile.updatePayload(form));

            return "redirect:/home?saved";

        } catch (IllegalArgumentException exception) {
            response.setStatus(400);
            model.addAttribute("error", exception.getMessage());

        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 422) {
                throw exception;
            }

            response.setStatus(422);
            model.addAttribute("error", exception.getReason());
        }

        account(user, model);

        model.addAttribute("student", students.ownProfile(user));
        model.addAttribute("form", form);
        model.addAttribute("canEdit", true);

        return "student-profile";
    }

    @GetMapping("/logout")
    public String logoutConfirmation() {
        return "logout-confirm";
    }
}