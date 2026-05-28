package asia.rgp.mock.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

  private String status;

  private String code;

  private String message;

  @JsonProperty("data")
  private List<TransferResultItem> data;
}
