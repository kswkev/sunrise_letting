package com.sunriseletting.web.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * CSRF protection on every POST, with the token kept in the session; all current pages public; and no
 * default login page, logout endpoint or HTTP Basic, because web has its own account pages.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
            ObjectProvider<ExpiredFormResponder> expiredFormResponders, ObjectProvider<RememberMeLogin> rememberMeLogin) {
        rememberMeLogin.ifAvailable(login -> http.addFilterBefore(new RememberMeFilter(login), AnonymousAuthenticationFilter.class));
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(
                        new ExpiredFormAccessDeniedHandler(expiredFormResponders.orderedStream().toList())))
                .addFilterBefore(new SignedInUserFilter(), AnonymousAuthenticationFilter.class)
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
}
