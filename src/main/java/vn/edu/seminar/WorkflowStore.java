package vn.edu.seminar;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository
public class WorkflowStore {
    private final JdbcTemplate jdbc;
    private final AccountPolicy policy;
    public WorkflowStore(JdbcTemplate jdbc, AccountPolicy policy) { this.jdbc = jdbc; this.policy = policy; }
    private static final RowMapper<Submission> SUBMISSION = (rs, n) -> new Submission(
        rs.getString("id"), rs.getString("submitted_at"), rs.getString("student_sub"), rs.getString("student_name"),
        rs.getString("student_email"), rs.getString("group_name"), rs.getString("topic"), rs.getString("lecturer_email"),
        rs.getString("file_id"), rs.getString("file_name"), rs.getString("file_url"), rs.getLong("file_size"), rs.getString("phase"), rs.getBoolean("cc_self"), rs.getString("file_download_url"));
    private static final RowMapper<Review> REVIEW = (rs, n) -> new Review(
        rs.getString("id"), rs.getString("submission_id"), rs.getString("reviewed_at"), rs.getString("reviewer_sub"),
        rs.getString("reviewer_email"), rs.getString("decision"), rs.getString("comment_text"));
    private static final RowMapper<MailNotice> NOTICE = (rs, n) -> new MailNotice(
        rs.getString("id"), rs.getString("submission_id"), rs.getString("kind"), rs.getString("sender_sub"),
        rs.getString("sender_email"), rs.getString("to_email"), rs.getString("cc_email"), rs.getString("status"),
        rs.getString("message_id"), rs.getString("error_code"));
    public Optional<Submission> find(String id) {
        return jdbc.query("SELECT * FROM submissions WHERE id = ?", SUBMISSION, id).stream().findFirst();
    }
    public Submission require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài nộp."));
    }
    public List<Submission> visible(OidcUser user) {
        return policy.role(user) == AccountPolicy.Role.STUDENT
            ? jdbc.query("SELECT * FROM submissions WHERE student_sub = ? ORDER BY submitted_at DESC", SUBMISSION, user.getSubject())
            : jdbc.query("SELECT * FROM submissions WHERE lecturer_email = ? AND phase = 'READY' ORDER BY submitted_at DESC",
                SUBMISSION, AccountPolicy.normalize(user.getEmail()));
    }
    public List<Submission> unfinished(OidcUser user) {
        return jdbc.query("SELECT * FROM submissions WHERE student_sub = ? AND phase <> 'READY' ORDER BY submitted_at DESC", SUBMISSION, user.getSubject());
    }
    public Submission createDraft(String id, OidcUser user, String group, String topic, String lecturer, boolean ccSelf) {
        jdbc.update("""
            INSERT INTO submissions(id, submitted_at, student_sub, student_name, student_email, group_name, topic, lecturer_email, cc_self)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, Instant.now().toString(), user.getSubject(), Objects.toString(user.getFullName(), user.getEmail()),
            AccountPolicy.email(user.getEmail()), group, topic, lecturer, ccSelf);
        return require(id);
    }
    public Submission setDraftCc(String id, boolean ccSelf) {
        jdbc.update("UPDATE submissions SET cc_self = ? WHERE id = ? AND phase <> 'READY'", ccSelf, id);
        return require(id);
    }
    public Submission setFile(String id, DriveClient.Uploaded file) {
        jdbc.update("UPDATE submissions SET file_id = ?, file_name = ?, file_url = ?, file_size = ?, file_download_url = ?, phase = 'UPLOADED' WHERE id = ?",
            file.id(), file.name(), file.url(), file.size(), file.downloadUrl(), id);
        return require(id);
    }
    @Transactional
    public Submission completeSubmission(String id) {
        Submission submission = require(id);
        if (!submission.uploaded()) throw new IllegalStateException("Chưa có file Drive.");
        jdbc.update("UPDATE submissions SET phase = 'READY' WHERE id = ?", id);
        queue(id, "SUBMISSION", submission.studentSub(), submission.studentEmail(), submission.lecturerEmail(), submission.ccSelf() ? submission.studentEmail() : "");
        return require(id);
    }
    public Optional<Review> review(String submissionId) {
        return jdbc.query("SELECT * FROM reviews WHERE submission_id = ?", REVIEW, submissionId).stream().findFirst();
    }
    @Transactional
    public Review recordReview(Submission submission, OidcUser user, String decision, String comment, boolean ccSelf) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO reviews(id, submission_id, reviewed_at, reviewer_sub, reviewer_email, decision, comment_text)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, id, submission.id(), Instant.now().toString(), user.getSubject(), AccountPolicy.email(user.getEmail()), decision, comment);
        queue(submission.id(), "REVIEW", user.getSubject(), AccountPolicy.email(user.getEmail()), submission.studentEmail(), ccSelf ? AccountPolicy.email(user.getEmail()) : "");
        return review(submission.id()).orElseThrow();
    }
    private void queue(String submissionId, String kind, String sub, String from, String to, String cc) {
        if (notice(submissionId, kind).isPresent()) return;
        jdbc.update("""
            INSERT INTO mail_notices(id, submission_id, kind, sender_sub, sender_email, to_email, cc_email)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID().toString(), submissionId, kind, sub, from, to, cc);
    }
    public Optional<MailNotice> notice(String submissionId, String kind) {
        return jdbc.query("SELECT * FROM mail_notices WHERE submission_id = ? AND kind = ?", NOTICE, submissionId, kind).stream().findFirst();
    }
    public MailNotice noticeById(String id) {
        return jdbc.query("SELECT * FROM mail_notices WHERE id = ?", NOTICE, id).stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo email."));
    }
    public List<MailNotice> notices(String submissionId) {
        return jdbc.query("SELECT * FROM mail_notices WHERE submission_id = ? ORDER BY kind DESC", NOTICE, submissionId);
    }
    public void mailState(String id, String status, String messageId, String error) {
        jdbc.update("UPDATE mail_notices SET status = ?, message_id = ?, error_code = ? WHERE id = ?", status, messageId, error, id);
    }
    public boolean claimMail(String id) {
        return jdbc.update("UPDATE mail_notices SET status = 'SENDING', error_code = '' WHERE id = ? AND status IN ('PENDING', 'FAILED', 'UNKNOWN')", id) == 1;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedMail() {
        jdbc.update("UPDATE mail_notices SET status = 'UNKNOWN', error_code = 'INTERRUPTED' WHERE status = 'SENDING'");
    }
}
