package asia.rgp.mock.agency.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayGameRequest {

  @NotBlank(message = "GameId is required")
  private String gameId;
}
