package vn.edu.seminar;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {
    public record Draft(String decision, String comment, boolean ccSelf) {}
    public record Outcome(Review review, String message) {}
    private final WorkflowStore store;
    private final AccountPolicy policy;
    private final TokenService tokens;
    private final MailDelivery delivery;
    private final WorkflowLocks locks;
    public ReviewService(WorkflowStore store, AccountPolicy policy, TokenService tokens, MailDelivery delivery, WorkflowLocks locks) {
        this.store = store; this.policy = policy; this.tokens = tokens; this.delivery = delivery; this.locks = locks;
    }
    public Draft draft(String id, HttpServletRequest request) {
        var stored = (Draft) request.getSession().getAttribute("seminar.review." + id);
        return stored == null ? new Draft("APPROVED", "", false) : stored;
    }
    public Outcome review(String id, String decision, String comment, boolean ccSelf, OidcUser user,
            Authentication authentication, HttpServletRequest request) {
        var submission = store.require(id); policy.requireReview(submission, user);
        if (!Set.of("APPROVED", "REJECTED").contains(decision)) throw new IllegalArgumentException("Kết quả không hợp lệ.");
        String cleanComment = comment == null ? "" : comment.trim();
        if (cleanComment.length() > 2000) throw new IllegalArgumentException("Nhận xét tối đa 2.000 ký tự.");
        request.getSession().setAttribute("seminar.review." + id, new Draft(decision, cleanComment, ccSelf));
        return locks.run(id, () -> {
            var existing = store.review(id);
            if (existing.isPresent()) return new Outcome(existing.get(), "Bài đã được xét duyệt. App không ghi hoặc gửi email lặp.");
            tokens.require(authentication, request, "review", Scopes.MAIL);
            var review = store.recordReview(submission, user, decision, cleanComment, ccSelf);
            var notice = store.notice(id, "REVIEW").orElseThrow();
            var result = delivery.deliver(notice.id(), false, user, authentication, request);
            request.getSession().removeAttribute("seminar.review." + id);
            return new Outcome(review, "Đã lưu kết quả " + review.decisionLabel() + ". " + result.message());
        });
    }
    public MailDelivery.Outcome resend(String noticeId, boolean acknowledgeUnknown, OidcUser user,
            Authentication authentication, HttpServletRequest request) {
        var notice = store.noticeById(noticeId);
        return locks.run(notice.submissionId(), () -> delivery.deliver(noticeId, acknowledgeUnknown, user, authentication, request));
    }
}
