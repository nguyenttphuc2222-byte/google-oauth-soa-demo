package vn.edu.seminar;

import java.time.Instant;
import java.util.List;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Repository;

@Repository
public class LecturerDirectory {
    private final JdbcTemplate jdbc;
    private final AccountPolicy policy;

    public LecturerDirectory(JdbcTemplate jdbc, AccountPolicy policy) {
        this.jdbc = jdbc; this.policy = policy;
    }

    public void remember(OidcUser user) {
        if (policy.role(user) != AccountPolicy.Role.LECTURER) return;
        jdbc.update("MERGE INTO lecturer_directory(email, last_login_at) KEY(email) VALUES (?, ?)",
            AccountPolicy.email(user.getEmail()), Instant.now().toString());
    }

    public List<String> suggestions() {
        var emails = new TreeSet<>(policy.lecturers());
        emails.addAll(jdbc.queryForList("SELECT email FROM lecturer_directory", String.class));
        return emails.stream().filter(policy::isLecturer).toList();
    }
}
