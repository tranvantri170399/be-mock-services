package asia.rgp.mock.agency.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameTokenResponse {

  private String gameId;
  private String refreshToken;
  private String token;
  private String gameUrl;
}
