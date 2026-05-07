package com.rosogisoft.service;

import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.repository.MockDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockMatcherService {

    private final MockDefinitionRepository mockRepository;
    private final RequestConditionMatcher requestConditionMatcher;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Find the best matching mock for an incoming request.
     * <p>
     * Matching order (all active mocks for this owner, sorted by priority DESC):
     * 1. httpMethod == request method OR httpMethod == "*"
     * 2. AntPathMatcher.match(pathPattern, requestPath)
     * 3. Basic mode: if requestBodyContains is set → request body must contain the substring
     * 4. Advanced mode: all configured request condition groups must match their boolean expression
     * <p>
     * The first mock that passes all checks is returned (highest priority wins).
     */
    public Optional<MockDefinition> findMatch (Long ownerId,
                                               String method,
                                               String path,
                                               String queryString,
                                               java.util.Map<String, String> headers,
                                               String body) {
        List<MockDefinition> candidates = mockRepository.findActiveByOwnerId(ownerId);

        for (MockDefinition mock : candidates) {
            if (!methodMatches(mock.getHttpMethod(), method)) {
                continue;
            }
            if (!pathMatches(mock.getPathPattern(), path)) {
                continue;
            }
            if (!requestConditionMatcher.matches(mock, queryString, headers, body)) {
                continue;
            }
            log.debug("Нашли Mock с id={} '{}' для {} {}", mock.getId(), mock.getName(), method, path);
            return Optional.of(mock);
        }

        log.debug("Не нашли Mock {} {}", method, path);
        return Optional.empty();
    }

    // ---------------------------------------------------------------
    private boolean methodMatches (String mockMethod, String requestMethod) {
        return "*".equals(mockMethod) ||
                mockMethod.equalsIgnoreCase(requestMethod);
    }

    private boolean pathMatches (String pattern, String path) {
        try {
            return pathMatcher.match(pattern, path);
        } catch (Exception e) {
            // malformed pattern — skip
            return false;
        }
    }

}
