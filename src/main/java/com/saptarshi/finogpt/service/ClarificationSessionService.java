package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.ClassificationResult;
import com.saptarshi.finogpt.dto.PendingClarification;
import com.saptarshi.finogpt.dto.QueryContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClarificationSessionService {

    private static final Duration SESSION_TTL = Duration.ofMinutes(15);

    private final Map<String, PendingClarification> sessions = new ConcurrentHashMap<>();

    public String createSession(Long userId,
                                String originalQuery,
                                ClassificationResult classificationResult,
                                QueryContext context) {
        cleanupExpiredSessions();

        String token = UUID.randomUUID().toString();
        sessions.put(token, new PendingClarification(
                token,
                userId,
                originalQuery,
                classificationResult,
                context,
                LocalDateTime.now()
        ));
        return token;
    }

    public PendingClarification getSession(String token, Long userId) {
        cleanupExpiredSessions();

        if (token == null || token.isBlank()) {
            return null;
        }

        PendingClarification session = sessions.get(token);
        if (session == null) {
            return null;
        }

        if (!session.getUserId().equals(userId)) {
            return null;
        }

        if (isExpired(session)) {
            sessions.remove(token);
            return null;
        }

        return session;
    }

    public void removeSession(String token) {
        if (token != null && !token.isBlank()) {
            sessions.remove(token);
        }
    }

    public void removeSessionsForUser(Long userId) {
        if (userId == null) {
            return;
        }
        sessions.entrySet().removeIf(entry -> userId.equals(entry.getValue().getUserId()));
    }

    private void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(entry -> Duration.between(entry.getValue().getCreatedAt(), now).compareTo(SESSION_TTL) > 0);
    }

    private boolean isExpired(PendingClarification session) {
        return Duration.between(session.getCreatedAt(), LocalDateTime.now()).compareTo(SESSION_TTL) > 0;
    }
}
