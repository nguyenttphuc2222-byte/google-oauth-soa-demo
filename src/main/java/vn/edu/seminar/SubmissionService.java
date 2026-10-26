package vn.edu.seminar;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SubmissionService {
    public static final String FOLDER = "seminar.authorizedFolder";
    public static class Attempt {
        private final String id, studentSub;
        private String groupName = "", topic = "", lecturerEmail = "", fileName = "", fileUrl = "";
        private boolean uploaded, ccSelf;
        Attempt(String id, String studentSub) { this.id = id; this.studentSub = studentSub; }
        Attempt(Submission submission) {
            this(submission.id(), submission.studentSub()); groupName = submission.groupName();
            topic = submission.topic(); lecturerEmail = submission.lecturerEmail();
            fileName = submission.fileName(); fileUrl = submission.fileActionUrl(); uploaded = submission.uploaded(); ccSelf = submission.ccSelf();
        }
        public String getId() { return id; }
        public String getGroupName() { return groupName; }
        public String getTopic() { return topic; }
        public String getLecturerEmail() { return lecturerEmail; }
        public String getFileName() { return fileName; }
        public String getFileUrl() { return fileUrl; }
        public boolean isUploaded() { return uploaded; }
        public boolean isCcSelf() { return ccSelf; }
    }
    public record Outcome(Submission submission, String message) {}
    private final AppSettings app;
    private final AccountPolicy policy;
    private final TokenService tokens;
    private final DriveClient drive;
    private final WorkflowStore store;
    private final UploadValidator validator;
    private final MailDelivery delivery;
    private final WorkflowLocks locks;
    private final DriveAccessService access;
    public SubmissionService(AppSettings app, AccountPolicy policy, TokenService tokens, DriveClient drive,
            WorkflowStore store, UploadValidator validator, MailDelivery delivery, WorkflowLocks locks, DriveAccessService access) {
        this.app = app; this.policy = policy; this.tokens = tokens; this.drive = drive;
        this.store = store; this.validator = validator; this.delivery = delivery; this.locks = locks; this.access = access;
    }
    @SuppressWarnings("unchecked")
    private Map<String, Attempt> attempts(HttpServletRequest request) {
        var session = request.getSession();
        synchronized (session) {
            var result = (Map<String, Attempt>) session.getAttribute("seminar.attempts");
            if (result == null) { result = new LinkedHashMap<>(); session.setAttribute("seminar.attempts", result); }
            return result;
        }
    }
    public Attempt prepare(OidcUser user, HttpServletRequest request) {
        policy.require(user, AccountPolicy.Role.STUDENT);
        synchronized (request.getSession()) {
            var map = attempts(request);
            if (map.size() >= 30) map.remove(map.keySet().iterator().next());
            var attempt = new Attempt(UUID.randomUUID().toString(), user.getSubject());
            map.put(attempt.id, attempt); return attempt;
        }
    }
    public Attempt resume(String id, OidcUser user, HttpServletRequest request) {
        policy.require(user, AccountPolicy.Role.STUDENT);
        var existing = store.find(id);
        if (existing.isPresent()) {
            if (!existing.get().studentSub().equals(user.getSubject())) throw new AccessDeniedException("Lượt nộp không thuộc bạn.");
            return new Attempt(existing.get());
        }
        var attempt = attempts(request).get(id);
        if (attempt == null || !attempt.studentSub.equals(user.getSubject()))
            throw new AccessDeniedException("Biểu mẫu đã hết phiên. Mở lại trang Nộp báo cáo.");
        return attempt;
    }
    public List<Attempt> pending(OidcUser user, HttpServletRequest request) {
        policy.require(user, AccountPolicy.Role.STUDENT);
        return store.unfinished(user).stream().map(Attempt::new).toList();
    }
    public Outcome submit(String id, String group, String topic, String lecturer, MultipartFile file, boolean ccSelf,
            OidcUser user, Authentication authentication, HttpServletRequest request) {
        policy.require(user, AccountPolicy.Role.STUDENT);
        final String cleanGroup = text(group, "Tên nhóm", 100), cleanTopic = text(topic, "Đề tài", 300);
        final String cleanLecturer = AccountPolicy.normalize(lecturer);
        if (access.automatic() && !DriveAccessService.schoolEmail(cleanLecturer)) throw new IllegalArgumentException("Chế độ Drive tự động chỉ chia sẻ bài cho email TDTU.");
        if (!policy.isLecturer(cleanLecturer)) throw new IllegalArgumentException("Nhập email giảng viên hợp lệ theo quy tắc phân vai.");
        return locks.run(id, () -> {
            var attempt = resume(id, user, request);
            var existing = store.find(id);
            if (existing.isPresent() && existing.get().ready())
                return new Outcome(existing.get(), "Bài này đã được ghi nhận. App không upload hoặc gửi mail lặp.");
            attempt.groupName = cleanGroup; attempt.topic = cleanTopic; attempt.lecturerEmail = cleanLecturer; attempt.ccSelf = ccSelf;
            if (existing.isPresent()) store.setDraftCc(id, ccSelf);
            String token = tokens.require(authentication, request, "submit", Scopes.DRIVE, Scopes.MAIL);
            var target = access.target(user, request);
            var folder = (DriveClient.Folder) request.getSession().getAttribute(FOLDER);
            if (folder == null || !target.id().equals(folder.id()))
                throw new IllegalArgumentException("Chọn thư mục lớp bằng Google Picker trước khi nộp.");
            if (existing.isPresent() && (!existing.get().groupName().equals(cleanGroup)
                    || !existing.get().topic().equals(cleanTopic) || !existing.get().lecturerEmail().equals(cleanLecturer)))
                throw new IllegalArgumentException("Giữ thông tin của lượt nộp khi thử lại. Tạo bài mới nếu muốn đổi nội dung.");
            // Validate before creating a new durable draft. Uploaded drafts can continue without the original file.
            UploadValidator.Validated checked = existing.isEmpty() ? validator.validate(file) : null;
            var submission = existing.isPresent() ? store.require(id) : store.createDraft(id, user, cleanGroup, cleanTopic, cleanLecturer, ccSelf);
            if (!submission.uploaded()) {
                var uploaded = drive.find(id, target.id(), token, request);
                if (uploaded == null) uploaded = drive.upload(id, user.getSubject(), checked != null ? checked : validator.validate(file), target.id(), token, request);
                submission = store.setFile(id, uploaded);
            }
            if (access.automatic()) drive.requireParent(submission.fileId(), target.id(), token, request);
            drive.ensureReader(submission.fileId(), cleanLecturer, token, request);
            submission = store.completeSubmission(id);
            var notice = store.notice(id, "SUBMISSION").orElseThrow();
            var result = delivery.deliver(notice.id(), false, user, authentication, request);
            return new Outcome(submission, "Đã nộp bài; trạng thái Chờ duyệt. " + result.message());
        });
    }
    public Outcome retry(String id, OidcUser user, Authentication authentication, HttpServletRequest request) {
        var stored = store.require(id); policy.require(user, AccountPolicy.Role.STUDENT); policy.requireView(stored, user);
        return submit(id, stored.groupName(), stored.topic(), stored.lecturerEmail(), null, stored.ccSelf(), user, authentication, request);
    }
    public static String text(String value, String label, int max) {
        if (value == null || value.trim().isBlank() || value.trim().length() > max || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label + " phải có nội dung, tối đa " + max + " ký tự và không chứa ký tự điều khiển.");
        return value.trim();
    }
}
