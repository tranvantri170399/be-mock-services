package asia.rgp.mock.wsproxy.config;

import asia.rgp.mock.wsproxy.websocket.WsProxyHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final WsProxyHandler wsProxyHandler;

  @Value("${app.websocket.path:/websocket}")
  private String websocketPath;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(wsProxyHandler, websocketPath).setAllowedOrigins("*");
  }
}
