package com.rosogisoft.service;

import com.rosogisoft.domain.RequestLog;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RequestLogService {

    private static final int DEFAULT_LIMIT = 200;

    private final RequestLogRepository logRepository;

    @Transactional
    public RequestLog save (RequestLog log) {
        return logRepository.save(log);
    }

    public List<RequestLog> findRecentForUser (User user) {
        return logRepository.findRecentByOwnerId(user.getId(), PageRequest.of(0, DEFAULT_LIMIT));
    }

    public List<RequestLog> findFilteredForUser(User user, LogFilter filter) {
        LogFilter safeFilter = filter != null ? filter : LogFilter.empty();
        return logRepository.findFilteredByOwnerId(
                user.getId(),
                safeFilter.search(),
                safeFilter.remoteAddresses() != null && !safeFilter.remoteAddresses().isEmpty(),
                safeRemoteAddresses(safeFilter.remoteAddresses()),
                safeFilter.method(),
                safeFilter.statusMin(),
                safeFilter.statusMax(),
                safeFilter.fromTimestamp() != null,
                safeFilter.fromTimestamp(),
                safeFilter.toTimestamp() != null,
                safeFilter.toTimestamp(),
                PageRequest.of(0, DEFAULT_LIMIT));
    }

    public Optional<RequestLog> findByIdForUser(Long id, User user) {
        return logRepository.findByIdAndOwnerId(id, user.getId());
    }

    public long countForUser (User user) {
        return logRepository.countByOwnerId(user.getId());
    }

    @Transactional
    public void clearForUser (User user) {
        logRepository.deleteAllByOwnerId(user.getId());
    }

    @Transactional
    public boolean deleteForUser(Long logId, User user) {
        return logRepository.deleteByIdAndOwnerId(logId, user.getId()) > 0;
    }

    public record LogFilter(String search,
                            List<String> remoteAddresses,
                            String method,
                            Integer statusMin,
                            Integer statusMax,
                            java.time.Instant fromTimestamp,
                            java.time.Instant toTimestamp) {
        public static LogFilter empty() {
            return new LogFilter(null, List.of(), null, null, null, null, null);
        }
    }

    private List<String> safeRemoteAddresses(List<String> remoteAddresses) {
        return remoteAddresses != null && !remoteAddresses.isEmpty()
                ? remoteAddresses
                : List.of("__mocktail_no_remote_address__");
    }
}
