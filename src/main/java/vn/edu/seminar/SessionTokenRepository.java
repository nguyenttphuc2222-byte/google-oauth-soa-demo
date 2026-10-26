package vn.edu.seminar;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.Collections;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.*;
/** Server-side token store. Official Picker receives a short-lived access token through a dedicated CSRF-protected endpoint. */
@Component
public class SessionTokenRepository implements OAuth2AuthorizedClientRepository {
    private static final String KEY=SessionTokenRepository.class.getName(),OWNER=KEY+".owner";
    @Override @SuppressWarnings("unchecked") public <T extends OAuth2AuthorizedClient>T loadAuthorizedClient(String id,Authentication auth,HttpServletRequest r){
        var s=r.getSession(false);if(s==null||auth==null)return null;var c=(OAuth2AuthorizedClient)s.getAttribute(KEY);
        return c!=null&&c.getClientRegistration().getRegistrationId().equals(id)&&c.getPrincipalName().equals(auth.getName())?(T)c:null;
    }
    @Override public void saveAuthorizedClient(OAuth2AuthorizedClient c,Authentication auth,HttpServletRequest r,HttpServletResponse response){
        var s=r.getSession();var pending=(GrantState.Pending)s.getAttribute(GrantState.PENDING);
        if(pending!=null&&(!pending.subject().equals(c.getPrincipalName())||pending.at().plusSeconds(600).isBefore(Instant.now())))throw new OAuth2AuthenticationException(new OAuth2Error("account_changed"));
        var old=(OAuth2AuthorizedClient)s.getAttribute(KEY);var owner=s.getAttribute(OWNER);
        if(owner!=null&&!owner.equals(c.getPrincipalName()))for(var n:Collections.list(s.getAttributeNames()))if(n.startsWith("seminar.")&&!n.equals(GrantState.PENDING)&&!n.equals(GrantState.LAST))s.removeAttribute(n);
        if(c.getRefreshToken()==null&&old!=null&&old.getPrincipalName().equals(c.getPrincipalName()))c=new OAuth2AuthorizedClient(c.getClientRegistration(),c.getPrincipalName(),c.getAccessToken(),old.getRefreshToken());
        s.setAttribute(KEY,c);s.setAttribute(OWNER,c.getPrincipalName());
    }
    public void saveRefreshed(OAuth2AuthorizedClient c,HttpServletRequest r){r.getSession().setAttribute(KEY,c);}
    @Override public void removeAuthorizedClient(String id,Authentication a,HttpServletRequest r,HttpServletResponse response){if(r.getSession(false)!=null)r.getSession().removeAttribute(KEY);}
}
