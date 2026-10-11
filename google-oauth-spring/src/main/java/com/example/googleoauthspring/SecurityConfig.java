package com.example.googleoauthspring;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public TdtuOidcUserService tdtuOidcUserService() {
        return new TdtuOidcUserService();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Environment environment,
            SecurityContextRepository contextRepository,
            TdtuOidcUserService userService) throws Exception {

        boolean demoEnabled = environment.acceptsProfiles(
                Profiles.of("demo"));

        http
            .securityContext(context -> context
                .securityContextRepository(contextRepository)
            )
            .authorizeHttpRequests(auth -> {
                auth.dispatcherTypeMatchers(
                        DispatcherType.ERROR,
                        DispatcherType.FORWARD
                ).permitAll();

                if (demoEnabled) {
                    auth.requestMatchers(
                            "/demo", "/demo/login"
                    ).permitAll();
                } else {
                    auth.requestMatchers(
                            "/demo", "/demo/login"
                    ).denyAll();
                }

                auth.requestMatchers(
                        "/", "/login", "/css/**",
                        "/oauth2/**", "/login/**"
                    ).permitAll()

                    .requestMatchers(
                        HttpMethod.POST, "/students/me"
                    ).hasRole("STUDENT")

                    .requestMatchers(
                        HttpMethod.GET, "/students/**"
                    ).hasRole("LECTURER")

                    .requestMatchers("/students/**").denyAll()

                    .requestMatchers("/home")
                        .hasAnyRole("STUDENT", "LECTURER")

                    .anyRequest().authenticated();
            })
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .userInfoEndpoint(info -> info
                    .oidcUserService(userService)
                )
                .failureUrl("/login?error")
                .defaultSuccessUrl("/home", true)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
            );

        return http.build();
    }
}