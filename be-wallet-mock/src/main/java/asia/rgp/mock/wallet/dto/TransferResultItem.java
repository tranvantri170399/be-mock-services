package asia.rgp.mock.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResultItem {

  @JsonProperty("amount_before")
  private Long amountBefore;

  @JsonProperty("amount_after")
  private Long amountAfter;

  @JsonProperty("req_amount")
  private Long reqAmount;

  @JsonProperty("dues_amount")
  private Long duesAmount;

  private String transactionId;

  @JsonProperty("agency_transaction_id")
  private String agencyTransactionId;

  private String status;

  @JsonProperty("error_code")
  private String errorCode;

  private String message;

  private String time;

  @JsonProperty("wallets")
  private List<Map<String, Object>> wallets;
}
