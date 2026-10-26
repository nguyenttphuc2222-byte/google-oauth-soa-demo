package vn.edu.seminar;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class MailService {
    private final AppSettings app;
    private final AccountPolicy policy;
    private final GoogleHttp http;
    public MailService(AppSettings app, AccountPolicy policy, GoogleHttp http) {
        this.app = app; this.policy = policy; this.http = http;
    }
    public String raw(MailNotice notice, Submission submission, Review review, OidcUser actor) {
        String from = AccountPolicy.email(actor.getEmail());
        if (!notice.senderSub().equals(actor.getSubject()) || !notice.senderEmail().equals(from))
            throw new AccessDeniedException("Thông báo không thuộc người gửi đang đăng nhập.");
        boolean initial = "SUBMISSION".equals(notice.kind());
        if (initial) {
            policy.require(actor, AccountPolicy.Role.STUDENT);
            if (!submission.studentSub().equals(actor.getSubject())) throw new AccessDeniedException("Sai người nộp.");
        } else {
            policy.requireReview(submission, actor);
            if (review == null || !review.reviewerSub().equals(actor.getSubject()))
                throw new AccessDeniedException("Sai người xét duyệt.");
        }
        String to = AccountPolicy.email(initial ? submission.lecturerEmail() : submission.studentEmail());
        if (!notice.toEmail().equals(to) || (!notice.ccEmail().isBlank() && !notice.ccEmail().equals(from))
                || !policy.isLecturer(submission.lecturerEmail()) || !policy.isStudent(submission.studentEmail()))
            throw new IllegalArgumentException("Người nhận không khớp bài nộp hoặc cấu hình vai trò.");
        String title = initial ? "Có bài nộp seminar mới" : "Kết quả xét duyệt: " + review.decisionLabel();
        String body = title + "\n\nMã bài nộp: " + submission.id()
                + "\nSinh viên: " + submission.studentName() + " <" + submission.studentEmail() + ">"
                + "\nNhóm: " + submission.groupName() + "\nĐề tài: " + submission.topic()
                + "\nThời gian nộp: " + submission.submittedAt()
                + "\nGiảng viên phụ trách: " + submission.lecturerEmail()
                + (initial ? "" : "\nKết quả: " + review.decisionLabel()
                        + "\nNhận xét: " + (review.comment().isBlank() ? "Không có nhận xét bổ sung." : review.comment()))
                + "\nFile bài nộp: " + submission.fileName()
                + "\n" + (submission.zip() ? "Tải ZIP (đăng nhập Google Drive): " : "Xem tài liệu: ") + submission.fileActionUrl()
                + "\n" + (initial ? "Mở trang xét duyệt: " : "Xem kết quả trên website: ") + app.submissionUrl(submission.id())
                + "\n\nĐăng nhập đúng tài khoản Google để truy cập. Thư được gửi bằng quyền Gmail của người thực hiện.";
        try {
            var message = new MimeMessage(Session.getInstance(new Properties()));
            message.setFrom(new InternetAddress(from));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            if (!notice.ccEmail().isBlank()) message.setRecipient(Message.RecipientType.CC, new InternetAddress(from));
            message.setSubject("[Seminar] " + title + " - " + submission.groupName(), StandardCharsets.UTF_8.name());
            message.setSentDate(new Date());
            var plain = new MimeBodyPart(); plain.setText(body, StandardCharsets.UTF_8.name());
            var html = new MimeBodyPart();
            html.setContent("<div style=\"font-family:Arial,sans-serif;line-height:1.7;color:#172b3a\">"
                    + "<h2>" + HtmlUtils.htmlEscape(title) + "</h2><p style=\"white-space:pre-wrap\">"
                    + HtmlUtils.htmlEscape(body) + "</p><p><a href=\""
                    + HtmlUtils.htmlEscape(submission.fileActionUrl()) + "\">"
                    + HtmlUtils.htmlEscape(submission.fileActionLabel()) + "</a></p><p><a style=\"display:inline-block;background:#2455d6;color:white;"
                    + "padding:12px 20px;border-radius:8px;text-decoration:none\" href=\""
                    + HtmlUtils.htmlEscape(app.submissionUrl(submission.id())) + "\">"
                    + (initial ? "Xem và xét duyệt bài nộp" : "Xem kết quả bài nộp") + "</a></p></div>",
                    "text/html; charset=UTF-8");
            var alternative = new MimeMultipart("alternative"); alternative.addBodyPart(plain); alternative.addBodyPart(html);
            message.setContent(alternative); message.saveChanges();
            // A stable Message-ID helps manual tracing; Gmail does not promise deduplication by this header.
            message.setHeader("Message-ID", "<" + notice.id() + "@seminar.local>");
            var output = new ByteArrayOutputStream(); message.writeTo(output);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
        } catch (MessagingException | java.io.IOException exception) {
            throw new IllegalArgumentException("Không tạo được thư MIME hợp lệ.");
        }
    }
    public String send(MailNotice notice, Submission submission, Review review, OidcUser actor, String token, HttpServletRequest request) {
        var result = http.json(HttpMethod.POST, URI.create(app.google().gmailBase() + "/users/me/messages/send"),
                token, Map.of("raw", raw(notice, submission, review, actor)), "Gmail · " + notice.kindLabel(), request);
        if (result.path("id").asText().isBlank()) throw new GoogleApiException(0, "Gmail · phản hồi gửi thư");
        return result.path("id").asText();
    }
}
