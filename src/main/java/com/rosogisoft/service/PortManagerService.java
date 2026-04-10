package com.rosogisoft.service;

import com.rosogisoft.config.AppProperties;
import com.rosogisoft.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortManagerService implements ApplicationListener<ContextRefreshedEvent> {

    private final UserRepository userRepository;
    private final AppProperties appProperties;
    private final ServletWebServerApplicationContext serverContext;

    /**
     * Track which ports have already been registered to avoid double-binding
     */
    private final Set<Integer> registeredPorts = ConcurrentHashMap.newKeySet();

    private volatile boolean initialized = false;

    // ---------------------------------------------------------------
    // Restore all user ports on startup
    // ---------------------------------------------------------------
    @Override
    public void onApplicationEvent (ContextRefreshedEvent event) {
        if (initialized) return; // ContextRefreshedEvent fires for child contexts too
        initialized = true;

        log.info("Восстанавливаем пользователя и его порт");
        userRepository.findAllWithAssignedPort().forEach(user -> {
            try {
                registerConnector(user.getAssignedPort());
                log.info("Установили порт {} для пользователя '{}'", user.getAssignedPort(), user.getUsername());
            } catch (Exception e) {
                log.error("Ошибка при назначении порта {} для пользователя '{}': {}",
                        user.getAssignedPort(), user.getUsername(), e.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------
    // Find and return the next free port in the configured range
    // ---------------------------------------------------------------
    public synchronized int assignNextPort () {
        Set<Integer> usedPorts = userRepository.findAllAssignedPorts();
        for (int port = appProperties.getRangeStart(); port <= appProperties.getRangeEnd(); port++) {
            if (!usedPorts.contains(port)) {
                return port;
            }
        }
        log.error("Нет свободных портов в диапазоне от {} до {}",
                appProperties.getRangeStart(), appProperties.getRangeEnd());
        throw new IllegalStateException(
                "No available ports in range %d–%d. All %d slots are taken."
                        .formatted(appProperties.getRangeStart(),
                                appProperties.getRangeEnd(),
                                appProperties.getRangeEnd() - appProperties.getRangeStart() + 1));
    }

    // ---------------------------------------------------------------
    // Register an additional Tomcat connector for the given port
    // ---------------------------------------------------------------
    public void registerConnector (int port) {
        if (registeredPorts.contains(port)) {
            log.debug("Подключение для порта {} уже зарегистрировано, пропускаем", port);
            return;
        }

        TomcatWebServer tomcat =
                (TomcatWebServer) serverContext.getWebServer();
        Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(port);
        connector.setScheme("http");
        connector.setProperty("connectionTimeout", "20000");

        try {
            tomcat.getTomcat().getService().addConnector(connector);
            registeredPorts.add(port);
            log.info("Зарегистрировали Mock подключение на порту {}", port);
        } catch (Exception e) {
            throw new RuntimeException("Cannot start Tomcat connector on port " + port, e);
        }
    }

    public boolean isPortRegistered (int port) {
        return registeredPorts.contains(port);
    }
}
