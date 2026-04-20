package com.rosogisoft.config;

import com.rosogisoft.web.LoginSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.ldap.core.support.DefaultDirObjectFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.ldap.url}")
    private String ldapUrl;

    @Value("${app.ldap.base-dn}")
    private String baseDn;

    @Value("${app.ldap.user-dn-pattern}")
    private String userDnPattern;

    @Value("${app.ldap.group-search-base:ou=groups}")
    private String groupSearchBase;

    @Value("${app.ldap.manager-dn:}")
    private String managerDn;

    @Value("${app.ldap.manager-password:}")
    private String managerPassword;

    @Autowired
    private LoginSuccessHandler loginSuccessHandler;

    // ---------------------------------------------------------------
    // Chain 1: user mock-ports (9000–9999) — no auth, no CSRF
    // ---------------------------------------------------------------
    @Bean
    @Order(1)
    public SecurityFilterChain userPortChain (HttpSecurity http) throws Exception {
        http
                .securityMatcher(request -> {
                    int port = request.getLocalPort();
                    return port >= 9000 && port <= 9999;
                })
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // ---------------------------------------------------------------
    // Chain 2: admin UI (port 8080) — form login, LDAP auth
    // ---------------------------------------------------------------
    @Bean
    @Order(2)
    public SecurityFilterChain adminChain (HttpSecurity http,
                                           AuthenticationManager authenticationManager) throws Exception {
        http
                .authenticationManager(authenticationManager)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/webjars/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
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
    // LDAP Context Source
    // ---------------------------------------------------------------
    @Bean
    public LdapContextSource ldapContextSource() {
        LdapContextSource source = new LdapContextSource();
        source.setUrl(ldapUrl);
        source.setBase(baseDn);

        // Устанавливать manager DN только если он задан
        if (managerDn != null && !managerDn.isBlank()) {
            source.setUserDn(managerDn);
            source.setPassword(managerPassword);
        }

        source.setPooled(false);

        java.util.Hashtable<String, Object> env = new java.util.Hashtable<>();
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "10000");
        source.setBaseEnvironmentProperties(env);

        return source;
    }

    @Bean
    public LdapAuthenticationProvider ldapAuthenticationProvider(LdapContextSource contextSource) {
        FilterBasedLdapUserSearch userSearch = new FilterBasedLdapUserSearch(
                "",
                "(sAMAccountName={0})",
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
    public AuthenticationManager authenticationManager(LdapAuthenticationProvider ldapAuthenticationProvider) {
        return new ProviderManager(ldapAuthenticationProvider);
    }

}
