package com.sunriseletting.web.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.ViewResolver;

/**
 * CSRF protection on every POST, with the token kept in the session; the Property Manager area for
 * Property Managers only, who must replace a temporary password before using it, and every other current page
 * public; and no default login page, logout endpoint or HTTP Basic, because web has its own account pages.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    /** The Property Manager area. This also matches {@code /manage} itself. */
    private static final String MANAGE = "/manage/**";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
            ObjectProvider<ExpiredFormResponder> expiredFormResponders, ObjectProvider<RememberMeLogin> rememberMeLogin,
            SignedInMarker signedInMarker, @Qualifier("thymeleafViewResolver") ViewResolver viewResolver) {
        rememberMeLogin.ifAvailable(login -> http.addFilterBefore(new RememberMeFilter(login), AnonymousAuthenticationFilter.class));
        SignInEntryPoint signInEntryPoint = new SignInEntryPoint(signedInMarker);
        RequestMatcher managerArea = PathPatternRequestMatcher.withDefaults().matcher(MANAGE);
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(MANAGE).hasAuthority(AccountRole.PROPERTY_MANAGER.authority())
                        .anyRequest().permitAll())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(signInEntryPoint)
                        .accessDeniedHandler(new ExpiredFormAccessDeniedHandler(expiredFormResponders.orderedStream().toList(),
                                signInEntryPoint, signedInMarker, managerArea, viewResolver)))
                .addFilterBefore(new SignedInUserFilter(), AnonymousAuthenticationFilter.class)
                .addFilterAfter(new TemporaryPasswordFilter(managerArea), AuthorizationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    SiteCookies siteCookies(@Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        return new SiteCookies(secure);
    }

    @Bean
    SignedInMarker signedInMarker(SiteCookies cookies) {
        return new SignedInMarker(cookies);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    SignInService signInService(SecurityContextRepository securityContextRepository, ObjectProvider<SignInListener> listeners) {
        return new SignInService(securityContextRepository, listeners.orderedStream().toList());
    }

    @Bean
    SignOutService signOutService(SecurityContextRepository securityContextRepository, ObjectProvider<SignOutListener> listeners) {
        return new SignOutService(securityContextRepository, listeners.orderedStream().toList());
    }

    @Bean
    ExpiredFormResponder signOutExpiredFormResponder(SignOutService signOutService) {
        return new SignOutExpiredFormResponder(signOutService);
    }

    @Bean
    ExpiredFormResponder keepAliveExpiredFormResponder() {
        return new KeepAliveExpiredFormResponder();
    }
}
