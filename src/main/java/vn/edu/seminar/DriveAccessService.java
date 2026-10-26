package vn.edu.seminar;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/** One application instance serializes owner-token refresh and folder permission mutations. */
@Service
public class DriveAccessService {
    private record Grant(String email, String sub, String root, String mode, String client, String cipher) {
        String context() { return String.join("\n", "seminar-drive-v1", email, sub, root, mode, client); }
    }
    private final AppSettings app;
    private final DriveAccessSettings settings;
    private final AccountPolicy policy;
    private final JdbcTemplate jdbc;
    private final DriveClient drive;
    private final DriveSecretBox box;
    private final TokenService tokens;
    private final ClientRegistrationRepository clients;
    private final RestClientRefreshTokenTokenResponseClient refresh;
    private OAuth2AccessToken cachedAccess;
    public DriveAccessService(AppSettings app, DriveAccessSettings settings, AccountPolicy policy, JdbcTemplate jdbc,
            DriveClient drive, DriveSecretBox box, TokenService tokens, ClientRegistrationRepository clients,
            RestClientRefreshTokenTokenResponseClient refresh) {
        this.app=app;this.settings=settings;this.policy=policy;this.jdbc=jdbc;this.drive=drive;
        this.box=box;this.tokens=tokens;this.clients=clients;this.refresh=refresh;
    }
    public boolean automatic() { return settings.automatic(); }
    public String mode() { return settings.mode().name(); }
    public String ownerEmail() { return settings.ownerEmail(); }
    public boolean keyReady() { return settings.keyReady(); }
    public boolean isAdmin(OidcUser user) {
        return user != null && !settings.ownerEmail().isBlank() && settings.ownerEmail().equals(AccountPolicy.normalize(user.getEmail()));
    }
    public void requireAdmin(OidcUser user) {
        policy.role(user);
        if (!isAdmin(user) || !schoolIdentity(user)) throw new AccessDeniedException("Chỉ chủ thư mục TDTU đã xác thực và được cấu hình mới được thiết lập quyền Drive.");
    }
    private Optional<Grant> grant() {
        return jdbc.query("SELECT * FROM drive_admin_grant WHERE id=1", (row,n)->new Grant(row.getString("owner_email"),
            row.getString("owner_sub"),row.getString("root_id"),row.getString("mode"),row.getString("client_id"),row.getString("refresh_cipher")))
            .stream().findFirst();
    }
    private boolean matches(Grant grant) {
        return grant.email().equals(settings.ownerEmail()) && grant.root().equals(app.folderId())
            && grant.mode().equals(mode()) && grant.client().equals(app.clientId());
    }
    public synchronized boolean ready() {
        return automatic() && keyReady() && grant().filter(this::matches).isPresent();
    }
    public synchronized void connect(String folderId, boolean acknowledgeShared, OidcUser user,
            Authentication authentication, HttpServletRequest request) {
        requireAdmin(user);
        if (!automatic()) throw new IllegalStateException("Đặt DRIVE_ACCESS_MODE=ISOLATED hoặc SHARED trước khi kết nối tự động.");
        settings.keyBytes();
        if (!app.folderId().equals(folderId)) throw new IllegalArgumentException("Chỉ kết nối đúng thư mục lớp đã cấu hình.");
        if (settings.mode()==DriveAccessSettings.Mode.SHARED && !acknowledgeShared)
            throw new IllegalArgumentException("Xác nhận phạm vi thư mục dùng chung trước khi bật tự cấp quyền.");
        String access=tokens.require(authentication,request,"drive_admin",Scopes.DRIVE);
        var client=tokens.client(authentication,request);
        if(client==null || client.getRefreshToken()==null)
            throw new IllegalStateException("Cần refresh token của chủ thư mục. Bấm cấp quyền Drive lại rồi kết nối.");
        verifyRoot(access,request);
        var bound=new Grant(settings.ownerEmail(),user.getSubject(),app.folderId(),mode(),app.clientId(),"");
        String encrypted=box.seal(client.getRefreshToken().getTokenValue(),bound.context());
        jdbc.update("MERGE INTO drive_admin_grant(id,owner_email,owner_sub,root_id,mode,client_id,refresh_cipher) KEY(id) VALUES (1,?,?,?,?,?,?)",
            bound.email(),bound.sub(),bound.root(),bound.mode(),bound.client(),encrypted);
        cachedAccess=client.getAccessToken();
        request.getSession().removeAttribute(SubmissionService.FOLDER);
    }
    public synchronized void disconnect(OidcUser user, HttpServletRequest request) {
        requireAdmin(user);jdbc.update("DELETE FROM drive_admin_grant");cachedAccess=null;
        request.getSession().removeAttribute(SubmissionService.FOLDER);
    }
    public synchronized void clearIfOwner(OidcUser user, HttpServletRequest request) {
        if(isAdmin(user)){jdbc.update("DELETE FROM drive_admin_grant");cachedAccess=null;request.getSession().removeAttribute(SubmissionService.FOLDER);}
    }
    private String ownerToken() {
        var stored=grant().filter(this::matches).orElseThrow(()->new IllegalStateException("Chủ thư mục chưa kết nối quyền Drive tự động cho cấu hình hiện tại."));
        if(cachedAccess!=null && cachedAccess.getExpiresAt()!=null && cachedAccess.getExpiresAt().isAfter(Instant.now().plusSeconds(45)))
            return cachedAccess.getTokenValue();
        String secret=box.open(stored.cipher(),stored.context());
        var old=new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"refresh-bootstrap",Instant.now().minusSeconds(3600),Instant.now().minusSeconds(1),Set.of(Scopes.DRIVE));
        try {
            var answer=refresh.getTokenResponse(new OAuth2RefreshTokenGrantRequest(clients.findByRegistrationId("google"),old,new OAuth2RefreshToken(secret,Instant.EPOCH)));
            if(!answer.getAccessToken().getScopes().contains(Scopes.DRIVE))throw new IllegalStateException("Quyền Drive của chủ thư mục không còn đủ; cần kết nối lại.");
            if(answer.getRefreshToken()!=null){
                String cipher=box.seal(answer.getRefreshToken().getTokenValue(),stored.context());
                jdbc.update("UPDATE drive_admin_grant SET refresh_cipher=? WHERE id=1",cipher);
            }
            cachedAccess=answer.getAccessToken();return cachedAccess.getTokenValue();
        }catch(OAuth2AuthorizationException exception){
            if("invalid_grant".equals(exception.getError().getErrorCode())){jdbc.update("DELETE FROM drive_admin_grant");cachedAccess=null;}
            throw new IllegalStateException("Chưa làm mới được quyền Drive của chủ thư mục. Chủ thư mục cần kiểm tra/kết nối lại.");
        }
    }
    private com.fasterxml.jackson.databind.JsonNode verifyRoot(String token,HttpServletRequest request) {
        var root=drive.metadata(app.folderId(),token,request);
        boolean owner=false;for(var item:root.path("owners"))owner|=settings.ownerEmail().equalsIgnoreCase(item.path("emailAddress").asText());
        if(!root.path("mimeType").asText().equals("application/vnd.google-apps.folder") || root.path("trashed").asBoolean()
                || !root.path("driveId").asText().isBlank() || !owner
                || !root.path("capabilities").path("canShare").asBoolean() || !root.path("capabilities").path("canAddChildren").asBoolean())
            throw new IllegalStateException("Cần thư mục My Drive do đúng tài khoản chủ thư mục sở hữu và được phép chia sẻ/thêm file.");
        boolean foundOwner=false;
        for(var permission:drive.permissions(app.folderId(),token,request)){
            if(permission.path("deleted").asBoolean())continue;
            String email=AccountPolicy.normalize(permission.path("emailAddress").asText());
            if(permission.path("role").asText().equals("owner") && email.equals(settings.ownerEmail())){foundOwner=true;continue;}
            if(settings.mode()==DriveAccessSettings.Mode.ISOLATED)
                throw new IllegalStateException("Chế độ riêng yêu cầu thư mục lớp chỉ có chủ sở hữu. Thư mục hiện còn quyền chia sẻ; dùng một thư mục riêng hoặc kiểm tra quyền cũ trên Drive. App không tự xóa quyền.");
            if(!permission.path("type").asText().equals("user") || !schoolEmail(email))
                throw new IllegalStateException("Thư mục lớp có quyền nhóm, domain, anyone hoặc tài khoản ngoài TDTU. Chủ thư mục cần kiểm tra phạm vi chia sẻ trước.");
        }
        if(!foundOwner)throw new IllegalStateException("Chưa xác nhận được đầy đủ quyền chủ sở hữu của thư mục.");
        return root;
    }
    public static boolean schoolEmail(String email) {
        try{email=AccountPolicy.email(email);}catch(IllegalArgumentException invalid){return false;}
        String domain=email.substring(email.lastIndexOf('@')+1);return Set.of("student.tdtu.edu.vn","tdtu.edu.vn").contains(domain);
    }
    private static boolean schoolIdentity(OidcUser user) {
        return user!=null && user.getIdToken()!=null && Boolean.TRUE.equals(user.getIdToken().getEmailVerified())
            && schoolEmail(user.getIdToken().getEmail()) && Set.of("tdtu.edu.vn","student.tdtu.edu.vn").contains(AccountPolicy.normalize(user.getIdToken().getClaimAsString("hd")));
    }
    public synchronized DriveClient.Folder target(OidcUser user,HttpServletRequest request) {
        policy.require(user,AccountPolicy.Role.STUDENT);
        return provision(user,request);
    }
    private DriveClient.Folder provision(OidcUser user,HttpServletRequest request) {
        var role=policy.role(user);
        if(!automatic())return new DriveClient.Folder(app.folderId(),"Seminar - Minh chứng");
        if(settings.mode()==DriveAccessSettings.Mode.ISOLATED && role==AccountPolicy.Role.LECTURER)return null;
        if(!schoolIdentity(user))throw new AccessDeniedException("Chỉ tài khoản TDTU đã xác thực được tự động cấp quyền Drive.");
        settings.keyBytes();String token=ownerToken();var root=verifyRoot(token,request);
        String email=AccountPolicy.email(user.getEmail());
        if(settings.mode()==DriveAccessSettings.Mode.SHARED){
            drive.ensureRole(app.folderId(),email,role==AccountPolicy.Role.STUDENT?"writer":"reader",token,request);
            return new DriveClient.Folder(app.folderId(),root.path("name").asText("Seminar - Minh chứng"));
        }
        String key=studentKey(user.getSubject());
        var stored=jdbc.query("SELECT folder_id,folder_name FROM drive_student_folders WHERE root_id=? AND student_key=?",
            (row,n)->new DriveClient.Folder(row.getString("folder_id"),row.getString("folder_name")),app.folderId(),key);
        var folder=stored.isEmpty()?drive.personalFolder(app.folderId(),key,"Seminar - Minh chứng - "+email,token,request):stored.get(0);
        var metadata=drive.metadata(folder.id(),token,request);boolean parent=false,owned=false;
        for(var id:metadata.path("parents"))parent|=id.asText().equals(app.folderId());
        for(var owner:metadata.path("owners"))owned|=owner.path("emailAddress").asText().equalsIgnoreCase(settings.ownerEmail());
        if(!parent || !owned || metadata.path("trashed").asBoolean() || metadata.path("writersCanShare").asBoolean()
                || !metadata.path("mimeType").asText().equals("application/vnd.google-apps.folder")
                || !key.equals(metadata.path("appProperties").path("driveAccessStudent").asText())
                || !app.folderId().equals(metadata.path("appProperties").path("driveAccessRoot").asText()))
            throw new IllegalStateException("Thư mục riêng không còn khớp cấu hình hoặc phạm vi chia sẻ. Chủ thư mục cần kiểm tra.");
        for(var permission:drive.permissions(folder.id(),token,request)){
            if(permission.path("deleted").asBoolean())continue;
            String grantee=permission.path("emailAddress").asText();
            if(!permission.path("type").asText().equals("user") || (!grantee.equalsIgnoreCase(settings.ownerEmail()) && !grantee.equalsIgnoreCase(email)))
                throw new IllegalStateException("Thư mục riêng có quyền chia sẻ thêm ngoài sinh viên/chủ thư mục. Cần kiểm tra trên Drive.");
        }
        drive.ensureRole(folder.id(),email,"writer",token,request);
        String name=metadata.path("name").asText(folder.name());
        jdbc.update("MERGE INTO drive_student_folders(root_id,student_key,folder_id,folder_name) KEY(root_id,student_key) VALUES (?,?,?,?)",app.folderId(),key,folder.id(),name);
        return new DriveClient.Folder(folder.id(),name);
    }
    static String studentKey(String subject) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public synchronized void onLogin(OidcUser user,Authentication authentication,HttpServletRequest request) {
        if(!automatic())return;
        try {
            var folder=provision(user,request);
            if(folder!=null && policy.role(user)==AccountPolicy.Role.STUDENT && tokens.has(authentication,request,Scopes.DRIVE)){
                String token=tokens.require(authentication,request,"submit",Scopes.DRIVE);
                try {request.getSession().setAttribute(SubmissionService.FOLDER,drive.verifyFolder(folder.id(),folder.id(),token,request));}
                catch(GoogleApiException | IllegalArgumentException permission){request.getSession().removeAttribute(SubmissionService.FOLDER);}
            }
        }catch(GoogleApiException | IllegalStateException | AccessDeniedException | Scopes.Missing problem){
            request.getSession().removeAttribute(SubmissionService.FOLDER);
            request.getSession().setAttribute("ui.error","Đã đăng nhập; chưa chuẩn bị được quyền Drive. "+problem.getMessage());
        }
    }
}
