package vn.edu.seminar;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
@Controller
public class ActionController {
    private final SubmissionService submissions; private final ReviewService reviews;
    public ActionController(SubmissionService submissions, ReviewService reviews) { this.submissions = submissions; this.reviews = reviews; }
    @PostMapping("/submissions")
    public ResponseEntity<?> submit(@RequestParam String attemptId, @RequestParam String groupName,
            @RequestParam String topic, @RequestParam String lecturerEmail,
            @RequestParam(required = false) org.springframework.web.multipart.MultipartFile file,
            @RequestParam(defaultValue = "false") boolean ccSelf,
            @AuthenticationPrincipal OidcUser user, Authentication authentication, HttpServletRequest request) {
        try {
            var result = submissions.submit(attemptId, groupName, topic, lecturerEmail, file, ccSelf, user, authentication, request);
            request.getSession().setAttribute("ui.success", result.message());
            return redirect("/submissions/" + result.submission().id(), request);
        } catch (GoogleApiException | IllegalStateException exception) {
            request.getSession().setAttribute("ui.error", exception.getMessage() + " Tiếp tục cùng lượt nộp để dùng lại file đã tải lên.");
            return redirect("/submit?attemptId=" + attemptId, request);
        } catch (Scopes.Missing exception) {
            request.getSession().setAttribute("ui.error", exception.getMessage()); return redirect("/submit?attemptId=" + attemptId, request);
        }
    }
    private ResponseEntity<?> redirect(String path, HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
            ? ResponseEntity.ok(Map.of("redirect", path)) : ResponseEntity.status(303).location(URI.create(path)).build();
    }
    @PostMapping("/submissions/retry/{id}")
    public String retry(@PathVariable String id, @AuthenticationPrincipal OidcUser user,
            Authentication authentication, HttpServletRequest request) {
        var result = submissions.retry(id, user, authentication, request);
        request.getSession().setAttribute("ui.success", result.message()); return "redirect:/submissions/" + id;
    }
    @PostMapping("/reviews/{id}")
    public String review(@PathVariable String id, @RequestParam String decision,
            @RequestParam(defaultValue = "") String comment, @RequestParam(defaultValue = "false") boolean ccSelf, @AuthenticationPrincipal OidcUser user,
            Authentication authentication, HttpServletRequest request) {
        try {
            var result = reviews.review(id, decision, comment, ccSelf, user, authentication, request);
            request.getSession().setAttribute("ui.success", result.message());
        } catch (Scopes.Missing exception) {
            request.getSession().setAttribute("ui.error", "Chưa đủ quyền gửi Gmail. Nhận xét được giữ trong phiên; cấp quyền rồi xác nhận lại.");
        }
        return "redirect:/submissions/" + id;
    }
    @PostMapping("/notifications/{noticeId}/retry")
    public String resend(@PathVariable String noticeId, @RequestParam(defaultValue = "false") boolean acknowledgeUnknown,
            @AuthenticationPrincipal OidcUser user, Authentication authentication, HttpServletRequest request) {
        var result = reviews.resend(noticeId, acknowledgeUnknown, user, authentication, request);
        request.getSession().setAttribute("ui.success", result.message()); return "redirect:/submissions/" + result.notice().submissionId();
    }
}
