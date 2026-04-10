package com.rosogisoft.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker (MessageBrokerRegistry registry) {
        // Prefix for messages TO clients
        registry.enableSimpleBroker("/topic");
        // Prefix for messages FROM clients (not used much here)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints (StompEndpointRegistry registry) {
        // JS connects to /ws with SockJS fallback
        registry.addEndpoint("/ws")
                .withSockJS();
    }
}
