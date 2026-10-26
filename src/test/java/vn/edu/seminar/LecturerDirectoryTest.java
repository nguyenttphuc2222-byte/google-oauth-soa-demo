package vn.edu.seminar;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LecturerDirectoryTest {
    @TempDir Path directory;

    JdbcTemplate database(String name) {
        var source = new DriverManagerDataSource("jdbc:h2:file:" + directory.resolve(name) + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        return new JdbcTemplate(source);
    }

    @Test void observedLecturerSuggestionsSurviveRestartAndMergeWithoutDuplicates() {
        var policy = mock(AccountPolicy.class);
        when(policy.role(any(OidcUser.class))).thenReturn(AccountPolicy.Role.LECTURER);
        when(policy.lecturers()).thenReturn(List.of("configured@tdtu.edu.vn", "observed@tdtu.edu.vn"));
        var eligible = Set.of("configured@tdtu.edu.vn", "observed@tdtu.edu.vn", "new@tdtu.edu.vn");
        when(policy.isLecturer(anyString())).thenAnswer(invocation -> eligible.contains(invocation.getArgument(0)));
        var helper = new CoreUnitTest();
        var jdbc = database("directory");
        var first = new LecturerDirectory(jdbc, policy);
        first.remember(helper.user("Observed@tdtu.edu.vn", true));
        first.remember(helper.user("observed@tdtu.edu.vn", true));
        first.remember(helper.user("new@tdtu.edu.vn", true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lecturer_directory", Integer.class)).isEqualTo(2);
        jdbc.execute("SHUTDOWN");

        var reopened = database("directory");
        assertThat(new LecturerDirectory(reopened, policy).suggestions())
            .containsExactly("configured@tdtu.edu.vn", "new@tdtu.edu.vn", "observed@tdtu.edu.vn");
        reopened.execute("SHUTDOWN");
    }

    @Test void onlyVerifiedLecturerIdentitiesAreStored() {
        var helper = new CoreUnitTest();
        var policy = new AccountPolicy(helper.app());
        var jdbc = database("identities");
        var lecturers = new LecturerDirectory(jdbc, policy);
        lecturers.remember(helper.user("student@example.com", true));
        assertThatThrownBy(() -> lecturers.remember(helper.user("lecturer@example.com", false)))
            .isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForList("SELECT email FROM lecturer_directory", String.class)).isEmpty();
        lecturers.remember(helper.user("lecturer@example.com", true));
        assertThat(jdbc.queryForList("SELECT email FROM lecturer_directory", String.class))
            .containsExactly("lecturer@example.com");
        jdbc.execute("SHUTDOWN");
    }

    @Test void suggestionsExcludePreviouslyAuthenticatedEmailsNoLongerEligible() {
        var policy = mock(AccountPolicy.class);
        when(policy.role(any(OidcUser.class))).thenReturn(AccountPolicy.Role.LECTURER);
        when(policy.lecturers()).thenReturn(List.of());
        when(policy.isLecturer("old@tdtu.edu.vn")).thenReturn(true);
        var jdbc = database("changed-policy");
        var lecturers = new LecturerDirectory(jdbc, policy);
        lecturers.remember(new CoreUnitTest().user("old@tdtu.edu.vn", true));
        assertThat(lecturers.suggestions()).containsExactly("old@tdtu.edu.vn");
        when(policy.isLecturer("old@tdtu.edu.vn")).thenReturn(false);
        assertThat(lecturers.suggestions()).isEmpty();
        jdbc.execute("SHUTDOWN");
    }
}
