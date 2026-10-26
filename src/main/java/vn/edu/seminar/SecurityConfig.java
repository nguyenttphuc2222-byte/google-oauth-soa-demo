package vn.edu.seminar;
import jakarta.servlet.DispatcherType;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.client.RestClient;
@Configuration
public class SecurityConfig {
    @Bean ClientRegistrationRepository registrations(AppSettings a){var g=a.google();return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("google")
        .clientName("Google").clientId(a.clientId()).clientSecret(a.clientSecret()).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(a.redirectUri()).scope(Scopes.IDENTITY)
        .authorizationUri(g.authorizationUri()).tokenUri(g.tokenUri()).userInfoUri(g.userInfoUri()).userNameAttributeName("sub")
        .jwkSetUri(g.jwkSetUri()).issuerUri(g.issuerUri()).build());}
    static RestClient tokenRestClient(){var f=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());f.setReadTimeout(Duration.ofSeconds(25));
        return RestClient.builder().requestFactory(f).messageConverters(c->{c.clear();c.add(new FormHttpMessageConverter());c.add(new OAuth2AccessTokenResponseHttpMessageConverter());}).defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build();}
    @Bean RestClientAuthorizationCodeTokenResponseClient codeClient(){var c=new RestClientAuthorizationCodeTokenResponseClient();c.setRestClient(tokenRestClient());return c;}
    @Bean RestClientRefreshTokenTokenResponseClient refreshClient(){var c=new RestClientRefreshTokenTokenResponseClient();c.setRestClient(tokenRestClient());return c;}
    @Bean OidcUserService users(AccountPolicy policy){return new OidcUserService(){@Override public OidcUser loadUser(org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest request){
        var u=super.loadUser(request);try{var authorities=new HashSet<GrantedAuthority>(u.getAuthorities());authorities.add(new SimpleGrantedAuthority("ROLE_"+policy.role(u)));
            return new DefaultOidcUser(authorities,u.getIdToken(),"sub");
        }catch(AccessDeniedException e){throw new OAuth2AuthenticationException(new OAuth2Error("account_not_allowed"));}
    }};}
    @Bean OAuth2AuthorizationRequestResolver resolver(ClientRegistrationRepository clients,SessionTokenRepository vault,AppSettings app,AuditTape tape){
        var d=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");d.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return new OAuth2AuthorizationRequestResolver(){
            @Override public OAuth2AuthorizationRequest resolve(jakarta.servlet.http.HttpServletRequest r){return customize(d.resolve(r),r);}
            @Override public OAuth2AuthorizationRequest resolve(jakarta.servlet.http.HttpServletRequest r,String id){return customize(d.resolve(r,id),r);}
            private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest original,jakarta.servlet.http.HttpServletRequest r){
                if(original==null)return null;if(!app.credentialsReady())throw new OAuth2AuthenticationException(new OAuth2Error("configuration_required"));
                var pending=(GrantState.Pending)r.getSession().getAttribute(GrantState.PENDING);var auth=SecurityContextHolder.getContext().getAuthentication();
                Set<String> previous=Set.of();String purpose=pending==null?"login":pending.purpose();
                if(pending!=null){if(auth==null||!pending.subject().equals(auth.getName())||pending.at().plusSeconds(600).isBefore(Instant.now()))throw new OAuth2AuthenticationException(new OAuth2Error("grant_expired"));
                    var old=vault.loadAuthorizedClient("google",auth,r);if(old!=null)previous=old.getAccessToken().getScopes();}
                var scopes=Scopes.requested(previous,purpose);var built=OAuth2AuthorizationRequest.from(original).scopes(scopes).additionalParameters(p->{
                    p.put("include_granted_scopes","true");p.put("access_type","offline");p.put("prompt",pending==null?"select_account":"consent");
                    if(pending!=null&&auth.getPrincipal() instanceof OidcUser u)p.put("login_hint",u.getEmail());}).build();
                r.getSession().setAttribute(GrantState.LAST,new GrantState.Snapshot(purpose,Set.copyOf(scopes),built.getState()!=null,"S256".equals(built.getAdditionalParameters().get("code_challenge_method")),built.getAdditionalParameters().containsKey("nonce")));
                tape.add(r,"OAuth authorize · "+purpose,"state + PKCE S256; yêu cầu "+scopes.size()+" scopes");return built;
            }
        };
    }
    @Bean SecurityFilterChain security(HttpSecurity http,OidcUserService users,OAuth2AuthorizationRequestResolver resolver,
            SessionTokenRepository vault,RestClientAuthorizationCodeTokenResponseClient codeClient,AuditTape tape,LecturerDirectory directory,DriveAccessService driveAccess)throws Exception{
        var contexts=new HttpSessionSecurityContextRepository();
        var requestCache=new org.springframework.security.web.savedrequest.HttpSessionRequestCache();
        requestCache.setRequestMatcher(r->"GET".equals(r.getMethod())&&r.getRequestURI().matches("/submissions/[a-f0-9-]{36}"));
        http.requestCache(c->c.requestCache(requestCache)).securityContext(s->s.securityContextRepository(contexts)).authorizeHttpRequests(a->a
            .dispatcherTypeMatchers(DispatcherType.ERROR,DispatcherType.FORWARD).permitAll()
            .requestMatchers("/","/login","/health","/static/**","/error","/oauth2/**","/login/**").permitAll()
            .requestMatchers(HttpMethod.POST,"/submissions","/submissions/retry/**").hasRole("STUDENT")
            .requestMatchers("/submit","/api/picker/**").hasRole("STUDENT").requestMatchers("/reviews/**").hasRole("LECTURER").anyRequest().authenticated())
            .oauth2Login(o->o.loginPage("/login").authorizedClientRepository(vault).authorizationEndpoint(e->e.authorizationRequestResolver(resolver))
                .tokenEndpoint(e->e.accessTokenResponseClient(codeClient)).userInfoEndpoint(e->e.oidcUserService(users))
                .successHandler((r,response,auth)->{directory.remember((OidcUser)auth.getPrincipal());var p=(GrantState.Pending)r.getSession().getAttribute(GrantState.PENDING);r.getSession().removeAttribute(GrantState.PENDING);
                    r.getSession().setAttribute("ui.success",p==null?"Đăng nhập Google thành công.":"Đã kết nối Google. Bạn có thể tiếp tục thao tác.");
                    driveAccess.onLogin((OidcUser)auth.getPrincipal(),auth,r);
                    tape.add(r,"Google callback","Xác thực ID token; đổi authorization code bằng PKCE");String target=p==null?"/dashboard":p.returnTo();
                    var saved=requestCache.getRequest(r,response);
                    if(p==null&&saved!=null){String path=java.net.URI.create(saved.getRedirectUrl()).getPath();if(path.matches("/submissions/[a-f0-9-]{36}"))target=path;}
                    requestCache.removeRequest(r,response);response.sendRedirect(target);})
                .failureHandler((r,response,ex)->{var s=r.getSession();var p=(GrantState.Pending)s.getAttribute(GrantState.PENDING);s.removeAttribute(GrantState.PENDING);
                    if(p!=null&&p.previous()!=null){var c=SecurityContextHolder.createEmptyContext();c.setAuthentication(p.previous());SecurityContextHolder.setContext(c);contexts.saveContext(c,r,response);}
                    String code=ex instanceof OAuth2AuthenticationException e?e.getError().getErrorCode():"login_failed";
                    String message=switch(code){case "account_changed"->"Tài khoản cấp quyền khác tài khoản đang đăng nhập. Hãy chọn đúng tài khoản.";
                        case "account_not_allowed"->"Cần email TDTU đã xác minh thuộc Workspace được cho phép. Kiểm tra tài khoản và cấu hình .env.";
                        case "access_denied"->"Bạn đã từ chối cấp quyền Google. Chức năng cần quyền đó chưa dùng được.";
                        case "configuration_required"->"Điền GOOGLE_CLIENT_ID và GOOGLE_CLIENT_SECRET trong .env rồi khởi động lại.";
                        default->"Không hoàn tất được OAuth ("+code.replaceAll("[^a-zA-Z0-9_/-]","")+"). Hãy kết nối lại.";};
                    s.setAttribute("ui.error",message);tape.add(r,"OAuth bị từ chối",code);response.sendRedirect(p==null?"/login":p.returnTo());}))
            .exceptionHandling(e->e.accessDeniedHandler((r,response,ex)->{response.setStatus(403);
                if("XMLHttpRequest".equals(r.getHeader("X-Requested-With"))){response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"error\":\"Backend đã chặn yêu cầu. Kiểm tra vai trò hoặc tải lại trang để có CSRF token mới.\"}");}
                else r.getRequestDispatcher("/error/forbidden").forward(r,response);}))
            .logout(l->l.logoutUrl("/logout").logoutSuccessUrl("/login?logout").invalidateHttpSession(true).deleteCookies("JSESSIONID"));
        return http.build();
    }
}
