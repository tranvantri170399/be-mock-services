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
public class TransferRequest {

  @JsonProperty("agency_id")
  private Integer agencyId;

  @JsonProperty("transfers")
  private List<TransferItem> transfers;
}
