package vn.edu.seminar;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
@Controller
public class OAuthController {
    private final AppSettings app;private final AccountPolicy policy;private final TokenService tokens;private final SessionTokenRepository vault;private final GoogleHttp http;private final AuditTape tape;private final DriveAccessService access;
    public OAuthController(AppSettings app,AccountPolicy policy,TokenService tokens,SessionTokenRepository vault,GoogleHttp http,AuditTape tape,DriveAccessService access){this.app=app;this.policy=policy;this.tokens=tokens;this.vault=vault;this.http=http;this.tape=tape;this.access=access;}
    @PostMapping("/oauth/connect/{purpose}") public String connect(@PathVariable String purpose,@RequestParam(defaultValue="/oauth")String returnTo,@AuthenticationPrincipal OidcUser u,Authentication a,HttpServletRequest r){
        var role=policy.role(u);if(purpose.equals("drive_admin")){access.requireAdmin(u);}else if(!Set.of("submit","review").contains(purpose)||purpose.equals("submit")&&role!=AccountPolicy.Role.STUDENT||purpose.equals("review")&&role!=AccountPolicy.Role.LECTURER)throw new AccessDeniedException("Vai trò không được xin nhóm quyền này.");
        if(!Set.of("/oauth","/dashboard","/submit","/drive-setup").contains(returnTo)&&!returnTo.matches("/submissions/[a-f0-9-]{36}"))returnTo="/oauth";
        r.getSession().setAttribute(GrantState.PENDING,new GrantState.Pending(purpose,u.getSubject(),returnTo,Instant.now(),a));return "redirect:/oauth2/authorization/google";
    }
    @GetMapping("/oauth") public String lab(Authentication a,Model m,HttpServletRequest r)throws Exception{
        var c=tokens.client(a,r);m.addAttribute("tokenPresent",c!=null);m.addAttribute("hasRefresh",c!=null&&c.getRefreshToken()!=null);m.addAttribute("scopes",tokens.scopes(a,r).stream().sorted().toList());
        m.addAttribute("fingerprint",c==null?"—":HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getAccessToken().getTokenValue().getBytes(StandardCharsets.UTF_8))).substring(0,16));
        m.addAttribute("issuedAt",c==null?null:c.getAccessToken().getIssuedAt());m.addAttribute("expiresAt",c==null?null:c.getAccessToken().getExpiresAt());
        m.addAttribute("grant",r.getSession().getAttribute(GrantState.LAST));m.addAttribute("events",List.copyOf(tape.events(r)));return "oauth";
    }
    @PostMapping("/oauth/refresh") public String refresh(Authentication a,HttpServletRequest r){tokens.refresh(a,r);r.getSession().setAttribute("ui.success","Đã gọi Google token endpoint để refresh thật.");return "redirect:/oauth";}
    @PostMapping("/oauth/revoke") public String revoke(@AuthenticationPrincipal OidcUser u,Authentication a,HttpServletRequest r){var c=tokens.client(a,r);if(c!=null){
        var form=new LinkedMultiValueMap<String,String>();form.add("token",c.getRefreshToken()==null?c.getAccessToken().getTokenValue():c.getRefreshToken().getTokenValue());
        http.call(HttpMethod.POST,URI.create(app.google().revokeUri()),null,form,MediaType.APPLICATION_FORM_URLENCODED,"Google · revoke grant",r);
        vault.removeAuthorizedClient("google",a,r,null);r.getSession().removeAttribute(SubmissionService.FOLDER);access.clearIfOwner(u,r);
    }r.getSession().setAttribute("ui.success","Đã thu hồi quyền ở Google và xóa token của phiên. Đăng nhập app và quyền gọi API là hai trạng thái khác nhau.");return "redirect:/oauth";}
}
