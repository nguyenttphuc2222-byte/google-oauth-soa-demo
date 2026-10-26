package vn.edu.seminar;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.*;
import static org.assertj.core.api.Assertions.*;

class CoreUnitTest {
    AppSettings app(String students, String lecturers, String domains) {
        return new AppSettings("id", "secret", "http://localhost/callback", "https://seminar.example.com",
            "key", "123", "folder_123456", students, lecturers, domains, false, false, "524h0133@student.tdtu.edu.vn", 15, "UTC",
            new AppSettings.Google("http://localhost/auth", "http://localhost/token", "http://localhost/jwks",
                "http://localhost/userinfo", "http://localhost", "http://localhost/revoke", "http://localhost/drive",
                "http://localhost/upload", "http://localhost/gmail"));
    }
    AppSettings app() { return app("student@example.com", "lecturer@example.com", ""); }
    OidcUser user(String email, boolean verified) { return user(email, verified, ""); }
    OidcUser user(String email, boolean verified, String hd) {
        var claims = new HashMap<String, Object>(Map.of("sub", email, "email", email, "email_verified", verified, "name", "Nguyễn An"));
        if (!hd.isBlank()) claims.put("hd", hd);
        return new DefaultOidcUser(Set.of(new SimpleGrantedAuthority("ROLE_LECTURER")),
            new OidcIdToken("test-id", Instant.now(), Instant.now().plusSeconds(3600), claims), "sub");
    }
    Submission submission() {
        return new Submission(UUID.randomUUID().toString(), Instant.now().toString(), "student@example.com", "Nguyễn An",
            "student@example.com", "Nhóm 05", "OAuth 2.0", "lecturer@example.com", "file_123456", "báo-cáo.pdf",
            "https://untrusted.example", 500, "READY", true);
    }
    Review review(Submission s) {
        return new Review(UUID.randomUUID().toString(), s.id(), Instant.now().toString(), "lecturer@example.com",
            "lecturer@example.com", "APPROVED", "Bài tốt. Đã duyệt.");
    }
    MailNotice notice(Submission s, boolean initial) {
        return new MailNotice(UUID.randomUUID().toString(), s.id(), initial ? "SUBMISSION" : "REVIEW",
            initial ? s.studentSub() : "lecturer@example.com", initial ? s.studentEmail() : s.lecturerEmail(),
            initial ? s.lecturerEmail() : s.studentEmail(), initial ? s.studentEmail() : s.lecturerEmail(), "PENDING", "", "");
    }
    MailService mail() { return new MailService(app(), new AccountPolicy(app()), new GoogleHttp(new ObjectMapper(), new AuditTape())); }
    MimeMessage parse(String raw) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
    }
    @Test void roleUsesVerifiedAllowlistRatherThanInjectedAuthority() {
        var policy = new AccountPolicy(app());
        assertThat(policy.role(user("student@example.com", true))).isEqualTo(AccountPolicy.Role.STUDENT);
        assertThat(policy.role(user("lecturer@example.com", true))).isEqualTo(AccountPolicy.Role.LECTURER);
    }
    @Test void unknownUnverifiedAndOverlappingAccountsAreRejected() {
        var policy = new AccountPolicy(app());
        assertThatThrownBy(() -> policy.role(user("student@example.com", false))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.role(user("intruder@example.com", true))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> new AccountPolicy(app("x@example.com", "x@example.com", ""))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bothSchoolDomainsWorkAndStudentAddressCanBeLecturer() {
        var policy = new AccountPolicy(app("a@student.tdtu.edu.vn", "b@student.tdtu.edu.vn,c@tdtu.edu.vn", "student.tdtu.edu.vn,tdtu.edu.vn"));
        assertThat(policy.role(user("b@student.tdtu.edu.vn", true, "student.tdtu.edu.vn"))).isEqualTo(AccountPolicy.Role.LECTURER);
        assertThat(policy.role(user("c@tdtu.edu.vn", true, "tdtu.edu.vn"))).isEqualTo(AccountPolicy.Role.LECTURER);
        assertThatThrownBy(() -> policy.role(user("a@student.tdtu.edu.vn", true, "attacker.example"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.role(user("a@student.tdtu.edu.vn", true))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void scopesAreMinimalAndDoNotInventWritePermissions() {
        assertThat(Scopes.requested(Set.of(), "login")).containsExactlyInAnyOrder("openid", "email", "profile");
        assertThat(Scopes.requested(Set.of(), "review")).containsExactlyInAnyOrder("openid", "email", "profile", Scopes.MAIL);
        assertThat(Scopes.requested(Set.of(), "submit")).containsExactlyInAnyOrder("openid", "email", "profile", Scopes.MAIL, Scopes.DRIVE);
        assertThat(Scopes.includes(Set.of(Scopes.MAIL), Scopes.DRIVE)).isFalse();
    }
    @Test void pdfContentValidatedAndPathSanitized() {
        var checked = new UploadValidator(app()).validate(new MockMultipartFile("file", "../../báo-cáo.pdf", "text/plain", "%PDF-1.4 demo".getBytes()));
        assertThat(checked.name()).isEqualTo("báo-cáo.pdf");
        assertThat(checked.mime()).isEqualTo("application/pdf");
        assertThatThrownBy(() -> new UploadValidator(app()).validate(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not PDF".getBytes()))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void pptxMustContainPresentationStructure() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var name : List.of("[Content_Types].xml", "ppt/presentation.xml")) {
                zip.putNextEntry(new ZipEntry(name)); zip.write("<xml/>".getBytes()); zip.closeEntry();
            }
        }
        assertThat(new UploadValidator(app()).validate(new MockMultipartFile("file", "slide.pptx", "application/zip", output.toByteArray())).mime()).contains("presentationml");
        assertThatThrownBy(() -> new UploadValidator(app()).validate(new MockMultipartFile("file", "slide.pptx", "application/zip", new byte[]{1,2,3}))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void initialEmailComesFromStudentToLecturerAndCopiesStudent() throws Exception {
        var s = submission(); var message = parse(mail().raw(notice(s, true), s, null, user("student@example.com", true)));
        assertThat(message.getFrom()[0].toString()).isEqualTo("student@example.com");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("lecturer@example.com");
        assertThat(message.getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("student@example.com");
        String text = ((Multipart) message.getContent()).getBodyPart(0).getContent().toString();
        assertThat(text).contains("Nguyễn An", "Nhóm 05", "https://seminar.example.com/submissions/" + s.id(), s.safeFileUrl());
    }
    @Test void resultEmailComesFromLecturerToStudentAndCopiesLecturer() throws Exception {
        var s = submission(); var message = parse(mail().raw(notice(s, false), s, review(s), user("lecturer@example.com", true)));
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("student@example.com");
        assertThat(message.getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("lecturer@example.com");
        assertThat(((Multipart) message.getContent()).getBodyPart(0).getContent().toString()).contains("Bài tốt. Đã duyệt.", "Hợp lệ");
    }
    @Test void emailRequiresCorrectActorAndRejectsHeaderInjection() {
        var s = submission();
        assertThatThrownBy(() -> mail().raw(notice(s, true), s, null, user("lecturer@example.com", true))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> AccountPolicy.email("student@example.com\r\nBcc: intruder@example.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubmissionService.text("Nhóm\r\nBcc:x", "Nhóm", 100)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void fileLinkRebuiltFromGoogleIdAndBaseLinkComesFromConfiguration() {
        var s = submission(); assertThat(s.safeFileUrl()).isEqualTo("https://drive.google.com/file/d/file_123456/view");
        assertThat(app().submissionUrl(s.id())).startsWith("https://seminar.example.com/submissions/");
    }

    @Test void omittedCcProducesOnlyThePrimaryRecipientForBothKinds() throws Exception {
        var submission = submission();
        for (boolean initial : new boolean[]{true, false}) {
            var old = notice(submission, initial);
            var withoutCc = new MailNotice(old.id(), old.submissionId(), old.kind(), old.senderSub(), old.senderEmail(), old.toEmail(), "", old.status(), "", "");
            var message = parse(mail().raw(withoutCc, submission, initial ? null : review(submission), user(old.senderEmail(), true)));
            assertThat(message.getRecipients(Message.RecipientType.CC)).isNull();
            assertThat(message.getAllRecipients()).hasSize(1);
            assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(old.toEmail());
        }
    }
    @Test void ccCannotBeChangedToAnotherAddress() {
        var submission = submission(); var old = notice(submission, true);
        var forged = new MailNotice(old.id(), old.submissionId(), old.kind(), old.senderSub(), old.senderEmail(), old.toEmail(), "lecturer@example.com", old.status(), "", "");
        assertThatThrownBy(() -> mail().raw(forged, submission, null, user("student@example.com", true))).isInstanceOf(IllegalArgumentException.class);
    }
}
