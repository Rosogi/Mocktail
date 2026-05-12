package com.rosogisoft.service;

import com.rosogisoft.domain.LlmAccessLevel;
import com.rosogisoft.domain.LlmAccessToken;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.LlmAccessTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LlmAccessTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final String TOKEN_PREFIX = "mtl_";

    private final LlmAccessTokenRepository tokenRepository;

    @Transactional(readOnly = true)
    public Optional<LlmAccessToken> findForUser(User user) {
        return tokenRepository.findByOwnerId(user.getId());
    }

    @Transactional
    public GeneratedToken generateOrRegenerate(User user) {
        String plainToken = generatePlainToken();
        String tokenHash = hashToken(plainToken);
        Instant now = Instant.now();

        LlmAccessToken accessToken = tokenRepository.findByOwnerId(user.getId())
                .orElseGet(() -> {
                    LlmAccessToken created = new LlmAccessToken();
                    created.setOwner(user);
                    created.setCreatedAt(now);
                    return created;
                });

        if (accessToken.getId() != null) {
            accessToken.setRegeneratedAt(now);
        }
        accessToken.setTokenHash(tokenHash);
        accessToken.setTokenPreview(preview(plainToken));
        accessToken = tokenRepository.save(accessToken);

        return new GeneratedToken(accessToken, plainToken);
    }

    @Transactional
    public LlmAccessToken updatePermissions(User user,
                                            LlmAccessLevel requestLogsAccess,
                                            LlmAccessLevel mocksAccess) {
        if (mocksAccess == LlmAccessLevel.READ_WRITE) {
            throw new IllegalArgumentException("Mock write access is not available yet.");
        }

        LlmAccessToken accessToken = tokenRepository.findByOwnerId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Generate an LLM token first."));
        accessToken.setRequestLogsAccess(requestLogsAccess != null ? requestLogsAccess : LlmAccessLevel.NONE);
        accessToken.setMocksAccess(mocksAccess != null ? mocksAccess : LlmAccessLevel.NONE);
        return tokenRepository.save(accessToken);
    }

    @Transactional
    public Optional<LlmAccessToken> authenticate(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findByTokenHash(hashToken(presentedToken.trim()))
                .filter(token -> token.getOwner() != null && token.getOwner().isEnabled())
                .map(token -> {
                    token.setLastUsedAt(Instant.now());
                    return tokenRepository.save(token);
                });
    }

    private String generatePlainToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String preview(String token) {
        if (token.length() <= 14) {
            return token;
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 6);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record GeneratedToken(LlmAccessToken accessToken, String plainToken) {}
}
