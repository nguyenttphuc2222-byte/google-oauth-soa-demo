package vn.edu.seminar;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller
public class MainController {
    public record Row(Submission submission, Review review) {
        public String status() { return !submission.ready() ? "DRAFT" : review == null ? "PENDING" : review.decision(); }
        public String label() { return !submission.ready() ? "Chưa hoàn tất" : review == null ? "Chờ duyệt" : review.decisionLabel(); }
        public String css() { return review == null ? "pending" : review.statusClass(); }
    }
    private final WorkflowStore store; private final AccountPolicy policy;
    private final SubmissionService submissions; private final ReviewService reviews;
    private final LecturerDirectory lecturerDirectory;
    public MainController(WorkflowStore store, AccountPolicy policy, SubmissionService submissions, ReviewService reviews,
            LecturerDirectory lecturerDirectory) {
        this.store = store; this.policy = policy; this.submissions = submissions; this.reviews = reviews;
        this.lecturerDirectory = lecturerDirectory;
    }
    @GetMapping({"/", "/login"}) public String login(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof OidcUser ? "redirect:/dashboard" : "login";
    }
    @GetMapping("/health") @ResponseBody public Map<String, String> health() { return Map.of("status", "ok", "version", "2.4.0"); }
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OidcUser user, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "ALL") String status, Model model) {
        var rows = store.visible(user).stream().map(s -> new Row(s, store.review(s.id()).orElse(null))).toList();
        model.addAttribute("total", rows.size());
        model.addAttribute("pendingCount", rows.stream().filter(row -> row.status().equals("PENDING")).count());
        model.addAttribute("approvedCount", rows.stream().filter(row -> row.status().equals("APPROVED")).count());
        model.addAttribute("rejectedCount", rows.stream().filter(row -> row.status().equals("REJECTED")).count());
        String query = q.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("rows", rows.stream().filter(row -> status.equals("ALL") || row.status().equals(status))
            .filter(row -> query.isBlank() || (row.submission().groupName() + " " + row.submission().topic() + " "
                + row.submission().studentName() + " " + row.submission().studentEmail()).toLowerCase(Locale.ROOT).contains(query)).toList());
        model.addAttribute("q", q); model.addAttribute("statusFilter", status); return "dashboard";
    }
    @GetMapping("/submit")
    public String submit(@AuthenticationPrincipal OidcUser user, @RequestParam(defaultValue = "") String attemptId,
            Model model, HttpServletRequest request) {
        policy.require(user, AccountPolicy.Role.STUDENT);
        model.addAttribute("lecturers", lecturerDirectory.suggestions());
        model.addAttribute("attempt", attemptId.isBlank() ? submissions.prepare(user, request) : submissions.resume(attemptId, user, request));
        model.addAttribute("pendingAttempts", submissions.pending(user, request)); return "submit";
    }
    @GetMapping("/submissions/{id}")
    public String detail(@PathVariable String id, @AuthenticationPrincipal OidcUser user, Model model, HttpServletRequest request) {
        var submission = store.require(id); policy.requireView(submission, user);
        var review = store.review(id).orElse(null);
        boolean canReview = policy.role(user) == AccountPolicy.Role.LECTURER && review == null && submission.ready();
        model.addAttribute("submission", submission); model.addAttribute("latest", review);
        model.addAttribute("canReview", canReview); model.addAttribute("notices", store.notices(id));
        if (canReview) model.addAttribute("draft", reviews.draft(id, request)); return "detail";
    }
    @RequestMapping("/error/forbidden") @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden(Model model) {
        model.addAttribute("errorTitle", "Thao tác không được phép");
        model.addAttribute("errorMessage", "Backend đã chặn yêu cầu vì vai trò hoặc CSRF không hợp lệ. Tải lại trang và dùng đúng tài khoản.");
        return "problem";
    }
}
