package vn.edu.seminar;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import static org.assertj.core.api.Assertions.*;

abstract class DriveAccessHttpTest {
    static final String OWNER=AutoDriveGoogle.OWNER, STUDENT="new@student.tdtu.edu.vn", LECTURER="524h0133@student.tdtu.edu.vn";
    abstract AutoDriveGoogle google();
    static void configure(DynamicPropertyRegistry registry,AutoDriveGoogle google,String mode){
        var properties=google.properties();properties.put("app.auto-domain-roles",true);properties.put("app.student-emails","");properties.put("app.lecturer-emails","");
        properties.put("app.allowed-workspace-domains","student.tdtu.edu.vn,tdtu.edu.vn");properties.put("app.lecturer-override-email",LECTURER);
        properties.put("drive-access.mode",mode);properties.put("drive-access.owner-email",OWNER);properties.put("drive-access.token-key",DriveSecretBoxTest.KEY);
        properties.forEach((name,value)->registry.add(name,()->value));
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DriveAccessService access;
    @Autowired DriveClient drive;
    @Autowired AppSettings settings;
    @Autowired DriveAccessSettings driveSettings;
    @Autowired AccountPolicy policy;
    @Autowired DriveSecretBox box;
    @Autowired TokenService tokens;
    @Autowired ClientRegistrationRepository clients;
    @Autowired RestClientRefreshTokenTokenResponseClient refresh;
    HttpClient browser;
    @BeforeEach void before(){
        google().reset();browser=newBrowser();
        for(var table:List.of("drive_admin_grant","drive_student_folders","lecturer_directory","mail_notices","reviews","submissions"))jdbc.update("DELETE FROM "+table);
    }
    HttpClient newBrowser(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(10)).build();}
    HttpResponse<String> get(String path)throws Exception{return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(25)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    String field(String html,String name){var matcher=Pattern.compile("name=\""+name+"\" (?:content|value)=\"([^\"]+)\"").matcher(html);assertThat(matcher.find()).as(name).isTrue();return matcher.group(1);}
    HttpResponse<String> send(String path,String body,String type)throws Exception{
        String csrf=field(get("/oauth").body(),"_csrf");return browser.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(25)).header("Content-Type",type).header("X-CSRF-TOKEN",csrf).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String path,String body)throws Exception{return send(path,body,"application/x-www-form-urlencoded");}
    HttpResponse<String> json(String path,String body)throws Exception{return send(path,body,"application/json");}
    HttpResponse<String> login(String email)throws Exception{google().nextEmail=email;google().nextSub=email;google().nextHd="tdtu.edu.vn";browser=newBrowser();return get("/oauth2/authorization/google");}
    void grantStudent()throws Exception{post("/oauth/connect/submit","returnTo=%2Fsubmit");}
    void ownerConsent()throws Exception{login(OWNER);assertThat(post("/oauth/connect/drive_admin","returnTo=%2Fdrive-setup").statusCode()).isEqualTo(200);}
    HttpResponse<String> connect(boolean acknowledge)throws Exception{return json("/api/drive-setup/connect","{\"folderId\":\""+AutoDriveGoogle.ROOT+"\",\"acknowledgeShared\":"+acknowledge+"}");}
    void setup(boolean acknowledge)throws Exception{ownerConsent();assertThat(connect(acknowledge).statusCode()).isEqualTo(200);assertThat(access.ready()).isTrue();}
    String targetFolder(String email){return jdbc.queryForObject("SELECT folder_id FROM drive_student_folders WHERE root_id=? AND student_key=?",String.class,AutoDriveGoogle.ROOT,DriveAccessService.studentKey(email));}
    String submit(String recipient)throws Exception{
        String id=field(get("/submit").body(),"attemptId"),boundary="automatic-drive-boundary";var body=new StringBuilder();
        for(var entry:Map.of("attemptId",id,"groupName","Nhóm Drive tự động","topic","Drive ACL","lecturerEmail",recipient).entrySet())body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(entry.getKey()).append("\"\r\n\r\n").append(entry.getValue()).append("\r\n");
        body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"report.pdf\"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.4\nDemo\r\n--").append(boundary).append("--\r\n");
        assertThat(send("/submissions",body.toString(),"multipart/form-data; boundary="+boundary).statusCode()).isEqualTo(200);return id;
    }
    DriveAccessService freshService(){return new DriveAccessService(settings,driveSettings,policy,jdbc,drive,box,tokens,clients,refresh);}
}
