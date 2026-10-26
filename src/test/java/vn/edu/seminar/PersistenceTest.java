package vn.edu.seminar;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.*;
import static org.assertj.core.api.Assertions.*;

class PersistenceTest {
    @TempDir Path directory;
    OidcUser user(String email) {
        return new DefaultOidcUser(Set.of(), new OidcIdToken("test", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("sub", email, "email", email, "email_verified", true, "name", "Nguyễn An")), "sub");
    }
    @Test void diskDatabaseKeepsSubmissionDecisionAndMailStateAcrossReopen() {
        var settings = new CoreUnitTest().app(); var policy = new AccountPolicy(settings);
        String url = "jdbc:h2:file:" + directory.resolve("seminar") + ";DB_CLOSE_ON_EXIT=FALSE";
        var source = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        var firstJdbc = new JdbcTemplate(source); var first = new WorkflowStore(firstJdbc, policy);
        String id = UUID.randomUUID().toString();
        first.createDraft(id, user("student@example.com"), "Nhóm bền vững", "OAuth", "lecturer@example.com", true);
        first.setFile(id, new DriveClient.Uploaded("file_123456", "bao-cao.pdf", 2048));
        first.completeSubmission(id);
        first.recordReview(first.require(id), user("lecturer@example.com"), "REJECTED", "Bổ sung minh chứng.", false);
        var initial = first.notice(id, "SUBMISSION").orElseThrow();
        var result = first.notice(id, "REVIEW").orElseThrow();
        first.mailState(initial.id(), "SENT", "gmail_123", "");
        first.mailState(result.id(), "SENDING", "", "");
        firstJdbc.execute("SHUTDOWN");
        var secondSource = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(secondSource);
        var secondJdbc = new JdbcTemplate(secondSource); var second = new WorkflowStore(secondJdbc, policy);
        second.recoverInterruptedMail();
        assertThat(second.require(id).ready()).isTrue();
        assertThat(second.require(id).ccSelf()).isTrue();
        assertThat(second.noticeById(initial.id()).ccEmail()).isEqualTo("student@example.com");
        assertThat(second.noticeById(result.id()).ccEmail()).isEmpty();
        assertThat(second.require(id).fileId()).isEqualTo("file_123456");
        assertThat(second.review(id).orElseThrow().comment()).isEqualTo("Bổ sung minh chứng.");
        assertThat(second.noticeById(initial.id()).status()).isEqualTo("SENT");
        assertThat(second.noticeById(result.id()).status()).isEqualTo("UNKNOWN");
        assertThat(second.visible(user("student@example.com"))).hasSize(1);
        secondJdbc.execute("SHUTDOWN");
    }

    @Test void oldDatabaseGetsCcColumnWithoutChangingExistingMailRecipients() {
        var policy = new AccountPolicy(new CoreUnitTest().app());
        var source = new DriverManagerDataSource("jdbc:h2:file:" + directory.resolve("upgrade") + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
        var schema = new ResourceDatabasePopulator(new ClassPathResource("schema.sql")); schema.execute(source);
        var jdbc = new JdbcTemplate(source); var store = new WorkflowStore(jdbc, policy);
        String id = UUID.randomUUID().toString();
        store.createDraft(id, user("student@example.com"), "Nhóm cũ", "OAuth", "lecturer@example.com", true);
        store.setFile(id, new DriveClient.Uploaded("file_123456", "bao-cao.pdf", 100)); store.completeSubmission(id);
        jdbc.execute("ALTER TABLE submissions DROP COLUMN cc_self");
        jdbc.execute("ALTER TABLE submissions DROP COLUMN file_download_url");
        schema.execute(source);
        assertThat(store.require(id).groupName()).isEqualTo("Nhóm cũ");
        assertThat(store.notice(id, "SUBMISSION").orElseThrow().ccEmail()).isEqualTo("student@example.com");
        assertThat(store.require(id).ccSelf()).isFalse();
        assertThat(store.require(id).safeDownloadUrl()).isEmpty();
        assertThat(store.require(id).fileActionUrl()).endsWith("/view");
        jdbc.execute("SHUTDOWN");
    }

    @Test void zipDownloadLinkAndCcRemainAvailableAfterDatabaseReopens() {
        var policy = new AccountPolicy(new CoreUnitTest().app());
        String url = "jdbc:h2:file:" + directory.resolve("zip") + ";DB_CLOSE_ON_EXIT=FALSE";
        var source = new DriverManagerDataSource(url, "sa", "");
        var schema = new ResourceDatabasePopulator(new ClassPathResource("schema.sql")); schema.execute(source);
        var jdbc = new JdbcTemplate(source); var store = new WorkflowStore(jdbc, policy);
        String id = UUID.randomUUID().toString(), link = "https://drive.google.com/uc?export=download&id=file_zip123";
        store.createDraft(id, user("student@example.com"), "Nhóm ZIP", "OAuth", "lecturer@example.com", true);
        store.setFile(id, new DriveClient.Uploaded("file_zip123", "seminar.zip", 1024, link));
        store.completeSubmission(id); jdbc.execute("SHUTDOWN");
        var reopenedSource = new DriverManagerDataSource(url, "sa", ""); schema.execute(reopenedSource);
        var reopenedJdbc = new JdbcTemplate(reopenedSource); var reopened = new WorkflowStore(reopenedJdbc, policy);
        assertThat(reopened.require(id).fileActionUrl()).isEqualTo(link);
        assertThat(reopened.require(id).fileActionLabel()).contains("Tải file ZIP");
        assertThat(reopened.notice(id, "SUBMISSION").orElseThrow().ccEmail()).isEqualTo("student@example.com");
        reopenedJdbc.execute("SHUTDOWN");
    }
}
