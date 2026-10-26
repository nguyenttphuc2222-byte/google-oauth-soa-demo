package vn.edu.seminar;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class MailDelivery {
    public record Outcome(MailNotice notice, String message) {}
    private final WorkflowStore store;
    private final MailService mail;
    private final TokenService tokens;
    private final AccountPolicy policy;
    public MailDelivery(WorkflowStore store, MailService mail, TokenService tokens, AccountPolicy policy) {
        this.store = store; this.mail = mail; this.tokens = tokens; this.policy = policy;
    }
    /** Called while holding WorkflowLocks for the submission. No automatic network retry for send. */
    public Outcome deliver(String noticeId, boolean acknowledgeUnknown, OidcUser actor,
            Authentication authentication, HttpServletRequest request) {
        var notice = store.noticeById(noticeId);
        var submission = store.require(notice.submissionId());
        policy.requireView(submission, actor);
        if (!notice.senderSub().equals(actor.getSubject())) throw new AccessDeniedException("Chỉ người gửi được gửi lại thông báo của mình.");
        if (notice.sent()) return new Outcome(notice, "Gmail đã xác nhận gửi thư này. App không gửi thêm.");
        if (notice.uncertain() && !acknowledgeUnknown)
            throw new IllegalArgumentException("Chưa rõ Gmail đã gửi hay chưa. Kiểm tra mục Đã gửi (Sent) trước khi xác nhận gửi lại.");
        String purpose = "SUBMISSION".equals(notice.kind()) ? "submit" : "review";
        String token = tokens.require(authentication, request, purpose, Scopes.MAIL);
        var review = store.review(submission.id()).orElse(null);
        // Validate MIME and recipients before recording an in-flight network operation.
        mail.raw(notice, submission, review, actor);
        if (notice.uncertain() && acknowledgeUnknown) store.mailState(noticeId, "UNKNOWN", "", "MANUAL_RETRY");
        if (!store.claimMail(noticeId)) return new Outcome(store.noticeById(noticeId), "Thông báo đã được xử lý ở yêu cầu khác.");
        final String messageId;
        try {
            messageId = mail.send(notice, submission, review, actor, token, request);
        } catch (GoogleApiException exception) {
            store.mailState(noticeId, exception.uncertain() ? "UNKNOWN" : "FAILED", "",
                    exception.status == 0 ? "NETWORK" : "HTTP_" + exception.status);
            return new Outcome(store.noticeById(noticeId), exception.uncertain()
                    ? "Đã lưu dữ liệu. Chưa xác định Gmail đã gửi thư hay chưa; kiểm tra Sent trước khi gửi lại."
                    : "Đã lưu dữ liệu nhưng Gmail từ chối gửi thư. Kiểm tra quyền rồi dùng Gửi lại thông báo.");
        }
        try {
            store.mailState(noticeId, "SENT", messageId, "");
        } catch (DataAccessException exception) {
            // SENDING remains durable; restart recovery turns it into UNKNOWN. Never retry send blindly.
            throw new IllegalStateException("Gmail đã xác nhận gửi, nhưng chưa lưu được trạng thái. Kiểm tra Sent; không bấm gửi lại ngay.");
        }
        return new Outcome(store.noticeById(noticeId), "Đã gửi email thông báo thành công.");
    }
}
