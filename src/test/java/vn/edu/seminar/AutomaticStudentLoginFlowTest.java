package vn.edu.seminar;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AutomaticStudentLoginFlowTest {
    static final FakeGoogle GOOGLE;
    static { try { GOOGLE = new FakeGoogle(); } catch (Exception error) { throw new ExceptionInInitializerError(error); } }
    static final String LECTURER = "524h0133@student.tdtu.edu.vn";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        var properties = GOOGLE.properties();
        properties.put("app.student-emails", ""); properties.put("app.lecturer-emails", "");
        properties.put("app.auto-student-login", false); properties.put("app.auto-domain-roles", true);
        properties.put("app.lecturer-override-email", LECTURER);
        properties.put("app.allowed-workspace-domains", "student.tdtu.edu.vn,tdtu.edu.vn");
        properties.forEach((key, value) -> registry.add(key, () -> value));
    }
    @LocalServerPort int port;
    @Autowired WorkflowStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppSettings settings;
    HttpClient browser;
    @BeforeEach void reset() {
        GOOGLE.reset();
        jdbc.update("DELETE FROM mail_notices"); jdbc.update("DELETE FROM reviews"); jdbc.update("DELETE FROM submissions");
        browser = newBrowser();
    }
    HttpClient newBrowser() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(10)).build(); }
    HttpResponse<String> get(String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    String field(String html, String name) {
        var matcher = Pattern.compile("name=\"" + name + "\" (?:content|value)=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).as("field %s", name).isTrue(); return matcher.group(1);
    }
    HttpResponse<String> post(String path, String body, String type) throws Exception {
        String csrf = field(get("/oauth").body(), "_csrf");
        return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", type).header("X-CSRF-TOKEN", csrf)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String path, String body) throws Exception { return post(path, body, "application/x-www-form-urlencoded"); }
    HttpResponse<String> login(String email, String hd) throws Exception {
        GOOGLE.nextEmail = email; GOOGLE.nextSub = email; GOOGLE.nextHd = hd; browser = newBrowser();
        return get("/oauth2/authorization/google");
    }
    String upload() throws Exception { return upload(LECTURER); }
    String upload(String lecturerEmail) throws Exception {
        String id = field(get("/submit").body(), "attemptId"), boundary = "auto-student-test";
        var body = new StringBuilder();
        for (var entry : Map.of("attemptId", id, "groupName", "Nhóm tự đăng nhập", "topic", "OAuth", "lecturerEmail", lecturerEmail).entrySet())
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"")
                .append(entry.getKey()).append("\"\r\n\r\n").append(entry.getValue()).append("\r\n");
        body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"report.pdf\"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.4\nDemo\r\n--")
            .append(boundary).append("--\r\n");
        assertThat(post("/submissions", body.toString(), "multipart/form-data; boundary=" + boundary).statusCode()).isEqualTo(200);
        return id;
    }
    @ParameterizedTest @ValueSource(strings = {"student.tdtu.edu.vn", "tdtu.edu.vn"})
    void unlistedStudentCanSubmitAndReceiveReviewButCannotAccessAnotherStudentsWork(String hd) throws Exception {
        assertThat(settings.autoDomainRoles()).isTrue(); assertThat(settings.autoStudentLogin()).isFalse();
        assertThat(settings.studentEmails()).isEmpty(); assertThat(settings.lecturerEmails()).isEmpty();
        String email = "new@student.tdtu.edu.vn";
        var page = login(email, hd);
        assertThat(page.uri().getPath()).isEqualTo("/dashboard");
        assertThat(page.body()).contains("STUDENT");
        post("/oauth/connect/submit", "returnTo=%2Fsubmit");
        assertThat(post("/api/picker/check", "{}", "application/json").statusCode()).isEqualTo(200);
        String id = upload();
        assertThat(store.require(id).studentEmail()).isEqualTo(email);
        assertThat(store.notice(id, "SUBMISSION").orElseThrow().sent()).isTrue();
        assertThat(post("/reviews/" + id, "decision=APPROVED").statusCode()).isEqualTo(403);
        login("another@student.tdtu.edu.vn", hd);
        assertThat(get("/submissions/" + id).statusCode()).isEqualTo(403);
        assertThat(get("/dashboard").body()).doesNotContain("Nhóm tự đăng nhập");
        var lecturerPage = login(LECTURER, hd);
        assertThat(lecturerPage.body()).contains("LECTURER");
        assertThat(get("/submit").statusCode()).isEqualTo(403);
        post("/oauth/connect/review", "returnTo=%2Foauth");
        assertThat(post("/reviews/" + id, "decision=APPROVED").statusCode()).isEqualTo(200);
        assertThat(store.review(id).orElseThrow().decision()).isEqualTo("APPROVED");
        assertThat(GOOGLE.sentActors).containsExactly(email, LECTURER);
        var resultMail = new MimeMessage(Session.getInstance(new Properties()),
            new ByteArrayInputStream(Base64.getUrlDecoder().decode(GOOGLE.sentRaw.get(1))));
        assertThat(resultMail.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(email);
    }
    @ParameterizedTest @CsvSource({
        "new@student.tdtu.edu.vn,''", "new@student.tdtu.edu.vn,evil.example",
        "person@gmail.com,tdtu.edu.vn", "teacher@tdtu.edu.vn,''",
        "new@student.tdtu.edu.vn.evil.example,tdtu.edu.vn", "new@evilstudent.tdtu.edu.vn,tdtu.edu.vn",
        "teacher@eviltdtu.edu.vn,tdtu.edu.vn", "teacher@faculty.tdtu.edu.vn,tdtu.edu.vn"
    })
    void disallowedIdentityReturnsToLoginWithoutAnAuthenticatedSession(String email, String hd) throws Exception {
        int uploadsBefore = GOOGLE.uploads.get();
        var page = login(email, hd);
        assertThat(page.uri().getPath()).isEqualTo("/login");
        assertThat(page.body()).contains("class=\"alert error\"");
        assertThat(get("/dashboard").uri().getPath()).isEqualTo("/login");
        assertThat(GOOGLE.sentRaw).isEmpty(); assertThat(GOOGLE.uploads.get()).isEqualTo(uploadsBefore);
    }
    @Test void unlistedLecturerReceivesLecturerAccess() throws Exception {
        var page = login("teacher@tdtu.edu.vn", "tdtu.edu.vn");
        assertThat(page.uri().getPath()).isEqualTo("/dashboard");
        assertThat(page.body()).contains("LECTURER");
        assertThat(get("/oauth").body()).contains("teacher@tdtu.edu.vn");
        assertThat(get("/submit").statusCode()).isEqualTo(403);
        assertThat(post("/oauth/connect/submit", "returnTo=%2Foauth").statusCode()).isEqualTo(403);
        assertThat(post("/oauth/connect/review", "returnTo=%2Foauth").statusCode()).isEqualTo(200);
        assertThat(get("/oauth").body()).contains(Scopes.MAIL).doesNotContain(Scopes.DRIVE);
    }
    @Test void unassignedAutomaticLecturerCannotOpenOrApproveAnotherLecturersSubmission() throws Exception {
        login("new@student.tdtu.edu.vn", "tdtu.edu.vn");
        post("/oauth/connect/submit", "returnTo=%2Fsubmit");
        post("/api/picker/check", "{}", "application/json");
        String id = upload();
        assertThat(login("other@tdtu.edu.vn", "tdtu.edu.vn").body()).contains("LECTURER");
        assertThat(get("/submissions/" + id).statusCode()).isEqualTo(403);
        assertThat(post("/reviews/" + id, "decision=APPROVED").statusCode()).isEqualTo(403);
        assertThat(get("/dashboard").body()).doesNotContain("Nhóm tự đăng nhập");
        assertThat(store.review(id)).isEmpty();
    }
    @Test void studentCanAssignSchoolLecturerBeforeThatLecturersFirstLogin() throws Exception {
        String teacher = "first-login@tdtu.edu.vn";
        login("new@student.tdtu.edu.vn", "tdtu.edu.vn");
        post("/oauth/connect/submit", "returnTo=%2Fsubmit");
        post("/api/picker/check", "{}", "application/json");
        String id = upload(teacher);
        assertThat(store.require(id).lecturerEmail()).isEqualTo(teacher);
        assertThat(GOOGLE.viewers.values()).containsExactly(teacher);
        var mail = new MimeMessage(Session.getInstance(new Properties()),
            new ByteArrayInputStream(Base64.getUrlDecoder().decode(GOOGLE.sentRaw.get(0))));
        assertThat(mail.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(teacher);
        assertThat(login(teacher, "tdtu.edu.vn").body()).contains("LECTURER");
        assertThat(get("/submissions/" + id).statusCode()).isEqualTo(200);
        post("/oauth/connect/review", "returnTo=%2Foauth");
        assertThat(post("/reviews/" + id, "decision=APPROVED").statusCode()).isEqualTo(200);
        assertThat(store.review(id)).isPresent();
    }
    @Test void signedUnverifiedEmailIsRejectedDespiteVerifiedUserInfo() throws Exception {
        GOOGLE.nextEmailVerified = false;
        var page = login("new@student.tdtu.edu.vn", "tdtu.edu.vn");
        assertThat(page.uri().getPath()).isEqualTo("/login");
        assertThat(get("/dashboard").uri().getPath()).isEqualTo("/login");
    }
    @ParameterizedTest @ValueSource(strings = {"", "evil.example"})
    void userInfoCannotSupplyAValidHdForAnInvalidSignedHd(String signedHd) throws Exception {
        GOOGLE.nextUserInfoHd = "tdtu.edu.vn";
        var page = login("new@student.tdtu.edu.vn", signedHd);
        assertThat(page.uri().getPath()).isEqualTo("/login");
        assertThat(get("/dashboard").uri().getPath()).isEqualTo("/login");
    }
    @Test void conflictingUserInfoEmailCannotGrantLecturerAuthority() throws Exception {
        GOOGLE.nextUserInfoEmail = LECTURER;
        var page = login("new@student.tdtu.edu.vn", "tdtu.edu.vn");
        assertThat(page.uri().getPath()).isEqualTo("/dashboard");
        assertThat(page.body()).contains("STUDENT");
        assertThat(get("/oauth").body()).contains("new@student.tdtu.edu.vn").doesNotContain(LECTURER);
        assertThat(get("/submit").statusCode()).isEqualTo(200);
        assertThat(post("/oauth/connect/review", "returnTo=%2Foauth").statusCode()).isEqualTo(403);
    }
    @Test void loginPageExplainsBothSchoolEmailDomains() throws Exception {
        assertThat(get("/login").body()).contains("student.tdtu.edu.vn", "tdtu.edu.vn");
    }
}
