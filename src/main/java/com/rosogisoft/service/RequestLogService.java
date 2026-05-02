package com.rosogisoft.service;

import com.rosogisoft.domain.RequestLog;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
