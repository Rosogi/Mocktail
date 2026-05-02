package com.rosogisoft.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.AntPathMatcher;

@Configuration
public class BeanConfig {

    @Bean
    public AntPathMatcher antPathMatcher () {
        AntPathMatcher matcher = new AntPathMatcher();
        matcher.setCaseSensitive(false);
        return matcher;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
