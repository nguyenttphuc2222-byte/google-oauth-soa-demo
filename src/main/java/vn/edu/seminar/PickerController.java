package vn.edu.seminar;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/picker")
public class PickerController {
    public record Choice(String id){}
    private final AppSettings app;private final AccountPolicy policy;private final TokenService tokens;private final DriveClient drive;private final DriveAccessService access;
    public PickerController(AppSettings app,AccountPolicy policy,TokenService tokens,DriveClient drive,DriveAccessService access){this.app=app;this.policy=policy;this.tokens=tokens;this.drive=drive;this.access=access;}
    @PostMapping("/credentials") public ResponseEntity<?> credentials(@AuthenticationPrincipal OidcUser u,Authentication a,HttpServletRequest r){
        policy.require(u,AccountPolicy.Role.STUDENT);if(app.apiKey().isBlank()||app.projectNumber().isBlank())throw new IllegalArgumentException("Điền GOOGLE_API_KEY và GOOGLE_PROJECT_NUMBER để bật Picker.");
        String token=tokens.require(a,r,"submit",Scopes.DRIVE);var target=access.target(u,r);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("accessToken",token,"apiKey",app.apiKey(),"appId",app.projectNumber(),"folderId",target.id()));
    }
    @PostMapping("/folder") public Map<String,String> folder(@RequestBody Choice choice,@AuthenticationPrincipal OidcUser u,Authentication a,HttpServletRequest r){
        policy.require(u,AccountPolicy.Role.STUDENT);String t=tokens.require(a,r,"submit",Scopes.DRIVE);var target=access.target(u,r);var f=drive.verifyFolder(choice.id(),target.id(),t,r);r.getSession().setAttribute(SubmissionService.FOLDER,f);return Map.of("name",f.name(),"redirect","/submit");
    }
    @PostMapping("/check") public Map<String,String> check(@AuthenticationPrincipal OidcUser u,Authentication a,HttpServletRequest r){policy.require(u,AccountPolicy.Role.STUDENT);var target=access.target(u,r);return folder(new Choice(target.id()),u,a,r);}
}
