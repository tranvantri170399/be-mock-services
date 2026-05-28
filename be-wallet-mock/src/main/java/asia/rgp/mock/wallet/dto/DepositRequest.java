package asia.rgp.mock.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;

/**
 * Request DTO for deposit operation.
 */
@Data
@Builder
public class DepositRequest {

  @NotBlank(message = "Agency ID is required")
  private String agencyId;

  @NotBlank(message = "UID is required")
  private String uid;

  @NotBlank(message = "Member ID is required")
  private String memberId;

  @Positive(message = "Amount must be positive")
  private double amount;
}
