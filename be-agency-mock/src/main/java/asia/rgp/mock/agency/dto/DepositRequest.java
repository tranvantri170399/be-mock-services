package asia.rgp.mock.agency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequest {

  @NotBlank(message = "Amount is required")
  @Positive(message = "Amount must be positive")
  private String amount;
}
