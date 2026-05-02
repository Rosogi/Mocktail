package com.rosogisoft.security;

import com.rosogisoft.domain.UserPasswordCredential;
import com.rosogisoft.repository.UserPasswordCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "database")
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserPasswordCredentialRepository credentialRepository;

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        UserPasswordCredential credential = credentialRepository.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + login));
        var user = credential.getUser();
        String role = "ROLE_" + user.getRole().getCode();
        return org.springframework.security.core.userdetails.User
                .withUsername(credential.getLogin())
                .password(credential.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(role)
                .build();
    }
}
