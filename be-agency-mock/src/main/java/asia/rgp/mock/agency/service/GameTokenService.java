package asia.rgp.mock.agency.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GameTokenService {

  @Value("${app.game-token.secret}")
  private String secret;

  @Value("${app.game-token.expiration}")
  private long expiration;

  @Value("${app.wallet.agency-id}")
  private String agencyId;

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(secret.getBytes());
  }

  public GameTokenResult generateGameToken(UUID userId, String gameId, String clientIp) {
    String refreshToken = UUID.randomUUID().toString();

    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", userId.toString());
    claims.put("iss", "inbound-service");
    claims.put("gid", gameId);
    claims.put("aid", agencyId);
    claims.put("ipc", clientIp);

    String token = Jwts.builder()
        .header().keyId("key-2026-01").and()
        .claims(claims)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(getSigningKey())
        .compact();

    String gameUrl = String.format(
        "http://localhost:8082/play?token=%s", token); // WsProxy mock URL

    return new GameTokenResult(gameId, refreshToken, token, gameUrl, userId.toString());
  }

  public boolean validateGameToken(String token) {
    try {
      Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public record GameTokenResult(
      String gameId, String refreshToken, String token, String gameUrl, String userId) {
  }
}
