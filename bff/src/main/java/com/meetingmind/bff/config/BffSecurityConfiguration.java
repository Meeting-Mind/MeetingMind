package com.meetingmind.bff.config;

import com.meetingmind.bff.auth.BffAbsoluteSessionExpiryFilter;
import com.meetingmind.bff.auth.BffAuthErrorWriter;
import com.meetingmind.bff.tokenvault.TokenVault;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BffSessionLifetimePolicy.class)
public class BffSecurityConfiguration {

    @Bean
    SecurityFilterChain bffSecurityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            TokenVault tokenVault,
            Clock tokenVaultClock) throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                        "/actuator/health/**",
                                        "/api/v1/auth/csrf",
                                        "/api/v1/auth/signup",
                                        "/api/v1/auth/login",
                                        "/api/v1/auth/google",
                                        "/api/v1/auth/session",
                                        "/api/v1/auth/logout",
                                        "/api/v1/auth/password-reset-requests",
                                        "/api/v1/auth/password-resets")
                                .permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionAuthenticationStrategy(sessionAuthenticationStrategy))
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .headers(Customizer.withDefaults())
                .addFilterAfter(
                        new BffAbsoluteSessionExpiryFilter(tokenVault, tokenVaultClock),
                        SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setParameterName("_csrf");
        return repository;
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> BffAuthErrorWriter.writeSessionInvalid(response);
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> response.sendError(HttpStatus.FORBIDDEN.value());
    }
}
