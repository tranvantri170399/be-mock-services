package asia.rgp.mock.wallet.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for user registration.
 */
@Data
@Builder
public class RegisterResponse {

  private boolean success;
  private String userId;
  private String agencyId;
  private String uid;
  private String memberId;
  private double balance;
  private String currency;
  private String message;
}
