package asia.rgp.mock.agency.service;

import io.jsonwebtoken.Claims;
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
public class JwtService {

  @Value("${app.jwt.secret}")
  private String secret;

  @Value("${app.jwt.expiration}")
  private long expiration;

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(secret.getBytes());
  }

  public String generateToken(String username, String displayName, UUID userId) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", userId.toString());
    claims.put("username", username);
    claims.put("fullName", displayName);
    return Jwts.builder()
        .claims(claims)
        .subject(username)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(getSigningKey())
        .compact();
  }

  public Claims extractClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public String extractUsername(String token) {
    return extractClaims(token).getSubject();
  }

  public UUID extractUserId(String token) {
    String userIdStr = extractClaims(token).get("userId", String.class);
    return UUID.fromString(userIdStr);
  }

  public boolean validateToken(String token) {
    try {
      extractClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
