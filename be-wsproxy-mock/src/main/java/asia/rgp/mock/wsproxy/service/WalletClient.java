package asia.rgp.mock.wsproxy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletClient {

  private final LuigiWalletClient luigiWalletClient;

  /**
   * Get balance from be-wallet-mock (Luigi gRPC) instead of be-agency-mock
   * (HTTP).
   * This ensures balance consistency with be-nagas-treasure which also uses Luigi
   * gRPC.
   */
  public long getBalance(String agencyId, String userId) {
    try {
      // Use userId as-is (format: AGENCY_001:username:uuid)
      // Don't duplicate userId to avoid format issues
      String luigiUserId = userId;

      double balance = luigiWalletClient.getBalance(luigiUserId);
      long balanceCents = (long) (balance * 100); // Convert to cents

      log.info("[WalletClient] getBalance | agencyId={} userId={} luigiUserId={} balance={} cents={}",
          agencyId, userId, luigiUserId, balance, balanceCents);
      return balanceCents;
    } catch (Exception e) {
      log.warn("[WalletClient] getBalance failed | agencyId={} userId={} error={}",
          agencyId, userId, e.getMessage());
      return 0L;
    }
  }
}
