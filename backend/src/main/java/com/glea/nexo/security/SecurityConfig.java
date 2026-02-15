package com.glea.nexo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    // MVP: sin JWT todavía. Permitimos ingest y health para avanzar.
    return http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/api/ping").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/ingest/**").permitAll()
            .anyRequest().permitAll()
        )
        .httpBasic(Customizer.withDefaults())
        .build();
  }
}

