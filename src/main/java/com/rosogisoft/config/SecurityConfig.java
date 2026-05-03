package com.rosogisoft.config;

import com.rosogisoft.web.LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties appProperties;
    private final MocktailProperties mocktailProperties;
    private final LoginSuccessHandler loginSuccessHandler;

    // ---------------------------------------------------------------
    // Chain 1: user mock-ports — no auth, no CSRF
    // ---------------------------------------------------------------
    @Bean
    @Order(1)
    public SecurityFilterChain userPortChain (HttpSecurity http) throws Exception {
        http
                .securityMatcher(request -> {
                    int port = request.getLocalPort();
                    if (mocktailProperties.mode() == DeploymentMode.STANDALONE) {
                        return port == mocktailProperties.getStandalone().getUserPort();
                    }
                    return port >= appProperties.getRangeStart() && port <= appProperties.getRangeEnd();
                })
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // ---------------------------------------------------------------
    // Chain 2a: UI — form login, LDAP auth
    // ---------------------------------------------------------------
    @Bean
    @Order(2)
    @ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "ldap", matchIfMissing = true)
    public SecurityFilterChain ldapUiChain(HttpSecurity http,
                                           AuthenticationManager authenticationManager) throws Exception {
        http
                .authenticationManager(authenticationManager)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/webjars/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/admin/**").denyAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .permitAll()
                );
        return http.build();
    }

    // ---------------------------------------------------------------
    // Chain 2b: UI — form login, database auth
    // ---------------------------------------------------------------
    @Bean
    @Order(2)
    @ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "database")
    public SecurityFilterChain databaseUiChain(HttpSecurity http,
                                               AuthenticationManager authenticationManager) throws Exception {
        http
                .authenticationManager(authenticationManager)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/webjars/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/password/change").authenticated()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .permitAll()
                );
        return http.build();
    }

    // ---------------------------------------------------------------
    // Chain 2c: UI — standalone, no auth
    // ---------------------------------------------------------------
    @Bean
    @Order(2)
    @ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "standalone")
    public SecurityFilterChain standaloneUiChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // ---------------------------------------------------------------
    // LDAP Context Source
    // ---------------------------------------------------------------
    @Bean
    @ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "ldap", matchIfMissing = true)
    public LdapContextSource ldapContextSource() {
        MocktailProperties.Ldap ldap = mocktailProperties.getAuth().getLdap();
        LdapContextSource source = new LdapContextSource();
        source.setUrl(ldap.getUrl());
        source.setBase(ldap.getBaseDn());

        // Устанавливать manager DN только если он задан
        if (ldap.getManagerDn() != null && !ldap.getManagerDn().isBlank()) {
            source.setUserDn(ldap.getManagerDn());
            source.setPassword(ldap.getManagerPassword());
        }

        source.setPooled(false);

        java.util.Hashtable<String, Object> env = new java.util.Hashtable<>();
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "10000");
        source.setBaseEnvironmentProperties(env);

        return source;
    }

    @Bean
    @ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "ldap", matchIfMissing = true)
    public LdapAuthenticationProvider ldapAuthenticationProvider(LdapContextSource contextSource) {
        MocktailProperties.Ldap ldap = mocktailProperties.getAuth().getLdap();
        FilterBasedLdapUserSearch userSearch = new FilterBasedLdapUserSearch(
                ldap.getUserSearchBase(),
                ldap.getUserSearchFilter(),
                contextSource
        );
        userSearch.setSearchSubtree(true);

        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(userSearch);

        DefaultLdapAuthoritiesPopulator authoritiesPopulator =
                new DefaultLdapAuthoritiesPopulator(contextSource, "");
        authoritiesPopulator.setGroupSearchFilter("(member={0})");
        authoritiesPopulator.setGroupRoleAttribute("cn");
        authoritiesPopulator.setSearchSubtree(true);
        authoritiesPopulator.setIgnorePartialResultException(true);

        return new LdapAuthenticationProvider(authenticator, authoritiesPopulator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "ldap", matchIfMissing = true)
    public AuthenticationManager ldapAuthenticationManager(LdapAuthenticationProvider ldapAuthenticationProvider) {
        return new ProviderManager(ldapAuthenticationProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "database")
    public AuthenticationManager databaseAuthenticationManager(UserDetailsService userDetailsService,
                                                               PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
