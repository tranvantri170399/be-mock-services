package asia.rgp.mock.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferItem {

  private String token;

  private String uid;

  @JsonProperty("agency_id")
  private String agencyId;

  @JsonProperty("member_id")
  private String memberId;

  private Long amount;

  private String transactionId;

  private String action; // BET, WIN, LOSE

  private Map<String, Object> data; // Game-specific data
}
