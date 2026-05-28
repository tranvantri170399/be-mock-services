package asia.rgp.mock.wsproxy.model;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

  private String sessionId;
  private String zone;
  private String pluginName;
  private String userId;
  private String username;
  private String displayName;
  private String agencyId;
  private String gameToken;
  private LocalDateTime createdAt;
  private LocalDateTime lastActivity;

  public void updateActivity() {
    this.lastActivity = LocalDateTime.now();
  }
}
