package vn.edu.seminar;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
@Service
public class TokenService {
    private final SessionTokenRepository vault;private final RestClientRefreshTokenTokenResponseClient refresh;private final AuditTape tape;
    public TokenService(SessionTokenRepository vault,RestClientRefreshTokenTokenResponseClient refresh,AuditTape tape){this.vault=vault;this.refresh=refresh;this.tape=tape;}
    public OAuth2AuthorizedClient client(Authentication a,HttpServletRequest r){return vault.loadAuthorizedClient("google",a,r);}
    public Set<String> scopes(Authentication a,HttpServletRequest r){var c=client(a,r);return c==null?Set.of():c.getAccessToken().getScopes();}
    public boolean has(Authentication a,HttpServletRequest r,String scope){return Scopes.includes(scopes(a,r),scope);}
    public String require(Authentication a,HttpServletRequest r,String purpose,String... scopes){
        var c=client(a,r);if(c==null)throw new Scopes.Missing(purpose);
        for(var s:scopes)if(!Scopes.includes(c.getAccessToken().getScopes(),s))throw new Scopes.Missing(purpose);
        if(c.getAccessToken().getExpiresAt()==null||!c.getAccessToken().getExpiresAt().isAfter(Instant.now().plusSeconds(45)))c=refresh(a,r);
        for(var s:scopes)if(!Scopes.includes(c.getAccessToken().getScopes(),s))throw new Scopes.Missing(purpose);
        return c.getAccessToken().getTokenValue();
    }
    public OAuth2AuthorizedClient refresh(Authentication a,HttpServletRequest r){synchronized(r.getSession()){
        var old=client(a,r);if(old==null||old.getRefreshToken()==null)throw new IllegalArgumentException("Chưa có refresh token. Cấp quyền lại bằng nút kết nối (offline + consent).");
        try{var ans=refresh.getTokenResponse(new OAuth2RefreshTokenGrantRequest(old.getClientRegistration(),old.getAccessToken(),old.getRefreshToken()));
            var c=new OAuth2AuthorizedClient(old.getClientRegistration(),old.getPrincipalName(),ans.getAccessToken(),ans.getRefreshToken()==null?old.getRefreshToken():ans.getRefreshToken());
            vault.saveRefreshed(c,r);tape.add(r,"POST token endpoint · refresh_token","Thành công · hạn mới "+c.getAccessToken().getExpiresAt());return c;
        }catch(OAuth2AuthorizationException e){if("invalid_grant".equals(e.getError().getErrorCode()))vault.removeAuthorizedClient("google",a,r,null);tape.add(r,"Refresh token",e.getError().getErrorCode());throw new IllegalArgumentException("Google từ chối làm mới token. Hãy kết nối Google lại.");}
    }}
}
