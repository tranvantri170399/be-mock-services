package asia.rgp.mock.agency.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameTokenRequest {
  private UUID userId;
  private String gameId;
  private String clientIp;
}
