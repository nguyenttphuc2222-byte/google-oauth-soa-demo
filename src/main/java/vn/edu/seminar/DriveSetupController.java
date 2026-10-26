package vn.edu.seminar;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class DriveSetupController {
    public record Connect(String folderId, boolean acknowledgeShared) {}
    private final AppSettings app;
    private final DriveAccessService access;
    private final TokenService tokens;
    public DriveSetupController(AppSettings app,DriveAccessService access,TokenService tokens){this.app=app;this.access=access;this.tokens=tokens;}
    @GetMapping("/drive-setup") public String setup(@AuthenticationPrincipal OidcUser user,Authentication authentication,Model model,HttpServletRequest request){
        access.requireAdmin(user);
        model.addAttribute("ownerHasDrive",tokens.has(authentication,request,Scopes.DRIVE));
        model.addAttribute("ownerHasRefresh",tokens.client(authentication,request)!=null && tokens.client(authentication,request).getRefreshToken()!=null);
        model.addAttribute("driveKeyReady",access.keyReady());model.addAttribute("driveRootId",app.folderId());return "drive-setup";
    }
    @PostMapping("/api/drive-setup/credentials") @ResponseBody
    public ResponseEntity<?> credentials(@AuthenticationPrincipal OidcUser user,Authentication authentication,HttpServletRequest request){
        access.requireAdmin(user);
        if(app.apiKey().isBlank()||app.projectNumber().isBlank())throw new IllegalArgumentException("Chưa cấu hình API key / project number cho Google Picker.");
        String token=tokens.require(authentication,request,"drive_admin",Scopes.DRIVE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("accessToken",token,"apiKey",app.apiKey(),"appId",app.projectNumber(),"folderId",app.folderId()));
    }
    @PostMapping("/api/drive-setup/connect") @ResponseBody
    public Map<String,String> connect(@RequestBody Connect body,@AuthenticationPrincipal OidcUser user,Authentication authentication,HttpServletRequest request){
        access.connect(body.folderId(),body.acknowledgeShared(),user,authentication,request);
        request.getSession().setAttribute("ui.success","Đã kết nối chủ thư mục. Backend có thể chuẩn bị quyền Drive cho người dùng mới.");
        return Map.of("redirect","/drive-setup");
    }
    @PostMapping("/drive-setup/disconnect") public String disconnect(@AuthenticationPrincipal OidcUser user,HttpServletRequest request){
        access.disconnect(user,request);
        request.getSession().setAttribute("ui.success","Đã ngừng tự cấp quyền và xóa refresh token đã lưu trên server. Các quyền Drive đã cấp vẫn giữ nguyên.");return "redirect:/drive-setup";
    }
}
