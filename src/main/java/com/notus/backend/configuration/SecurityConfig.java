package com.notus.backend.configuration;

import com.notus.backend.auth.ClerkAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final ClerkAuthFilter clerkAuthFilter;

    public SecurityConfig(ClerkAuthFilter clerkAuthFilter) {
        this.clerkAuthFilter = clerkAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/health", "/api/test", "/actuator/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/group-invitations/preview").permitAll()
                        .requestMatchers("/api/me").authenticated()
                        .requestMatchers("/api/admin/teacher-codes/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/group-invitations/accept").authenticated()
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .requestMatchers("/api/history/student").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/attendance/check-in").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/quiz-assignments/*/take").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/quiz-assignments/*/my-answers").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/quiz-assignments/*/submit").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/quiz-assignments/active-for-session").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/quiz-assignments/new-reviews").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/quiz-assignments/submissions/*/mark-seen").hasRole("STUDENT")
                        .requestMatchers("/api/history/teacher", "/api/history/teacher/**", "/api/schedule/teacher/**").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/schedule").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.PUT, "/api/schedule/**").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/schedule/**").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/attendance/sessions").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/sessions/*/qr").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/sessions/*/records").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/sessions/*/summary").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/attendance/sessions/*/close").hasRole("TEACHER")
                        .requestMatchers("/api/teacher/groups/**", "/api/teacher/analytics/**", "/api/teacher/realtime/**", "/api/teacher/notifications", "/api/teacher/activity", "/api/teacher/ai-keys/**").hasRole("TEACHER")
                        .requestMatchers("/api/quiz/**").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/quiz-assignments").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/quiz-assignments/my").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/quiz-assignments/session/*/results").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/quiz-assignments/*/results").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/quiz-assignments/*/activate").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/quiz-assignments/*/deactivate").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/quiz-assignments/submissions/*/answers").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/quiz-assignments/submissions/*/review").hasRole("TEACHER")
                        .requestMatchers("/api/quiz/**").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                );

        http.addFilterBefore(clerkAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
