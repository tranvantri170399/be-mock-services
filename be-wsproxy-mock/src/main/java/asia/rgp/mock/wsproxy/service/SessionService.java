package asia.rgp.mock.wsproxy.service;

import asia.rgp.mock.wsproxy.model.Session;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  public Session createSession(
      String zone,
      String pluginName,
      String userId,
      String username,
      String displayName,
      String agencyId,
      String gameToken) {
    String sessionId = generateSessionId();
    Session session =
        Session.builder()
            .sessionId(sessionId)
            .zone(zone)
            .pluginName(pluginName)
            .userId(userId)
            .username(username)
            .displayName(displayName)
            .agencyId(agencyId)
            .gameToken(gameToken)
            .createdAt(java.time.LocalDateTime.now())
            .lastActivity(java.time.LocalDateTime.now())
            .build();
    sessions.put(sessionId, session);
    return session;
  }

  public Optional<Session> getSession(String sessionId) {
    return Optional.ofNullable(sessions.get(sessionId));
  }

  public Optional<Session> getSessionByUsernameOrSessionId(String lookup) {
    Session session = sessions.get(lookup);
    if (session != null) {
      return Optional.of(session);
    }
    return sessions.values().stream()
        .filter(s -> s.getUsername().equals(lookup))
        .findFirst();
  }

  public void removeSession(String sessionId) {
    sessions.remove(sessionId);
  }

  public void updateActivity(String sessionId) {
    Session session = sessions.get(sessionId);
    if (session != null) {
      session.updateActivity();
    }
  }

  private String generateSessionId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
