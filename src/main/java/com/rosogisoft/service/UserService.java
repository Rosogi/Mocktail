package com.rosogisoft.service;

import com.rosogisoft.config.MocktailProperties;
import com.rosogisoft.domain.AuthProvider;
import com.rosogisoft.domain.Role;
import com.rosogisoft.domain.User;
import com.rosogisoft.domain.UserIdentity;
import com.rosogisoft.domain.UserPasswordCredential;
import com.rosogisoft.repository.AuthProviderRepository;
import com.rosogisoft.repository.RoleRepository;
import com.rosogisoft.repository.UserIdentityRepository;
import com.rosogisoft.repository.UserPasswordCredentialRepository;
import com.rosogisoft.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    public static final String AUTH_LDAP = "LDAP";
    public static final String AUTH_DATABASE = "DATABASE";
    public static final String AUTH_STANDALONE = "STANDALONE";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";
    public static final String STANDALONE_SUBJECT = "local";
    private static final int TEMPORARY_PASSWORD_LENGTH = 20;
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuthProviderRepository authProviderRepository;
    private final RoleRepository roleRepository;
    private final UserIdentityRepository identityRepository;
    private final UserPasswordCredentialRepository credentialRepository;
    private final ObjectProvider<PortManagerService> portManagerServiceProvider;
    private final PasswordEncoder passwordEncoder;
    private final MocktailProperties mocktailProperties;

    /**
     * Called on every successful LDAP login.
     * Creates the user record if it's their first time and assigns a port.
     */
    @Transactional
    public User ensureLdapUserExists(String loginName) {
        return ensureIdentityUser(AUTH_LDAP, loginName, loginName, ROLE_USER,
                () -> portManagerService().assignNextPort());
    }

    @Transactional
    public User ensureStandaloneUser() {
        return ensureIdentityUser(AUTH_STANDALONE, STANDALONE_SUBJECT, "Local user", ROLE_USER,
                () -> mocktailProperties.getStandalone().getUserPort());
    }

    @Transactional
    public OneTimePassword createDatabaseUser(String login,
                                              String displayName,
                                              String roleCode) {
        String normalizedLogin = normalizeLogin(login);
        if (credentialRepository.existsByLogin(normalizedLogin)) {
            throw new IllegalArgumentException("Login already exists.");
        }

        String temporaryPassword = generateTemporaryPassword();
        User user = createUser(
                normalizeDisplayName(displayName, normalizedLogin),
                roleCode,
                portManagerService().assignNextPort()
        );

        AuthProvider provider = authProvider(AUTH_DATABASE);
        identityRepository.save(new UserIdentity(user, provider, normalizedLogin, normalizedLogin));

        UserPasswordCredential credential = new UserPasswordCredential(
                user,
                normalizedLogin,
                passwordEncoder.encode(temporaryPassword)
        );
        credential.setMustChangePassword(true);
        credentialRepository.save(credential);

        portManagerService().registerConnector(user.getAssignedPort());
        log.info("Создали DB-пользователя '{}' с портом {}", normalizedLogin, user.getAssignedPort());
        return new OneTimePassword(user, normalizedLogin, temporaryPassword);
    }

    @Transactional
    public OneTimePassword createBootstrapAdmin(String login, Optional<String> configuredPassword) {
        String normalizedLogin = normalizeLogin(login);
        if (credentialRepository.existsByUser_Role_Code(ROLE_ADMIN)) {
            throw new IllegalStateException("Bootstrap admin already exists.");
        }
        if (credentialRepository.existsByLogin(normalizedLogin)) {
            throw new IllegalStateException("Bootstrap admin login already exists.");
        }

        String password = configuredPassword
                .filter(value -> !value.isBlank())
                .orElseGet(this::generateTemporaryPassword);
        User user = createUser(normalizedLogin, ROLE_ADMIN, portManagerService().assignNextPort());

        AuthProvider provider = authProvider(AUTH_DATABASE);
        identityRepository.save(new UserIdentity(user, provider, normalizedLogin, normalizedLogin));

        UserPasswordCredential credential = new UserPasswordCredential(
                user,
                normalizedLogin,
                passwordEncoder.encode(password)
        );
        credential.setMustChangePassword(true);
        credentialRepository.save(credential);

        portManagerService().registerConnector(user.getAssignedPort());
        return new OneTimePassword(user, normalizedLogin, password);
    }

    @Transactional
    public OneTimePassword resetPassword(Long userId) {
        UserPasswordCredential credential = credentialRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User credentials not found."));
        String temporaryPassword = generateTemporaryPassword();
        credential.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        credential.setMustChangePassword(true);
        credential.setPasswordChangedAt(null);
        credentialRepository.save(credential);
        return new OneTimePassword(credential.getUser(), credential.getLogin(), temporaryPassword);
    }

    @Transactional
    public void changeOwnPassword(String login, String currentPassword, String newPassword) {
        UserPasswordCredential credential = credentialRepository.findByLogin(normalizeLogin(login))
                .orElseThrow(() -> new IllegalArgumentException("User credentials not found."));
        if (!passwordEncoder.matches(currentPassword, credential.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is invalid.");
        }
        credential.setPasswordHash(passwordEncoder.encode(newPassword));
        credential.setMustChangePassword(false);
        credential.setPasswordChangedAt(Instant.now());
        credentialRepository.save(credential);
    }

    @Transactional
    public void setEnabled(Long userId, boolean enabled) {
        UserPasswordCredential credential = credentialRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User credentials not found."));
        if (!enabled && isLastEnabledAdmin(credential.getUser())) {
            throw new IllegalStateException("Cannot disable the last enabled administrator.");
        }
        User user = credential.getUser();
        user.setEnabled(enabled);
        userRepository.save(user);
    }

    @Transactional
    public void changeRole(Long userId, String roleCode) {
        UserPasswordCredential credential = credentialRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User credentials not found."));
        User user = credential.getUser();
        if (!ROLE_ADMIN.equals(roleCode) && isLastEnabledAdmin(user)) {
            throw new IllegalStateException("Cannot remove the last enabled administrator.");
        }
        user.setRole(role(roleCode));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean hasDatabaseAdmin() {
        return credentialRepository.existsByUser_Role_Code(ROLE_ADMIN);
    }

    @Transactional(readOnly = true)
    public boolean isPasswordChangeRequired(String login) {
        return credentialRepository.findByLogin(normalizeLogin(login))
                .map(UserPasswordCredential::isMustChangePassword)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByIdentity(String providerCode, String externalSubject) {
        return identityRepository.findByAuthProvider_CodeAndExternalSubject(providerCode, externalSubject)
                .map(UserIdentity::getUser);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByDatabaseLogin(String login) {
        return findByIdentity(AUTH_DATABASE, normalizeLogin(login));
    }

    @Transactional(readOnly = true)
    public List<UserPasswordCredential> findDatabaseAccounts() {
        return credentialRepository.findAllByOrderByLoginAsc();
    }

    @Transactional(readOnly = true)
    public List<Role> findRoles() {
        return roleRepository.findAllByOrderByCodeAsc();
    }

    public Optional<User> findByPort (int port) {
        return userRepository.findByAssignedPort(port);
    }

    private User ensureIdentityUser(String providerCode,
                                    String externalSubject,
                                    String displayName,
                                    String roleCode,
                                    PortSupplier portSupplier) {
        return identityRepository.findByAuthProvider_CodeAndExternalSubject(providerCode, externalSubject)
                .map(UserIdentity::getUser)
                .orElseGet(() -> {
                    User user = createUser(displayName, roleCode, portSupplier.nextPort());
                    identityRepository.save(new UserIdentity(
                            user,
                            authProvider(providerCode),
                            externalSubject,
                            externalSubject
                    ));
                    portManagerService().registerConnector(user.getAssignedPort());
                    log.info("Создали пользователя '{}' ({}) с портом {}",
                            displayName, providerCode, user.getAssignedPort());
                    return user;
                });
    }

    private User createUser(String displayName, String roleCode, int port) {
        User user = new User(normalizeDisplayName(displayName, "user"));
        user.setRole(role(roleCode));
        user.setAssignedPort(port);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private PortManagerService portManagerService() {
        return portManagerServiceProvider.getObject();
    }

    private AuthProvider authProvider(String code) {
        return authProviderRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Auth provider not found: " + code));
    }

    private Role role(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + code));
    }

    private boolean isLastEnabledAdmin(User user) {
        return user.isEnabled() &&
                user.getRole() != null &&
                ROLE_ADMIN.equals(user.getRole().getCode()) &&
                credentialRepository.countByUser_Role_CodeAndUser_EnabledTrue(ROLE_ADMIN) <= 1;
    }

    private String normalizeLogin(String login) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Login is required.");
        }
        return login.trim();
    }

    private String normalizeDisplayName(String displayName, String fallback) {
        if (displayName == null || displayName.isBlank()) {
            return fallback;
        }
        return displayName.trim();
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    private interface PortSupplier {
        int nextPort();
    }

    public record OneTimePassword(User user, String login, String password) {}
}
