package vn.edu.seminar;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
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
class OAuthFlowTest {
    static final FakeGoogle GOOGLE;
    static { try { GOOGLE = new FakeGoogle(); } catch (Exception exception) { throw new ExceptionInInitializerError(exception); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { GOOGLE.properties().forEach((key, value) -> registry.add(key, () -> value)); }
    @LocalServerPort int port;
    @Autowired WorkflowStore store;
    @Autowired JdbcTemplate jdbc;
    HttpClient browser;
    HttpClient newBrowser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(10)).build();
    }
    @BeforeEach void before() {
        GOOGLE.reset(); browser = newBrowser();
        jdbc.update("DELETE FROM mail_notices"); jdbc.update("DELETE FROM reviews"); jdbc.update("DELETE FROM submissions");
    }
    HttpResponse<String> get(String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    String field(String html, String name) {
        var matcher = Pattern.compile("name=\"" + name + "\" (?:content|value)=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).as("field %s", name).isTrue(); return matcher.group(1);
    }
    HttpResponse<String> post(String path, String body) throws Exception { return send(path, body, "application/x-www-form-urlencoded"); }
    HttpResponse<String> send(String path, String body, String type) throws Exception {
        return send(path, body.getBytes(StandardCharsets.UTF_8), type);
    }
    HttpResponse<String> send(String path, byte[] body, String type) throws Exception {
        String csrf = field(get("/oauth").body(), "_csrf");
        return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", type).header("X-CSRF-TOKEN", csrf)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    void student() throws Exception {
        get("/oauth2/authorization/google"); post("/oauth/connect/submit", "returnTo=%2Foauth");
        assertThat(send("/api/picker/check", "{}", "application/json").statusCode()).isEqualTo(200);
    }
    void lecturer() throws Exception { actor("lecturer@example.com", "lecturer-sub"); }
    void actor(String email, String sub) throws Exception {
        GOOGLE.nextEmail = email; GOOGLE.nextSub = sub; browser = newBrowser(); get("/oauth2/authorization/google");
    }
    String submit() throws Exception { return submit(false); }
    String submit(boolean ccSelf) throws Exception {
        String id = field(get("/submit").body(), "attemptId");
        assertThat(upload(id, ccSelf).statusCode()).isEqualTo(200); return id;
    }
    HttpResponse<String> upload(String id) throws Exception { return upload(id, false); }
    HttpResponse<String> upload(String id, boolean ccSelf) throws Exception {
        return upload(id, ccSelf, "bao-cao.pdf", "application/pdf", "%PDF-1.4\nDemo".getBytes(StandardCharsets.UTF_8));
    }
    HttpResponse<String> upload(String id, boolean ccSelf, String filename, String mime, byte[] file) throws Exception {
        String boundary = "test-boundary"; var body = new StringBuilder();
        var fields = new HashMap<>(Map.of("attemptId", id, "groupName", "Nhóm 05", "topic", "OAuth 2.0", "lecturerEmail", "lecturer@example.com"));
        if (ccSelf) fields.put("ccSelf", "true");
        for (var field : fields.entrySet()) body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"")
            .append(field.getKey()).append("\"\r\n\r\n").append(field.getValue()).append("\r\n");
        body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"")
            .append(filename).append("\"\r\nContent-Type: ").append(mime).append("\r\n\r\n");
        var output = new ByteArrayOutputStream(); output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        output.write(file); output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send("/submissions", output.toByteArray(), "multipart/form-data; boundary=" + boundary);
    }
    void grantReview(String id) throws Exception { post("/oauth/connect/review", "returnTo=" + URLEncoder.encode("/submissions/" + id, StandardCharsets.UTF_8)); }
    MimeMessage message(int index) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(Base64.getUrlDecoder().decode(GOOGLE.sentRaw.get(index))));
    }

    @Test void completeFlowSendsBothEmailsUsingEachActorsTokenAndDoesNotRepeat() throws Exception {
        student(); String id = submit(true);
        assertThat(store.require(id).ready()).isTrue(); assertThat(store.review(id)).isEmpty();
        assertThat(GOOGLE.sentActors).containsExactly("student@example.com");
        assertThat(message(0).getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("lecturer@example.com");
        assertThat(message(0).getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("student@example.com");
        assertThat(GOOGLE.viewers.values()).contains("lecturer@example.com");
        int uploads = GOOGLE.uploads.get(); upload(id);
        assertThat(GOOGLE.uploads.get()).isEqualTo(uploads); assertThat(GOOGLE.sentRaw).hasSize(1);
        lecturer(); grantReview(id);
        assertThat(post("/reviews/" + id, "decision=APPROVED&comment=Bai+tot&ccSelf=true").statusCode()).isEqualTo(200);
        assertThat(store.review(id).orElseThrow().decision()).isEqualTo("APPROVED");
        assertThat(GOOGLE.sentActors).containsExactly("student@example.com", "lecturer@example.com");
        assertThat(message(1).getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("student@example.com");
        assertThat(message(1).getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("lecturer@example.com");
        post("/reviews/" + id, "decision=REJECTED&comment=changed");
        assertThat(store.review(id).orElseThrow().decision()).isEqualTo("APPROVED");
        assertThat(GOOGLE.sentRaw).hasSize(2); assertThat(store.notices(id)).hasSize(2);
    }
    @Test void emailDeepLinkSurvivesLoginAndDoesNotReviewOnGet() throws Exception {
        student(); String id = submit(); browser = newBrowser();
        assertThat(get("/submissions/" + id).uri().getPath()).isEqualTo("/login");
        GOOGLE.nextEmail = "lecturer@example.com"; GOOGLE.nextSub = "lecturer-sub";
        var page = get("/oauth2/authorization/google");
        assertThat(page.uri().getPath()).isEqualTo("/submissions/" + id);
        assertThat(page.body()).contains("Nhận xét và xét duyệt"); assertThat(store.review(id)).isEmpty();
        assertThat(GOOGLE.sentRaw).hasSize(1);
    }
    @Test void actualPkceExchangeRefreshAndRevocationWithoutExposingSecrets() throws Exception {
        int before = GOOGLE.exchanges.get(); get("/oauth2/authorization/google");
        assertThat(GOOGLE.exchanges.get()).isEqualTo(before + 1);
        assertThat(get("/oauth").body()).contains("PKCE · S256").doesNotContain("mock-access-", "mock-refresh-", "test-secret");
        post("/oauth/connect/submit", "returnTo=%2Foauth");
        int count = GOOGLE.refreshes.get(); post("/oauth/refresh", ""); assertThat(GOOGLE.refreshes.get()).isEqualTo(count + 1);
        count = GOOGLE.revocations.get(); post("/oauth/revoke", ""); assertThat(GOOGLE.revocations.get()).isEqualTo(count + 1);
        assertThat(get("/oauth").body()).contains("Chưa có token");
    }
    @Test void invalidStateNeverExchangesCode() throws Exception {
        int count = GOOGLE.exchanges.get(); assertThat(get("/login/oauth2/code/google?code=bad&state=bad").body()).contains("Không hoàn tất được OAuth");
        assertThat(GOOGLE.exchanges.get()).isEqualTo(count);
    }
    @Test void wrongNonceCannotAuthenticate() throws Exception {
        GOOGLE.wrongNonce = true; assertThat(get("/oauth2/authorization/google").uri().getPath()).isEqualTo("/login");
    }
    @Test void accountChangeDuringConsentPreservesOriginalIdentity() throws Exception {
        get("/oauth2/authorization/google"); GOOGLE.nextEmail = "lecturer@example.com"; GOOGLE.nextSub = "lecturer-sub";
        var page = post("/oauth/connect/submit", "returnTo=%2Foauth");
        assertThat(page.body()).contains("Tài khoản cấp quyền khác", "STUDENT", "student@example.com");
    }
    @Test void partialGrantCannotUploadOrSendEmail() throws Exception {
        get("/oauth2/authorization/google"); GOOGLE.identityOnly = true;
        post("/oauth/connect/submit", "returnTo=%2Foauth");
        assertThat(get("/submit").body()).contains("Kết nối Drive &amp; Gmail");
        int count = GOOGLE.uploads.get(); upload(field(get("/submit").body(), "attemptId"));
        assertThat(GOOGLE.uploads.get()).isEqualTo(count); assertThat(GOOGLE.sentRaw).isEmpty();
    }
    @Test void studentCannotForgeReviewOrRequestLecturerScopes() throws Exception {
        get("/oauth2/authorization/google");
        assertThat(post("/oauth/connect/review", "returnTo=%2Foauth").statusCode()).isEqualTo(403);
        assertThat(post("/reviews/11111111-1111-4111-8111-111111111111", "decision=APPROVED").statusCode()).isEqualTo(403);
    }
    @Test void lecturerNeedsNoDriveScopeAndCannotOpenUploadPage() throws Exception {
        lecturer(); assertThat(get("/submit").statusCode()).isEqualTo(403);
        post("/oauth/connect/review", "returnTo=%2Foauth");
        assertThat(get("/oauth").body()).contains(Scopes.MAIL).doesNotContain(Scopes.DRIVE);
    }
    @Test void csrfIsRequired() throws Exception {
        get("/oauth2/authorization/google");
        var response = browser.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth/refresh"))
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
    }
    @Test void otherStudentCannotViewSubmissionOrSendItsNotice() throws Exception {
        student(); String id = submit(); String notice = store.notice(id, "SUBMISSION").orElseThrow().id();
        actor("otherstudent@example.com", "other-student-sub");
        assertThat(get("/submissions/" + id).statusCode()).isEqualTo(403);
        assertThat(get("/dashboard").body()).doesNotContain("Nhóm 05");
        assertThat(post("/notifications/" + notice + "/retry", "").statusCode()).isEqualTo(403);
    }
    @Test void unassignedLecturerCannotViewOrApproveForwardedLink() throws Exception {
        student(); String id = submit(); actor("otherlecturer@example.com", "other-lecturer-sub");
        assertThat(get("/submissions/" + id).statusCode()).isEqualTo(403);
        assertThat(post("/reviews/" + id, "decision=APPROVED").statusCode()).isEqualTo(403);
        assertThat(store.review(id)).isEmpty();
    }
    @Test void failedSubmissionEmailCanRetryWithoutUploadingAgain() throws Exception {
        student(); GOOGLE.mailError = 403; String id = submit();
        var notice = store.notice(id, "SUBMISSION").orElseThrow(); assertThat(notice.status()).isEqualTo("FAILED");
        assertThat(store.require(id).ready()).isTrue(); int count = GOOGLE.uploads.get(); GOOGLE.mailError = 0;
        assertThat(post("/notifications/" + notice.id() + "/retry", "").statusCode()).isEqualTo(200);
        assertThat(store.noticeById(notice.id()).sent()).isTrue(); assertThat(GOOGLE.uploads.get()).isEqualTo(count);
        assertThat(store.noticeById(notice.id()).ccEmail()).isEmpty();
        assertThat(message(0).getRecipients(Message.RecipientType.CC)).isNull();
    }
    @Test void sharingFailureLeavesRecoverableDraftAndNoMail() throws Exception {
        student(); GOOGLE.failShare = true; String id = submit();
        assertThat(store.require(id).uploaded()).isTrue(); assertThat(store.require(id).ready()).isFalse();
        assertThat(GOOGLE.sentRaw).isEmpty(); int count = GOOGLE.uploads.get(); GOOGLE.failShare = false;
        post("/submissions/retry/" + id, "");
        assertThat(store.require(id).ready()).isTrue(); assertThat(GOOGLE.uploads.get()).isEqualTo(count); assertThat(GOOGLE.sentRaw).hasSize(1);
    }
    @Test void ambiguousDriveUploadIsRecoveredUsingSubmissionId() throws Exception {
        student(); GOOGLE.failAfterUpload = true; int count = GOOGLE.uploads.get(); String id = submit();
        assertThat(store.require(id).uploaded()).isFalse(); post("/submissions/retry/" + id, "");
        assertThat(store.require(id).ready()).isTrue(); assertThat(GOOGLE.uploads.get()).isEqualTo(count + 1); assertThat(GOOGLE.sentRaw).hasSize(1);
    }
    @Test void uncertainReviewEmailRequiresAcknowledgementAndKeepsDecision() throws Exception {
        student(); String id = submit(); lecturer(); grantReview(id); GOOGLE.mailError = 503;
        post("/reviews/" + id, "decision=REJECTED&comment=Can+bo+sung");
        var notice = store.notice(id, "REVIEW").orElseThrow(); assertThat(notice.status()).isEqualTo("UNKNOWN");
        assertThat(store.review(id).orElseThrow().decision()).isEqualTo("REJECTED");
        GOOGLE.mailError = 0;
        assertThat(post("/notifications/" + notice.id() + "/retry", "").statusCode()).isEqualTo(400);
        post("/notifications/" + notice.id() + "/retry", "acknowledgeUnknown=true");
        assertThat(store.noticeById(notice.id()).sent()).isTrue(); assertThat(GOOGLE.sentRaw).hasSize(2);
    }
    @Test void missingGmailConsentPreservesCommentWithoutSavingDecision() throws Exception {
        student(); String id = submit(); lecturer();
        post("/reviews/" + id, "decision=REJECTED&comment=Giu+nhan+xet&ccSelf=true");
        assertThat(store.review(id)).isEmpty();
        String form = get("/submissions/" + id).body();
        assertThat(form).contains("Giu nhan xet");
        assertThat(Pattern.compile("<input[^>]*id=\"review-cc-self\"[^>]*checked").matcher(form).find()).isTrue();
        grantReview(id); post("/reviews/" + id, "decision=REJECTED&comment=Giu+nhan+xet&ccSelf=true");
        assertThat(store.review(id)).isPresent(); assertThat(GOOGLE.sentRaw).hasSize(2);
    }
    @Test void dashboardAndDetailsRemainReadableAfterRevokingGoogleApiGrant() throws Exception {
        student(); String id = submit(); post("/oauth/revoke", "");
        assertThat(get("/dashboard").body()).contains("Nhóm 05");
        assertThat(get("/submissions/" + id).statusCode()).isEqualTo(200);
    }
    @Test void pickerCannotAuthorizeDifferentFolder() throws Exception {
        student(); assertThat(send("/api/picker/folder", "{\"id\":\"another_folder_123\"}", "application/json").statusCode()).isEqualTo(400);
    }
    @Test void staticAssetsArePublicAndOldSetupPageIsGone() throws Exception {
        assertThat(get("/static/app.css").statusCode()).isEqualTo(200);
        assertThat(get("/static/app.js").body()).contains("PickerBuilder");
        lecturer(); assertThat(get("/setup").statusCode()).isEqualTo(404);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void studentAndLecturerChooseCcIndependently(boolean studentCc, boolean lecturerCc) throws Exception {
        student(); String id = submit(studentCc);
        lecturer(); grantReview(id);
        post("/reviews/" + id, "decision=APPROVED" + (lecturerCc ? "&ccSelf=true" : ""));
        assertThat(GOOGLE.sentRaw).hasSize(2);
        assertThat(message(0).getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("lecturer@example.com");
        assertThat(message(1).getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("student@example.com");
        assertThat(store.notice(id, "SUBMISSION").orElseThrow().ccEmail()).isEqualTo(studentCc ? "student@example.com" : "");
        assertThat(store.notice(id, "REVIEW").orElseThrow().ccEmail()).isEqualTo(lecturerCc ? "lecturer@example.com" : "");
        if (studentCc) assertThat(message(0).getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("student@example.com");
        else assertThat(message(0).getRecipients(Message.RecipientType.CC)).isNull();
        if (lecturerCc) assertThat(message(1).getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("lecturer@example.com");
        else assertThat(message(1).getRecipients(Message.RecipientType.CC)).isNull();
    }
    @Test void selectedCcSurvivesIncompleteUploadAndNewLogin() throws Exception {
        student(); GOOGLE.failShare = true; String id = submit(true);
        assertThat(store.require(id).ccSelf()).isTrue();
        actor("student@example.com", "student-sub");
        post("/oauth/connect/submit", "returnTo=%2Foauth"); send("/api/picker/check", "{}", "application/json");
        String form = get("/submit?attemptId=" + id).body();
        assertThat(Pattern.compile("<input[^>]*id=\"submission-cc-self\"[^>]*checked").matcher(form).find()).isTrue();
        GOOGLE.failShare = false; post("/submissions/retry/" + id, "");
        assertThat(store.notice(id, "SUBMISSION").orElseThrow().ccEmail()).isEqualTo("student@example.com");
        assertThat(message(0).getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("student@example.com");
    }
    @Test void mailRetryKeepsSavedCcRatherThanAcceptingNewRecipientParameters() throws Exception {
        student(); GOOGLE.mailError = 403; String id = submit(true);
        String noticeId = store.notice(id, "SUBMISSION").orElseThrow().id(); GOOGLE.mailError = 0;
        post("/notifications/" + noticeId + "/retry", "ccSelf=false&ccEmail=intruder%40example.com");
        assertThat(message(0).getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("student@example.com");
        assertThat(message(0).getAllRecipients()).hasSize(2);
    }

    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void failedEmailsKeepBothCcChoicesAcrossLogoutAndRetry(boolean studentCc, boolean lecturerCc) throws Exception {
        student(); GOOGLE.mailError = 403; String id = submit(studentCc);
        String initialNotice = store.notice(id, "SUBMISSION").orElseThrow().id();
        // A fresh login loses the form/session, but saved recipients must remain authoritative.
        actor("student@example.com", "student-sub");
        post("/oauth/connect/submit", "returnTo=%2Foauth"); GOOGLE.mailError = 0;
        post("/notifications/" + initialNotice + "/retry", "ccSelf=" + !studentCc);
        lecturer(); grantReview(id); GOOGLE.mailError = 403;
        post("/reviews/" + id, "decision=REJECTED&comment=Bo+sung&ccSelf=" + lecturerCc);
        String reviewNotice = store.notice(id, "REVIEW").orElseThrow().id();
        assertThat(store.review(id).orElseThrow().comment()).isEqualTo("Bo sung");
        actor("lecturer@example.com", "lecturer-sub"); grantReview(id); GOOGLE.mailError = 0;
        post("/notifications/" + reviewNotice + "/retry", "ccSelf=" + !lecturerCc + "&ccEmail=intruder%40example.com");
        assertThat(GOOGLE.sentRaw).hasSize(2);
        assertThat(message(0).getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("lecturer@example.com");
        assertThat(message(1).getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("student@example.com");
        assertThat(message(0).getAllRecipients()).hasSize(studentCc ? 2 : 1);
        assertThat(message(1).getAllRecipients()).hasSize(lecturerCc ? 2 : 1);
        if (studentCc) assertThat(message(0).getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("student@example.com");
        else assertThat(message(0).getRecipients(Message.RecipientType.CC)).isNull();
        if (lecturerCc) assertThat(message(1).getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("lecturer@example.com");
        else assertThat(message(1).getRecipients(Message.RecipientType.CC)).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"report.pdf", "report.doc", "report.docx", "report.zip", "slides.ppt", "slides.pptx"})
    void uploadedFileHasCorrectMimeAndViewOrDownloadActionForAssignedLecturer(String name) throws Exception {
        byte[] file;
        if (name.endsWith(".zip")) file = UploadAndFileActionTest.archive("source/Main.java", "report.pdf");
        else if (name.endsWith(".docx")) file = UploadAndFileActionTest.archive("[Content_Types].xml", "word/document.xml");
        else if (name.endsWith(".pptx")) file = UploadAndFileActionTest.archive("[Content_Types].xml", "ppt/presentation.xml");
        else if (name.endsWith(".pdf")) file = "%PDF-1.4\nSeminar".getBytes(StandardCharsets.UTF_8);
        else {
            file = new byte[512];
            System.arraycopy(new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1}, 0, file, 0, 8);
        }
        student(); String id = field(get("/submit").body(), "attemptId");
        // Generic browser MIME must not override the MIME established by validation.
        assertThat(upload(id, false, name, "application/octet-stream", file).statusCode()).isEqualTo(200);
        var submission = store.require(id);
        assertThat(submission.fileName()).isEqualTo(name);
        String expectedMime = Map.of("report.pdf", "application/pdf", "report.doc", "application/msword",
            "report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "report.zip", "application/zip",
            "slides.ppt", "application/vnd.ms-powerpoint", "slides.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation").get(name);
        assertThat(GOOGLE.files.get(id).get("mimeType")).isEqualTo(expectedMime);
        assertThat(submission.safeDownloadUrl()).contains("export=download");
        assertThat(GOOGLE.viewers.get(submission.fileId())).isEqualTo("lecturer@example.com");
        String mailText = ((Multipart) message(0).getContent()).getBodyPart(0).getContent().toString();
        assertThat(mailText).contains(name, submission.fileActionUrl());
        lecturer(); String page = get("/submissions/" + id).body();
        assertThat(page).contains(submission.fileActionLabel(), submission.fileActionUrl().replace("&", "&amp;"));
        if (name.endsWith(".zip")) assertThat(submission.fileActionUrl()).contains("export=download");
        else assertThat(submission.fileActionUrl()).endsWith("/view");
        assertThat(get("/oauth").body()).doesNotContain(Scopes.DRIVE);
    }

    @Test void invalidZipIsRejectedBeforeDriveUploadOrEmail() throws Exception {
        student(); String id = field(get("/submit").body(), "attemptId"); int uploads = GOOGLE.uploads.get();
        assertThat(upload(id, false, "fake.zip", "application/zip", "not a zip".getBytes()).statusCode()).isEqualTo(400);
        assertThat(store.find(id)).isEmpty(); assertThat(GOOGLE.uploads.get()).isEqualTo(uploads);
        assertThat(GOOGLE.sentRaw).isEmpty();
    }
    @Test void recoveredZipKeepsDownloadLinkWithoutUploadingAgain() throws Exception {
        student(); String id = field(get("/submit").body(), "attemptId"); int uploads = GOOGLE.uploads.get();
        GOOGLE.failAfterUpload = true;
        upload(id, true, "seminar.zip", "application/zip", UploadAndFileActionTest.archive("report.pdf"));
        post("/submissions/retry/" + id, "");
        var submission = store.require(id);
        assertThat(submission.ready()).isTrue(); assertThat(submission.zip()).isTrue();
        assertThat(submission.fileActionUrl()).contains("export=download");
        assertThat(GOOGLE.uploads.get()).isEqualTo(uploads + 1);
        assertThat(message(0).getRecipients(Message.RecipientType.CC)[0].toString()).isEqualTo("student@example.com");
    }
    @Test void zipWithoutDownloadLinkStillOffersDriveFallback() throws Exception {
        student(); GOOGLE.omitDownloadLink = true;
        String id = field(get("/submit").body(), "attemptId");
        upload(id, false, "seminar.zip", "application/zip", UploadAndFileActionTest.archive("report.pdf"));
        lecturer();
        assertThat(get("/submissions/" + id).body()).contains("Mở Drive để tải ZIP", store.require(id).safeFileUrl());
    }
}
