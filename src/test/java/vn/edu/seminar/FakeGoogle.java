package vn.edu.seminar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.*;

/** Local test provider ONLY. This class is never included in the production JAR. */
public class FakeGoogle extends Dispatcher implements AutoCloseable {
    final MockWebServer server=new MockWebServer();final ObjectMapper mapper=new ObjectMapper();final RSAKey key;
    public final AtomicInteger exchanges=new AtomicInteger(),refreshes=new AtomicInteger(),revocations=new AtomicInteger(),uploads=new AtomicInteger();
    public volatile String nextEmail="student@example.com",nextSub="student-sub",nextHd="";
    public volatile String nextUserInfoEmail,nextUserInfoHd="";
    public volatile boolean nextEmailVerified=true,nextUserInfoEmailVerified=true;
    public volatile boolean wrongNonce,identityOnly,failShare,failAfterUpload,omitDownloadLink;
    public volatile int mailError;
    final Map<String,Step> codes=new ConcurrentHashMap<>(),access=new ConcurrentHashMap<>(),refresh=new ConcurrentHashMap<>();
    public final List<List<Object>> submissions=Collections.synchronizedList(new ArrayList<>()),reviews=Collections.synchronizedList(new ArrayList<>());
    public final List<String> sentRaw=Collections.synchronizedList(new ArrayList<>());
    final Map<String,Map<String,Object>> files=new ConcurrentHashMap<>();
    final Map<String,String> viewers=new ConcurrentHashMap<>();
    public final List<String> sentActors=Collections.synchronizedList(new ArrayList<>());
    record Step(String sub,String email,String scope,String nonce,String challenge,String hd,
        boolean emailVerified,String userInfoEmail,String userInfoHd,boolean userInfoEmailVerified){}
    public FakeGoogle()throws Exception{var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);var p=g.generateKeyPair();key=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)p.getPublic()).privateKey(p.getPrivate()).keyID("test-key").build();server.setDispatcher(this);server.start();}
    public String base(){return server.url("/").toString().replaceAll("/$","");}
    public Map<String,Object> properties(){var p=new HashMap<String,Object>();p.put("server.port",0);p.put("app.client-id","test-client");p.put("app.client-secret","test-secret");p.put("app.redirect-uri","{baseUrl}/login/oauth2/code/google");
        p.put("app.student-emails","student@example.com,otherstudent@example.com");p.put("app.lecturer-emails","lecturer@example.com,otherlecturer@example.com");p.put("app.allowed-workspace-domains","");
        p.put("app.auto-student-login",false);
        p.put("app.auto-domain-roles",false);
        p.put("app.folder-id","folder_123456");p.put("app.base-url","https://seminar.example.com");p.put("spring.datasource.url","jdbc:h2:mem:test-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");p.put("app.api-key","test-api-key");p.put("app.project-number","123456789");
        for(var e:Map.of("authorization-uri","/authorize","token-uri","/token","jwk-set-uri","/jwks","user-info-uri","/userinfo","issuer-uri","","revoke-uri","/revoke","drive-base","/drive/v3","upload-base","/upload/drive/v3","gmail-base","/gmail/v1").entrySet())p.put("app.google."+e.getKey(),base()+e.getValue());return p;
    }
    public void reset(){nextEmail="student@example.com";nextSub="student-sub";nextHd="";nextEmailVerified=true;nextUserInfoEmail=null;nextUserInfoHd="";nextUserInfoEmailVerified=true;wrongNonce=false;identityOnly=false;failShare=false;failAfterUpload=false;omitDownloadLink=false;mailError=0;submissions.clear();reviews.clear();sentRaw.clear();sentActors.clear();files.clear();viewers.clear();}
    @Override public MockResponse dispatch(RecordedRequest request){try{
        var url=request.getRequestUrl();String path=url.encodedPath();
        // Test runner controls. No such endpoints exist in the application or executable JAR.
        if(path.equals("/test/identity")){nextEmail=Objects.toString(url.queryParameter("email"),"student@example.com");nextSub=nextEmail.equals("student@example.com")?"student-sub":nextEmail.equals("lecturer@example.com")?"lecturer-sub":nextEmail.equals("otherstudent@example.com")?"other-student-sub":"other-lecturer-sub";return json(200,Map.of("ok",true));}
        if(path.equals("/test/stats"))return json(200,Map.of("submissions",submissions.size(),"reviews",reviews.size(),"sentRaw",sentRaw,"uploads",uploads.get()));
        if(path.equals("/authorize")){var step=new Step(nextSub,nextEmail,url.queryParameter("scope"),url.queryParameter("nonce"),url.queryParameter("code_challenge"),nextHd,nextEmailVerified,Objects.requireNonNullElse(nextUserInfoEmail,nextEmail),nextUserInfoHd,nextUserInfoEmailVerified);String code=UUID.randomUUID().toString();codes.put(code,step);
            return new MockResponse().setResponseCode(302).setHeader("Location",url.queryParameter("redirect_uri")+"?code="+code+"&state="+URLEncoder.encode(url.queryParameter("state"),StandardCharsets.UTF_8));}
        if(path.equals("/token")){var f=form(request.getBody().readUtf8());if("refresh_token".equals(f.get("grant_type"))){refreshes.incrementAndGet();var step=refresh.get(f.get("refresh_token"));if(step==null)return json(400,Map.of("error","invalid_grant"));String at="mock-access-"+UUID.randomUUID();access.put(at,step);return json(200,Map.of("access_token",at,"token_type","Bearer","expires_in",3600,"scope",step.scope()));}
            exchanges.incrementAndGet();var step=codes.remove(f.get("code"));if(step==null||f.get("code_verifier")==null)return json(400,Map.of("error","invalid_grant"));
            String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(f.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));if(!challenge.equals(step.challenge()))return json(400,Map.of("error","invalid_grant"));
            String scopes=identityOnly?"openid email profile":step.scope();var grant=new Step(step.sub(),step.email(),scopes,step.nonce(),step.challenge(),step.hd(),step.emailVerified(),step.userInfoEmail(),step.userInfoHd(),step.userInfoEmailVerified());String at="mock-access-"+UUID.randomUUID(),rt="mock-refresh-"+UUID.randomUUID();access.put(at,grant);refresh.put(rt,grant);
            return json(200,Map.of("access_token",at,"refresh_token",rt,"token_type","Bearer","expires_in",3600,"scope",scopes,"id_token",idToken(step)));}
        if(path.equals("/jwks"))return new MockResponse().setHeader("Content-Type","application/json").setBody(new JWKSet(key.toPublicJWK()).toString());
        if(path.equals("/userinfo")){var s=access.get(bearer(request));if(s==null)return json(401,Map.of("error","invalid_token"));var info=new HashMap<String,Object>(Map.of("sub",s.sub(),"email",s.userInfoEmail(),"email_verified",s.userInfoEmailVerified(),"name",s.email().startsWith("student")?"Nguyễn An":"Trần Bình"));if(!s.userInfoHd().isBlank())info.put("hd",s.userInfoHd());return json(200,info);}
        if(path.equals("/revoke")){revocations.incrementAndGet();var f=form(request.getBody().readUtf8());String token=f.get("token");var step=refresh.remove(token);if(step!=null)access.entrySet().removeIf(e->e.getValue().sub().equals(step.sub()));else access.remove(token);return json(200,Map.of());}
        var actor=access.get(bearer(request));if(actor==null)return json(401,Map.of("error","UNAUTHENTICATED"));
        if(path.startsWith("/drive/")||path.startsWith("/upload/")){if(!actor.scope().contains(Scopes.DRIVE))return json(403,Map.of("error","PERMISSION_DENIED"));
            if(path.endsWith("/files/folder_123456"))return json(200,Map.of("id","folder_123456","name","Minh chứng Seminar","mimeType","application/vnd.google-apps.folder","capabilities",Map.of("canAddChildren",true)));
            if(path.endsWith("/permissions")){
                String fileId=url.pathSegments().get(url.pathSegments().size()-2);
                if(request.getMethod().equals("GET"))return json(200,Map.of("permissions",viewers.containsKey(fileId)?List.of(Map.of("type","user","role","reader","emailAddress",viewers.get(fileId))):List.of()));
                if(failShare)return json(403,Map.of("error","SHARING_BLOCKED"));
                var j=mapper.readTree(request.getBody().readUtf8());viewers.put(fileId,j.path("emailAddress").asText());
                return json(200,Map.of("id","permission_test"));
            }
            if(path.startsWith("/drive/")){
                String query=Objects.toString(url.queryParameter("q"),"");
                return json(200,Map.of("files",files.entrySet().stream().filter(e->query.contains(e.getKey())).map(Map.Entry::getValue).toList()));
            }
            uploads.incrementAndGet();String id="file_"+UUID.randomUUID().toString().replace("-","");
            String body=request.getBody().readUtf8();int start=body.indexOf("\r\n\r\n")+4,end=body.indexOf("\r\n--",start);
            var metadata=mapper.readTree(body.substring(start,end));
            String attempt=metadata.path("appProperties").path("submissionId").asText();
            var file=new HashMap<String,Object>();file.put("id",id);file.put("name",metadata.path("name").asText());
            file.put("mimeType",metadata.path("mimeType").asText());file.put("size",1024);
            if(!omitDownloadLink)file.put("webContentLink","https://drive.google.com/uc?export=download&id="+id);
            files.put(attempt,file);
            if(failAfterUpload){failAfterUpload=false;return json(503,Map.of("error","AFTER_UPLOAD"));}
            return json(200,file);}
        if(path.endsWith("/users/me/messages/send")){if(!actor.scope().contains(Scopes.MAIL))return json(403,Map.of("error","PERMISSION_DENIED"));if(mailError>0)return json(mailError,Map.of("error","MAIL_ERROR"));
            String raw=mapper.readTree(request.getBody().readUtf8()).path("raw").asText();
            var mime=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties()),new java.io.ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
            if(!((jakarta.mail.internet.InternetAddress)mime.getFrom()[0]).getAddress().equals(actor.email()))return json(403,Map.of("error","WRONG_TOKEN_OWNER"));
            sentRaw.add(raw);sentActors.add(actor.email());return json(200,Map.of("id","mail_"+sentRaw.size()));}
        return json(404,Map.of("error","not_found"));
    }catch(Exception e){return json(500,Map.of("error",e.getClass().getSimpleName()));}}
    String bearer(RecordedRequest r){return Objects.toString(r.getHeader("Authorization"),"").replaceFirst("^Bearer ","");}
    String idToken(Step s)throws Exception{var claims=new JWTClaimsSet.Builder().issuer(base()).subject(s.sub()).audience("test-client").issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(3600)))
        .claim("nonce",wrongNonce?"wrong-nonce":s.nonce()).claim("email",s.email()).claim("email_verified",s.emailVerified()).claim("name",s.email().startsWith("student")?"Nguyễn An":"Trần Bình").build();
        if(!s.hd().isBlank())claims= new JWTClaimsSet.Builder(claims).claim("hd",s.hd()).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),claims);jwt.sign(new RSASSASigner(key));return jwt.serialize();}
    MockResponse json(int code,Object body){try{return new MockResponse().setResponseCode(code).setHeader("Content-Type","application/json;charset=UTF-8").setBody(mapper.writeValueAsString(body));}catch(Exception e){throw new RuntimeException(e);}}
    static Map<String,String> form(String body){var out=new HashMap<String,String>();for(var p:body.split("&")){var kv=p.split("=",2);out.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),kv.length<2?"":URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}return out;}
    @Override public void close()throws Exception{server.close();}
}
