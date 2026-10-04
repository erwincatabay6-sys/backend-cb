package com.cellbank.config;


import com.cellbank.auth.AuthRateLimitFilter;
import com.cellbank.auth.AuthRateLimitService;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
            SessionRegistry sessionRegistry,
            AuthRateLimitService authRateLimitService) throws Exception {

        http
                .csrf(Customizer.withDefaults())
                
                .addFilterBefore(
                        new AuthRateLimitFilter(authRateLimitService),
                        UsernamePasswordAuthenticationFilter.class
                )

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
                                "/api/auth/verify-email",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        )
                               
                        
                        .permitAll()

                        .requestMatchers("/api/users/**")
                        .hasRole("ADMIN")

                        // Customer viewing
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers",
                                "/api/customers/{customerId}"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )

                        // Customer registration
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "FRONT_DESK"
                        )

                        // Customer editing
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/customers/{customerId}"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "FRONT_DESK"
                        )

                     // Device viewing
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers/{customerId}/devices",
                                "/api/customers/{customerId}/devices/{deviceId}"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )

                        // Device registration
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers/{customerId}/devices"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "FRONT_DESK"
                        )

                        // Device editing
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/customers/{customerId}/devices/{deviceId}"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "FRONT_DESK"
                        )
                        
                        // Repair viewing
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/repairs",
                                "/api/repairs/technicians",
                                "/api/repairs/{repairId}",
                                "/api/repairs/{repairId}/status-history"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )

                        // Repair creation
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/repairs"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "FRONT_DESK"
                        )
                        
                        // Customer and device repair history
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers/{customerId}/repairs",
                                "/api/customers/{customerId}/devices/{deviceId}/repairs"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "TECHNICIAN",
                                "FRONT_DESK"
                        )
                        
                        // Technician reassignment
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/repairs/{repairId}/assignment"
                        )
                        .hasAnyRole(
                                "ADMIN",
                                "FRONT_DESK"
                        )

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
