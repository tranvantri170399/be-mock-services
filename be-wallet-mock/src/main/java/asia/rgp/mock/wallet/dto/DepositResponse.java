package asia.rgp.mock.wallet.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for deposit operation.
 */
@Data
@Builder
public class DepositResponse {

  private boolean success;
  private String userId;
  private String agencyId;
  private String uid;
  private String memberId;
  private double depositedAmount;
  private double newBalance;
  private String currency;
  private String message;
}
