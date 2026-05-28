package asia.rgp.mock.agency.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletSyncClient {

  private final RestTemplate restTemplate;

  @Value("${app.wallet.base-url}")
  private String walletBaseUrl;

  @Value("${app.wallet.agency-id}")
  private String agencyId;

  public void win(String userId, long amountCents) {
    try {
      Map<String, Object> item = Map.of(
          "uid", userId,
          "agency_id", agencyId,
          "amount", amountCents,
          "transaction_id", UUID.randomUUID().toString(),
          "action", "WIN");
      Map<String, Object> request = Map.of("transfers", List.of(item));
      restTemplate.postForObject(walletBaseUrl + "/wallet/transfer", request, Map.class);
      log.info("[WalletSync] WIN | userId={} amount={}", userId, amountCents);
    } catch (Exception e) {
      log.warn("[WalletSync] WIN failed | userId={} error={}", userId, e.getMessage());
    }
  }

  public void registerUser(String userId, String agencyId) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      // Parse userId (format: AGENCY_001:username:uuid)
      String[] userIdParts = userId.split(":");
      String username = userIdParts.length >= 2 ? userIdParts[1] : userId;
      String memberId = userIdParts.length >= 3 ? userIdParts[2] : userId;

      Map<String, String> request = Map.of(
          "agencyId", agencyId,
          "uid", username,
          "memberId", memberId);

      HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
      restTemplate.postForObject(walletBaseUrl + "/api/v1/user/register", entity, Map.class);
      log.info("[WalletSync] Register user | userId={} agencyId={}", userId, agencyId);
    } catch (Exception e) {
      log.warn("[WalletSync] Register user failed | userId={} agencyId={} error={}", userId, agencyId, e.getMessage());
    }
  }

  public void deposit(String userId, String agencyId, double amount) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      // Parse userId (format: AGENCY_001:username:uuid)
      String[] userIdParts = userId.split(":");
      String username = userIdParts.length >= 2 ? userIdParts[1] : userId;
      String memberId = userIdParts.length >= 3 ? userIdParts[2] : userId;

      Map<String, Object> request = Map.of(
          "agencyId", agencyId,
          "uid", username,
          "memberId", memberId,
          "amount", amount);

      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
      restTemplate.postForObject(walletBaseUrl + "/api/v1/user/deposit", entity, Map.class);
      log.info("[WalletSync] Deposit | userId={} agencyId={} amount={}", userId, agencyId, amount);
    } catch (Exception e) {
      log.warn("[WalletSync] Deposit failed | userId={} agencyId={} amount={} error={}", userId, agencyId, amount,
          e.getMessage());
    }
  }
}
