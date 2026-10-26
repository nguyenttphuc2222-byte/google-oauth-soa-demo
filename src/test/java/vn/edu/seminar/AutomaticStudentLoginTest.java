package vn.edu.seminar;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import static org.assertj.core.api.Assertions.*;

class AutomaticStudentLoginTest {
    static final String DEMO_LECTURER = "524h0133@student.tdtu.edu.vn";
    static final String SCHOOL_DOMAINS = "student.tdtu.edu.vn,tdtu.edu.vn";
    final CoreUnitTest helper = new CoreUnitTest();

    AccountPolicy policy(boolean autoDomainRoles, boolean autoStudentLogin, String domains,
            String students, String lecturers, String override) {
        var app = helper.app();
        return new AccountPolicy(new AppSettings(app.clientId(), app.clientSecret(), app.redirectUri(), app.baseUrl(),
            app.apiKey(), app.projectNumber(), app.folderId(), students, lecturers, domains,
            autoStudentLogin, autoDomainRoles, override, app.maxUploadMb(), app.displayTimezone(), app.google()));
    }
    AccountPolicy policy() { return policy(true, false, SCHOOL_DOMAINS, "", "", DEMO_LECTURER); }

    @ParameterizedTest @CsvSource({
        "new@student.tdtu.edu.vn,student.tdtu.edu.vn,STUDENT",
        "new@student.tdtu.edu.vn,tdtu.edu.vn,STUDENT",
        "teacher@tdtu.edu.vn,student.tdtu.edu.vn,LECTURER",
        "teacher@tdtu.edu.vn,tdtu.edu.vn,LECTURER",
        "524h0133@student.tdtu.edu.vn,student.tdtu.edu.vn,LECTURER",
        "524h0133@student.tdtu.edu.vn,tdtu.edu.vn,LECTURER"
    })
    void signedSchoolIdentityReceivesItsDomainRole(String email, String hd, AccountPolicy.Role expected) {
        var policy = policy();
        assertThat(policy.role(helper.user(email, true, hd))).isEqualTo(expected);
        assertThat(policy.isStudent(email)).isEqualTo(expected == AccountPolicy.Role.STUDENT);
        assertThat(policy.isLecturer(email)).isEqualTo(expected == AccountPolicy.Role.LECTURER);
    }
    @Test void exceptionHasPriorityAndEmailComparisonIgnoresCase() {
        var policy = policy();
        assertThat(policy.role(helper.user("524H0133@STUDENT.TDTU.EDU.VN", true, "tdtu.edu.vn")))
            .isEqualTo(AccountPolicy.Role.LECTURER);
        assertThat(policy.isStudent(DEMO_LECTURER)).isFalse();
        assertThat(policy.isStudent("NEW@STUDENT.TDTU.EDU.VN")).isTrue();
        assertThat(policy.isLecturer("TEACHER@TDTU.EDU.VN")).isTrue();
    }
    @Test void automaticRolesReplaceLegacyAssignmentsAndDoNotNeedTheOldStudentToggle() {
        var policy = policy(true, false, SCHOOL_DOMAINS,
            "teacher@tdtu.edu.vn", "new@student.tdtu.edu.vn", DEMO_LECTURER);
        assertThat(policy.role(helper.user("teacher@tdtu.edu.vn", true, "tdtu.edu.vn")))
            .isEqualTo(AccountPolicy.Role.LECTURER);
        assertThat(policy.role(helper.user("new@student.tdtu.edu.vn", true, "tdtu.edu.vn")))
            .isEqualTo(AccountPolicy.Role.STUDENT);
        assertThat(policy.isLecturer("new@student.tdtu.edu.vn")).isFalse();
    }
    @Test void overrideCanBeMovedToAnotherStudentAccount() {
        var policy = policy(true, false, SCHOOL_DOMAINS, "", "", "another-demo@student.tdtu.edu.vn");
        assertThat(policy.role(helper.user("another-demo@student.tdtu.edu.vn", true, "tdtu.edu.vn")))
            .isEqualTo(AccountPolicy.Role.LECTURER);
        assertThat(policy.role(helper.user(DEMO_LECTURER, true, "tdtu.edu.vn")))
            .isEqualTo(AccountPolicy.Role.STUDENT);
    }
    @ParameterizedTest @ValueSource(strings = {
        "new@student.tdtu.edu.vn", "teacher@tdtu.edu.vn", DEMO_LECTURER
    })
    void allRolesRequireVerifiedEmail(String email) {
        assertThatThrownBy(() -> policy().role(helper.user(email, false, "tdtu.edu.vn")))
            .isInstanceOf(AccessDeniedException.class);
    }
    @ParameterizedTest @ValueSource(strings = {
        "person@gmail.com", "new@student.tdtu.edu.vn.evil.example", "new@evilstudent.tdtu.edu.vn",
        "teacher@eviltdtu.edu.vn", "teacher@faculty.tdtu.edu.vn", "teacher@tdtu.edu.vn.evil.example",
        "new@student.tdtu.edu.vn@tdtu.edu.vn"
    })
    void unrelatedAndLookalikeEmailDomainsAreRejected(String email) {
        var policy = policy();
        assertThatThrownBy(() -> policy.role(helper.user(email, true, "tdtu.edu.vn")))
            .isInstanceOf(AccessDeniedException.class);
        assertThat(policy.isStudent(email)).isFalse();
        assertThat(policy.isLecturer(email)).isFalse();
    }
    @Test void legacyAllowlistCannotAdmitAnExternalAccountInAutomaticMode() {
        var policy = policy(true, false, SCHOOL_DOMAINS, "student@gmail.com", "teacher@gmail.com", DEMO_LECTURER);
        for (String email : Set.of("student@gmail.com", "teacher@gmail.com")) {
            assertThatThrownBy(() -> policy.role(helper.user(email, true, "tdtu.edu.vn")))
                .isInstanceOf(AccessDeniedException.class);
            assertThat(policy.isStudent(email)).isFalse();
            assertThat(policy.isLecturer(email)).isFalse();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"", "evil.example", "tdtu.edu.vn.evil.example", "faculty.tdtu.edu.vn"})
    void everyAutomaticRoleRequiresSchoolHdEvenWithoutGeneralFilter(String hd) {
        var policy = policy(true, false, "", "", "", DEMO_LECTURER);
        for (String email : Set.of("new@student.tdtu.edu.vn", "teacher@tdtu.edu.vn", DEMO_LECTURER))
            assertThatThrownBy(() -> policy.role(helper.user(email, true, hd)))
                .isInstanceOf(AccessDeniedException.class);
    }
    @Test void configuredHdFilterCanFurtherRestrictAdmission() {
        var policy = policy(true, false, "student.tdtu.edu.vn", "", "", DEMO_LECTURER);
        assertThatThrownBy(() -> policy.role(helper.user("new@student.tdtu.edu.vn", true, "tdtu.edu.vn")))
            .isInstanceOf(AccessDeniedException.class);
    }
    OidcUser userWithUserInfo(String email, boolean verified, String hd, Map<String, Object> info) {
        var signed = helper.user(email, verified, hd);
        return new DefaultOidcUser(signed.getAuthorities(), signed.getIdToken(), new OidcUserInfo(info), "sub");
    }
    @ParameterizedTest @ValueSource(strings = {"", "evil.example"})
    void userInfoCannotSupplyOrReplaceSignedWorkspaceHd(String hd) {
        String email = "new@student.tdtu.edu.vn";
        var user = userWithUserInfo(email, true, hd,
            Map.of("sub", email, "email", email, "email_verified", true, "hd", "tdtu.edu.vn"));
        assertThatThrownBy(() -> policy().role(user)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void userInfoCannotVerifyAnUnverifiedIdToken() {
        String email = "new@student.tdtu.edu.vn";
        var user = userWithUserInfo(email, false, "tdtu.edu.vn",
            Map.of("sub", email, "email", email, "email_verified", true, "hd", "tdtu.edu.vn"));
        assertThatThrownBy(() -> policy().role(user)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void conflictingUserInfoEmailCannotPromoteStudentToLecturer() {
        String email = "new@student.tdtu.edu.vn";
        var user = userWithUserInfo(email, true, "tdtu.edu.vn",
            Map.of("sub", email, "email", DEMO_LECTURER, "email_verified", true, "hd", "tdtu.edu.vn"));
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(policy().role(user)).isEqualTo(AccountPolicy.Role.STUDENT);
    }
    @Test void disabledDomainModeRetainsLegacyAllowlists() {
        String lecturer = "demo-lecturer@student.tdtu.edu.vn";
        var policy = policy(false, false, SCHOOL_DOMAINS, "listed@student.tdtu.edu.vn", lecturer, DEMO_LECTURER);
        assertThat(policy.role(helper.user("listed@student.tdtu.edu.vn", true, "tdtu.edu.vn")))
            .isEqualTo(AccountPolicy.Role.STUDENT);
        assertThat(policy.role(helper.user(lecturer, true, "tdtu.edu.vn")))
            .isEqualTo(AccountPolicy.Role.LECTURER);
        assertThatThrownBy(() -> policy.role(helper.user("new@student.tdtu.edu.vn", true, "tdtu.edu.vn")))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.role(helper.user("teacher@tdtu.edu.vn", true, "tdtu.edu.vn")))
            .isInstanceOf(AccessDeniedException.class);
    }
    @Test void legacyStudentToggleStillSupportsItsOriginalExplicitLecturerException() {
        String lecturer = "demo-lecturer@student.tdtu.edu.vn";
        var policy = policy(false, true, SCHOOL_DOMAINS, "", lecturer, DEMO_LECTURER);
        assertThat(policy.role(helper.user("new@student.tdtu.edu.vn", true, "tdtu.edu.vn")))
            .isEqualTo(AccountPolicy.Role.STUDENT);
        assertThat(policy.role(helper.user(lecturer, true, "tdtu.edu.vn")))
            .isEqualTo(AccountPolicy.Role.LECTURER);
        assertThat(policy.isStudent(lecturer)).isFalse();
        assertThatThrownBy(() -> policy.role(helper.user("teacher@tdtu.edu.vn", true, "tdtu.edu.vn")))
            .isInstanceOf(AccessDeniedException.class);
    }
}
