package com.rosogisoft.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

@Configuration
public class BeanConfig {

    @Bean
    public AntPathMatcher antPathMatcher () {
        AntPathMatcher matcher = new AntPathMatcher();
        matcher.setCaseSensitive(false);
        return matcher;
    }
}
