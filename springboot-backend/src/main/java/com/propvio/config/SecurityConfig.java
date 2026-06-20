package com.propvio.config;

import com.propvio.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — same as Node.js open routes
                .requestMatchers("/api/user/login",
                                 "/api/user/register",
                                 "/api/user/verify/**",
                                 "/api/user/forgot",
                                 "/api/user/reset/**",
                                 "/api/user/admin").permitAll()

                // Public product/property reads
                .requestMatchers(HttpMethod.GET, "/api/product/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/properties/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/news/**").permitAll()

                // AI endpoints — public (calculator, search, trends, key validation)
                .requestMatchers("/api/ai/calculate-price",
                                 "/api/ai/supported-cities",
                                 "/api/ai/search",
                                 "/api/ai/validate-keys").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/locations/**").permitAll()

                // Contact form — public
                .requestMatchers("/api/forms/submit").permitAll()

                // Appointments — allow guest bookings without auth
                .requestMatchers(HttpMethod.POST, "/api/appointments", "/api/appointments/schedule").permitAll()

                // Health / actuator / error page
                .requestMatchers("/actuator/**", "/api/health/**", "/error").permitAll()

                // Everything else needs JWT
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
