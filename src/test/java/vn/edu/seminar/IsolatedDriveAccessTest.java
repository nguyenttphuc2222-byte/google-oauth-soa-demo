package vn.edu.seminar;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class IsolatedDriveAccessTest extends DriveAccessHttpTest {
    static final AutoDriveGoogle GOOGLE;
    static{try{GOOGLE=new AutoDriveGoogle();}catch(Exception error){throw new ExceptionInInitializerError(error);}}
    @Override AutoDriveGoogle google(){return GOOGLE;}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){configure(registry,GOOGLE,"ISOLATED");}

    @Test void loginProvisionsOnlyPersonalFolderAndSubmissionSharesOnlyItsFile()throws Exception{
        setup(false);login(STUDENT);String folder=targetFolder(STUDENT);
        assertThat(folder).isNotEqualTo(AutoDriveGoogle.ROOT);assertThat(GOOGLE.role(folder,STUDENT)).isEqualTo(30);assertThat(GOOGLE.role(AutoDriveGoogle.ROOT,STUDENT)).isZero();
        assertThat(GOOGLE.mutations).allSatisfy(mutation->assertThat(mutation.get("type")).isEqualTo("user"));
        grantStudent();String id=submit(LECTURER);var submission=jdbc.queryForMap("SELECT file_id FROM submissions WHERE id=?",id);String fileId=submission.get("FILE_ID").toString();
        assertThat(GOOGLE.nodes.get(fileId).get("parents")).isEqualTo(List.of(folder));assertThat(GOOGLE.viewers.get(fileId)).isEqualTo(LECTURER);
        assertThat(GOOGLE.role(folder,LECTURER)).isZero();assertThat(GOOGLE.role(AutoDriveGoogle.ROOT,LECTURER)).isZero();
        int mutations=GOOGLE.mutations.size();login(LECTURER);assertThat(GOOGLE.mutations).hasSize(mutations);
        assertThat(get("/submissions/"+id).statusCode()).isEqualTo(200);
        login("other@tdtu.edu.vn");assertThat(get("/submissions/"+id).statusCode()).isEqualTo(403);
        login("second@student.tdtu.edu.vn");assertThat(targetFolder("second@student.tdtu.edu.vn")).isNotEqualTo(folder);
        assertThat(GOOGLE.role(folder,"second@student.tdtu.edu.vn")).isZero();assertThat(GOOGLE.role(targetFolder("second@student.tdtu.edu.vn"),STUDENT)).isZero();
        assertThat(GOOGLE.sentActors).containsExactly(STUDENT);
    }
    @Test void ownerRefreshGrantIsEncryptedAndWorksWithoutOwnerSession()throws Exception{
        setup(false);String cipher=jdbc.queryForObject("SELECT refresh_cipher FROM drive_admin_grant",String.class);
        assertThat(cipher).startsWith("v1.").doesNotContain("mock-refresh");post("/logout","");
        int before=GOOGLE.refreshes.get();var fresh=freshService();
        var folder=fresh.target(new CoreUnitTest().user(STUDENT,true,"tdtu.edu.vn"),new MockHttpServletRequest());
        assertThat(folder.id()).isEqualTo(targetFolder(STUDENT));assertThat(GOOGLE.refreshes.get()).isEqualTo(before+1);
        assertThat(fresh.ready()).isTrue();
    }
    @Test void encryptedGrantAndFolderMappingWorkAfterDiskDatabaseReopens(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)throws Exception{
        setup(false);login(STUDENT);String folder=targetFolder(STUDENT);post("/logout","");
        String url="jdbc:h2:file:"+directory.resolve("drive-state")+";DB_CLOSE_ON_EXIT=FALSE";
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"sa","");
        var schema=new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("schema.sql"));schema.execute(source);
        var disk=new org.springframework.jdbc.core.JdbcTemplate(source);var row=jdbc.queryForMap("SELECT * FROM drive_admin_grant");
        disk.update("INSERT INTO drive_admin_grant VALUES (1,?,?,?,?,?,?)",row.get("OWNER_EMAIL"),row.get("OWNER_SUB"),row.get("ROOT_ID"),row.get("MODE"),row.get("CLIENT_ID"),row.get("REFRESH_CIPHER"));
        disk.update("INSERT INTO drive_student_folders VALUES (?,?,?,?)",AutoDriveGoogle.ROOT,DriveAccessService.studentKey(STUDENT),folder,"Thư mục sinh viên");disk.execute("SHUTDOWN");
        var reopenedSource=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"sa","");schema.execute(reopenedSource);
        var reopened=new org.springframework.jdbc.core.JdbcTemplate(reopenedSource);
        try{
            var cold=new DriveAccessService(settings,driveSettings,policy,reopened,drive,box,tokens,clients,refresh);int before=GOOGLE.refreshes.get();
            assertThat(cold.target(new CoreUnitTest().user(STUDENT,true,"tdtu.edu.vn"),new MockHttpServletRequest()).id()).isEqualTo(folder);
            assertThat(cold.ready()).isTrue();assertThat(GOOGLE.refreshes.get()).isEqualTo(before+1);assertThat(GOOGLE.foldersCreated.get()).isEqualTo(1);
        }finally{reopened.execute("SHUTDOWN");}
    }
    @Test void pickerCredentialsContainOnlyCurrentTargetAndUserToken()throws Exception{
        setup(false);GOOGLE.pickerRequired.add(STUDENT);login(STUDENT);grantStudent();
        String folder=targetFolder(STUDENT);var credentials=json("/api/picker/credentials","{}");
        assertThat(credentials.statusCode()).isEqualTo(200);assertThat(credentials.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var config=GOOGLE.mapper.readTree(credentials.body());assertThat(config.path("folderId").asText()).isEqualTo(folder);
        assertThat(GOOGLE.access.get(config.path("accessToken").asText()).email()).isEqualTo(STUDENT);
        assertThat(credentials.body()).doesNotContain("refresh","cipher",DriveSecretBoxTest.KEY);
        assertThat(json("/api/picker/folder","{\"id\":\""+AutoDriveGoogle.ROOT+"\"}").statusCode()).isEqualTo(400);
        login("second@student.tdtu.edu.vn");String other=targetFolder("second@student.tdtu.edu.vn");login(STUDENT);grantStudent();
        assertThat(json("/api/picker/folder","{\"id\":\""+other+"\"}").statusCode()).isEqualTo(400);
        GOOGLE.picked.add(STUDENT+":"+folder);assertThat(json("/api/picker/folder","{\"id\":\""+folder+"\"}").statusCode()).isEqualTo(200);
        String javascript=get("/static/app.js").body();assertThat(javascript).contains("setFileIds(config.folderId)","Feature.NAV_HIDDEN");
    }
    @ParameterizedTest @ValueSource(strings={"user","group","domain","anyone"})
    void inheritedOrBroadRootPermissionsStopIsolatedSetupWithoutDeletingAnything(String type)throws Exception{
        var extra=new HashMap<String,Object>(Map.of("id","old_permission","type",type,"role","reader","emailAddress",LECTURER));GOOGLE.acl.get(AutoDriveGoogle.ROOT).add(extra);
        GOOGLE.permissionPageSize=1;ownerConsent();assertThat(connect(false).statusCode()).isEqualTo(400);
        assertThat(access.ready()).isFalse();assertThat(GOOGLE.acl.get(AutoDriveGoogle.ROOT)).contains(extra);assertThat(GOOGLE.mutations).isEmpty();
    }
    @Test void unknownOwnerAndForeignFolderCannotBeRegistered()throws Exception{
        ownerConsent();assertThat(json("/api/drive-setup/connect","{\"folderId\":\"another_folder_123\"}").statusCode()).isEqualTo(400);
        GOOGLE.nodes.get(AutoDriveGoogle.ROOT).put("owners",List.of(Map.of("emailAddress","other@tdtu.edu.vn")));
        assertThat(connect(false).statusCode()).isEqualTo(400);assertThat(access.ready()).isFalse();
    }
    @Test void ordinaryStudentAndLecturerCannotUseOrConsentToOwnerEndpoints()throws Exception{
        for(String email:new String[]{STUDENT,LECTURER}){
            login(email);assertThat(get("/drive-setup").statusCode()).isEqualTo(403);
            assertThat(json("/api/drive-setup/credentials","{}").statusCode()).isEqualTo(403);
            assertThat(connect(true).statusCode()).isEqualTo(403);
            assertThat(post("/oauth/connect/drive_admin","returnTo=%2Fdrive-setup").statusCode()).isEqualTo(403);
        }
        assertThat(GOOGLE.mutations).isEmpty();
    }
    @Test void failedFolderCreationIsRecoveredAndRepeatedLoginDoesNotDuplicateFolders()throws Exception{
        setup(false);GOOGLE.failAfterFolder=true;login(STUDENT);assertThat(GOOGLE.foldersCreated.get()).isEqualTo(1);
        login(STUDENT);String id=targetFolder(STUDENT);login(STUDENT);
        assertThat(targetFolder(STUDENT)).isEqualTo(id);assertThat(GOOGLE.foldersCreated.get()).isEqualTo(1);
        long grants=GOOGLE.mutations.stream().filter(m->m.get("email").equals(STUDENT)&&m.get("role").equals("writer")).count();assertThat(grants).isEqualTo(1);
    }
    @Test void sharingFailureKeepsLoginWorkingAndAllowsRetry()throws Exception{
        setup(false);GOOGLE.denySharing=true;assertThat(login(STUDENT).uri().getPath()).isEqualTo("/dashboard");assertThat(GOOGLE.foldersCreated.get()).isEqualTo(1);
        GOOGLE.denySharing=false;grantStudent();assertThat(GOOGLE.foldersCreated.get()).isEqualTo(1);assertThat(GOOGLE.role(targetFolder(STUDENT),STUDENT)).isEqualTo(30);
    }
    @Test void laterChangesToRootOrPersonalAclStopFurtherProvisioning()throws Exception{
        setup(false);login(STUDENT);String folder=targetFolder(STUDENT);GOOGLE.allow(folder,"foreign@tdtu.edu.vn","reader");
        assertThatThrownBy(()->freshService().target(new CoreUnitTest().user(STUDENT,true,"tdtu.edu.vn"),new MockHttpServletRequest())).isInstanceOf(IllegalStateException.class).hasMessageContaining("chia sẻ thêm");
        GOOGLE.acl.get(folder).removeIf(p->"foreign@tdtu.edu.vn".equals(p.get("emailAddress")));GOOGLE.allow(AutoDriveGoogle.ROOT,LECTURER,"reader");
        assertThatThrownBy(()->freshService().target(new CoreUnitTest().user(STUDENT,true,"tdtu.edu.vn"),new MockHttpServletRequest())).isInstanceOf(IllegalStateException.class).hasMessageContaining("chỉ có chủ sở hữu");
    }
    @Test void invalidOwnerRefreshGrantIsDisabledInsteadOfFallingBackToWiderAccess()throws Exception{
        setup(false);GOOGLE.rejectRefresh=true;
        assertThatThrownBy(()->freshService().target(new CoreUnitTest().user(STUDENT,true,"tdtu.edu.vn"),new MockHttpServletRequest())).isInstanceOf(IllegalStateException.class);
        assertThat(access.ready()).isFalse();assertThat(GOOGLE.mutations).isEmpty();
    }
    @Test void disconnectRemovesStoredSecretButKeepsDrivePermissionsAndFiles()throws Exception{
        setup(false);login(STUDENT);String folder=targetFolder(STUDENT);login(OWNER);post("/drive-setup/disconnect","");
        assertThat(access.ready()).isFalse();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM drive_admin_grant",Integer.class)).isZero();
        assertThat(GOOGLE.nodes).containsKey(folder);assertThat(GOOGLE.role(folder,STUDENT)).isEqualTo(30);
    }
    @Test void setupAndProvisioningFailClosedBeforeOwnerConsent()throws Exception{
        assertThat(login(STUDENT).uri().getPath()).isEqualTo("/dashboard");grantStudent();
        assertThat(json("/api/picker/credentials","{}").statusCode()).isEqualTo(400);
        assertThat(GOOGLE.mutations).isEmpty();assertThat(GOOGLE.foldersCreated.get()).isZero();
    }
    @Test void ownerSetupRequiresCsrfAndDoesNotExposeSavedRefreshToken()throws Exception{
        ownerConsent();
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/drive-setup/connect"))
            .header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"folderId\":\"folder_123456\"}")).build();
        assertThat(browser.send(request,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(access.ready()).isFalse();assertThat(connect(false).statusCode()).isEqualTo(200);
        assertThat(get("/drive-setup").body()).doesNotContain("mock-refresh-",DriveSecretBoxTest.KEY,"v1.");
    }
    @Test void configurationBindingStopsReuseForAnotherRootOrClient()throws Exception{
        setup(false);jdbc.update("UPDATE drive_admin_grant SET root_id='different_folder_123'");
        assertThat(access.ready()).isFalse();
        assertThatThrownBy(()->freshService().target(new CoreUnitTest().user(STUDENT,true,"tdtu.edu.vn"),new MockHttpServletRequest())).isInstanceOf(IllegalStateException.class);
        assertThat(GOOGLE.mutations).isEmpty();
    }
    @Test void personalFolderMovedOutOfTheClassTreeCannotBeUsed()throws Exception{
        setup(false);login(STUDENT);String folder=targetFolder(STUDENT);GOOGLE.nodes.get(folder).put("parents",List.of("elsewhere_folder_123"));
        assertThatThrownBy(()->freshService().target(new CoreUnitTest().user(STUDENT,true,"tdtu.edu.vn"),new MockHttpServletRequest())).isInstanceOf(IllegalStateException.class);
    }
}
