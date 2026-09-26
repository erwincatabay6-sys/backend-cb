package com.cellbank.config;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionRegistry sessionRegistry) throws Exception {

        http
                .csrf(Customizer.withDefaults())

                .httpBasic(AbstractHttpConfigurer::disable)

                .requestCache(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event ->
                                event.getResponse().setStatus(
                                        HttpStatus.UNAUTHORIZED.value()
                                )
                        )
                )

                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/health",
                                "/api/auth/csrf"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )
                        
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/auth/me"
                        )
                        
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/change-password"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )
                        
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me/profile-image"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/me/profile-image"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )
                        
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me/email-verification"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )
                        
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/me/email-verification"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/verify-email"
                        )
                               
                        
                        .permitAll()

                        .requestMatchers("/api/users/**")
                        .hasRole("ADMIN")

                        .anyRequest()
                        .denyAll()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("identifier")
                        .passwordParameter("password")

                        .successHandler((request, response, authentication) ->
                                response.setStatus(
                                        HttpStatus.NO_CONTENT.value()
                                )
                        )

                        .failureHandler((request, response, exception) ->
                                response.setStatus(
                                        HttpStatus.UNAUTHORIZED.value()
                                )
                        )

                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")

                        .logoutSuccessHandler(
                                (request, response, authentication) ->
                                        response.setStatus(
                                                HttpStatus.NO_CONTENT.value()
                                        )
                        )

                        .permitAll()
                )

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED
                                )
                        )

                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        response.setStatus(
                                                HttpStatus.FORBIDDEN.value()
                                        )
                        )
                );

        return http.build();
    }
}
