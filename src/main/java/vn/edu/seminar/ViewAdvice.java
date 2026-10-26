package vn.edu.seminar;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@ControllerAdvice
public class ViewAdvice {
    private final AppSettings app; private final AccountPolicy policy; private final TokenService tokens; private final DriveAccessService access;
    public ViewAdvice(AppSettings app, AccountPolicy policy, TokenService tokens, DriveAccessService access) { this.app = app; this.policy = policy; this.tokens = tokens; this.access = access; }
    @ModelAttribute public void common(Model model, Authentication auth, HttpServletRequest request) {
        boolean signed = auth != null && auth.getPrincipal() instanceof OidcUser;
        model.addAttribute("signedIn", signed); model.addAttribute("credentialsReady", app.credentialsReady());
        model.addAttribute("resourcesReady", app.resourcesReady()); model.addAttribute("maxUploadMb", app.maxUploadMb());
        model.addAttribute("autoStudentLogin", app.autoStudentLogin());
        model.addAttribute("autoDomainRoles", app.autoDomainRoles());
        model.addAttribute("driveAccessMode", access.mode()); model.addAttribute("driveAccessReady", access.ready()); model.addAttribute("driveOwnerEmail", access.ownerEmail()); model.addAttribute("driveAdmin", signed && access.isAdmin((OidcUser) auth.getPrincipal()));
        model.addAttribute("pickerReady", !app.apiKey().isBlank() && !app.projectNumber().isBlank());
        model.addAttribute("lecturers", policy.lecturers()); model.addAttribute("currentPath", request.getRequestURI());
        for (var type : java.util.List.of("success", "error")) {
            var message = request.getSession().getAttribute("ui." + type);
            if (message != null) { model.addAttribute(type, message); request.getSession().removeAttribute("ui." + type); }
        }
        if (signed) {
            var user = (OidcUser) auth.getPrincipal();
            model.addAttribute("userName", user.getFullName() == null ? user.getEmail() : user.getFullName());
            model.addAttribute("userEmail", user.getEmail()); model.addAttribute("userSub", user.getSubject());
            model.addAttribute("role", policy.role(user).name());
            model.addAttribute("hasSubmit", tokens.has(auth, request, Scopes.DRIVE) && tokens.has(auth, request, Scopes.MAIL));
            model.addAttribute("hasReview", tokens.has(auth, request, Scopes.MAIL));
            model.addAttribute("authorizedFolder", request.getSession().getAttribute(SubmissionService.FOLDER));
        }
    }
}
