package com.example.googleoauthspring;

import java.util.HashSet;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class TdtuOidcUserService extends OidcUserService {

    @Override
    public OidcUser loadUser(OidcUserRequest request)
            throws OAuth2AuthenticationException {
        OidcUser user = super.loadUser(request);
        final String role;
        try {
            role = TdtuRolePolicy.roleFor(
                    user.getEmail(), user.getEmailVerified(),
                    user.getClaimAsString("hd"));
        } catch (IllegalArgumentException exception) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("tdtu_account_required"),
                    "Please use your TDTU Google Workspace account");
        }

        HashSet<GrantedAuthority> authorities = new HashSet<>(user.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        if (user.getUserInfo() == null) {
            return new DefaultOidcUser(authorities, user.getIdToken());
        }
        return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo());
    }
}
