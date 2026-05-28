package asia.rgp.mock.wsproxy.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GameTokenValidator {

  @Value("${app.game-token.secret}")
  private String secret;

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(secret.getBytes());
  }

  public Claims validateToken(String token) {
    try {
      return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid game token", e);
    }
  }

  public String extractUserId(String token) {
    Claims claims = validateToken(token);
    return claims.getSubject();
  }

  public String extractGameId(String token) {
    Claims claims = validateToken(token);
    return claims.get("gid", String.class);
  }

  public String extractAgencyId(String token) {
    Claims claims = validateToken(token);
    return claims.get("aid", String.class);
  }
}
