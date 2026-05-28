package asia.rgp.mock.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

/**
 * Request DTO for user registration.
 */
@Data
@Builder
public class RegisterRequest {

  @NotBlank(message = "Agency ID is required")
  private String agencyId;

  @NotBlank(message = "UID is required")
  private String uid;

  @NotBlank(message = "Member ID is required")
  private String memberId;
}
