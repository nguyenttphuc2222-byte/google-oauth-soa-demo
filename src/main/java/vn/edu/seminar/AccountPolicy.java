package vn.edu.seminar;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
@Component
public class AccountPolicy {
    public enum Role { STUDENT, LECTURER }
    private static final Set<String> TDTU_DOMAINS = Set.of("student.tdtu.edu.vn", "tdtu.edu.vn");
    private final Set<String> students, lecturers, domains;
    private final boolean autoStudentLogin, autoDomainRoles;
    private final String lecturerOverrideEmail;
    public AccountPolicy(AppSettings app) {
        students = csv(app.studentEmails()); lecturers = csv(app.lecturerEmails()); domains = csv(app.allowedWorkspaceDomains());
        autoStudentLogin = app.autoStudentLogin();
        autoDomainRoles = app.autoDomainRoles();
        lecturerOverrideEmail = normalize(app.lecturerOverrideEmail());
        if (!lecturerOverrideEmail.isEmpty()) {
            email(lecturerOverrideEmail);
            if (!TDTU_DOMAINS.contains(domain(lecturerOverrideEmail)))
                throw new IllegalArgumentException("LECTURER_OVERRIDE_EMAIL phải thuộc miền email TDTU.");
        }
        if (!autoDomainRoles && !Collections.disjoint(students, lecturers)) throw new IllegalArgumentException("Một email chỉ được thuộc một vai trò.");
        students.forEach(AccountPolicy::email); lecturers.forEach(AccountPolicy::email);
    }
    public static Set<String> csv(String value) {
        var result = new LinkedHashSet<String>();
        if (value != null) for (var part : value.split(",")) if (!part.isBlank()) result.add(normalize(part));
        return Set.copyOf(result);
    }
    public static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    public static String email(String value) {
        value = normalize(value);
        if (!value.matches("[a-z0-9.!#$%&'*+/=?^_~\\x60{|}=-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,63}"))
            throw new IllegalArgumentException("Địa chỉ email không hợp lệ.");
        return value;
    }
    public Role role(OidcUser user) {
        // Spring Security validates the ID token signature, issuer, audience, expiry and nonce before this policy.
        // Read signed claims directly; UserInfo must not supply claims missing from the ID token.
        if (user == null || user.getIdToken() == null || !Boolean.TRUE.equals(user.getIdToken().getEmailVerified()))
            throw new AccessDeniedException("Email chưa xác minh hoặc sai miền Workspace.");
        String address = normalize(user.getIdToken().getEmail());
        String hd = normalize(user.getIdToken().getClaimAsString("hd"));
        if (!address.equals(normalize(user.getEmail())) || (!domains.isEmpty() && !domains.contains(hd)))
            throw new AccessDeniedException("Email không khớp ID token hoặc sai miền Workspace.");
        Role assigned = roleForEmail(address);
        if (autoDomainRoles) {
            if (assigned != null && TDTU_DOMAINS.contains(hd)) return assigned;
        } else {
            if (assigned != null && (lecturers.contains(address) || students.contains(address))) return assigned;
            if (assigned == Role.STUDENT && TDTU_DOMAINS.contains(hd)) return assigned;
        }
        throw new AccessDeniedException("Tài khoản chưa được cho phép theo cấu hình đăng nhập.");
    }
    public void require(OidcUser user, Role expected) {
        if (role(user) != expected) throw new AccessDeniedException("Vai trò không được phép thực hiện thao tác này.");
    }
    public void requireView(Submission submission, OidcUser user) {
        boolean allowed = role(user) == Role.STUDENT ? submission.studentSub().equals(user.getSubject())
                : submission.ready() && submission.lecturerEmail().equals(normalize(user.getEmail()));
        if (!allowed) throw new AccessDeniedException("Bài nộp không thuộc tài khoản hoặc phân công của bạn.");
    }
    public void requireReview(Submission submission, OidcUser user) {
        require(user, Role.LECTURER); requireView(submission, user);
        if (!submission.ready()) throw new IllegalArgumentException("Bài nộp chưa hoàn tất.");
    }
    public boolean isLecturer(String address) { return roleForEmail(address) == Role.LECTURER; }
    public boolean isStudent(String address) { return roleForEmail(address) == Role.STUDENT; }
    private Role roleForEmail(String address) {
        try { address = email(address); }
        catch (IllegalArgumentException invalid) { return null; }
        if (autoDomainRoles) {
            // The demo exception must precede the exact student-domain match.
            if (address.equals(lecturerOverrideEmail)) return Role.LECTURER;
            return switch (domain(address)) {
                case "tdtu.edu.vn" -> Role.LECTURER;
                case "student.tdtu.edu.vn" -> Role.STUDENT;
                default -> null;
            };
        }
        if (lecturers.contains(address)) return Role.LECTURER;
        return students.contains(address) || automaticStudent(address) ? Role.STUDENT : null;
    }
    private static String domain(String address) { return address.substring(address.lastIndexOf('@') + 1); }
    private boolean automaticStudent(String address) {
        if (!autoStudentLogin) return false;
        int separator = address.indexOf('@');
        return separator > 0 && separator == address.lastIndexOf('@')
            && address.substring(separator + 1).equals("student.tdtu.edu.vn");
    }
    public List<String> lecturers() {
        var suggestions = new TreeSet<>(lecturers);
        if (autoDomainRoles && !lecturerOverrideEmail.isEmpty()) suggestions.add(lecturerOverrideEmail);
        return suggestions.stream().filter(this::isLecturer).toList();
    }
}
