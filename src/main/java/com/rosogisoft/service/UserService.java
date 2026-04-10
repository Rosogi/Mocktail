package com.rosogisoft.service;

import com.rosogisoft.domain.User;
import com.rosogisoft.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PortManagerService portManagerService;

    /**
     * Called on every successful LDAP login.
     * Creates the user record if it's their first time and assigns a port.
     */
    @Transactional
    public User ensureUserExists (String username) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            int port = portManagerService.assignNextPort();

            User user = new User(username);
            user.setAssignedPort(port);
            User saved = userRepository.save(user);

            // Register the Tomcat connector immediately
            portManagerService.registerConnector(port);

            log.info("Создали пользователя '{}' с портом {}", username, port);
            return saved;
        });
    }

    public Optional<User> findByUsername (String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByPort (int port) {
        return userRepository.findByAssignedPort(port);
    }
}
