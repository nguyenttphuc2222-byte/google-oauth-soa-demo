package vn.edu.seminar;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SharedDriveAccessTest extends DriveAccessHttpTest {
    static final AutoDriveGoogle GOOGLE;
    static{try{GOOGLE=new AutoDriveGoogle();}catch(Exception error){throw new ExceptionInInitializerError(error);}}
    @Override AutoDriveGoogle google(){return GOOGLE;}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){configure(registry,GOOGLE,"SHARED");}
    @Test void sharedModeRequiresOwnerAcknowledgementBeforeAnyPermissionChanges()throws Exception{
        ownerConsent();assertThat(connect(false).statusCode()).isEqualTo(400);assertThat(access.ready()).isFalse();assertThat(GOOGLE.mutations).isEmpty();
    }
    @Test void newStudentAndLecturerGetExpectedRootRolesWhileOwnerIsPreserved()throws Exception{
        setup(true);login(STUDENT);assertThat(GOOGLE.role(AutoDriveGoogle.ROOT,STUDENT)).isEqualTo(30);
        login(LECTURER);assertThat(GOOGLE.role(AutoDriveGoogle.ROOT,LECTURER)).isEqualTo(10);
        login(OWNER);assertThat(GOOGLE.acl.get(AutoDriveGoogle.ROOT)).anySatisfy(p->{assertThat(p.get("emailAddress")).isEqualTo(OWNER);assertThat(p.get("role")).isEqualTo("owner");});
        assertThat(GOOGLE.mutations).allSatisfy(m->{assertThat(m.get("type")).isEqualTo("user");assertThat(m.get("role")).isIn("reader","writer");});
        assertThat(GOOGLE.foldersCreated.get()).isZero();
    }
    @Test void existingReaderIsUpgradedButPreExistingStrongerGrantsAreNotDowngraded()throws Exception{
        GOOGLE.allow(AutoDriveGoogle.ROOT,STUDENT,"reader");GOOGLE.allow(AutoDriveGoogle.ROOT,LECTURER,"writer");setup(true);
        login(STUDENT);login(LECTURER);
        assertThat(GOOGLE.role(AutoDriveGoogle.ROOT,STUDENT)).isEqualTo(30);assertThat(GOOGLE.role(AutoDriveGoogle.ROOT,LECTURER)).isEqualTo(30);
        assertThat(GOOGLE.mutations).hasSize(1);assertThat(GOOGLE.mutations.get(0).get("method")).isEqualTo("PATCH");
    }
    @ParameterizedTest @ValueSource(strings={"group","domain","anyone"})
    void broadExistingRootSharesCannotBeSilentlyAccepted(String type)throws Exception{
        GOOGLE.acl.get(AutoDriveGoogle.ROOT).add(new HashMap<>(Map.of("id","broad_share","type",type,"role","reader")));
        ownerConsent();assertThat(connect(true).statusCode()).isEqualTo(400);assertThat(access.ready()).isFalse();assertThat(GOOGLE.mutations).isEmpty();
    }
    @Test void existingExternalUserShareIsNotSilentlyAccepted()throws Exception{
        GOOGLE.allow(AutoDriveGoogle.ROOT,"person@gmail.com","reader");ownerConsent();
        assertThat(connect(true).statusCode()).isEqualTo(400);assertThat(GOOGLE.mutations).isEmpty();
    }
}
