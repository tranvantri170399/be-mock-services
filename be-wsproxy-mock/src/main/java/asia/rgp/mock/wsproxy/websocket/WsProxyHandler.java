package asia.rgp.mock.wsproxy.websocket;

import asia.rgp.mock.wsproxy.grpc.PluginServiceClient;
import asia.rgp.mock.wsproxy.model.Session;
import asia.rgp.mock.wsproxy.service.GameTokenValidator;
import asia.rgp.mock.wsproxy.service.MessagePackHelper;
import asia.rgp.mock.wsproxy.service.SessionService;
import asia.rgp.mock.wsproxy.service.WalletClient;
import asia.rgp.mock.wsproxy.zmq.ZmqSubscriber;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsProxyHandler extends TextWebSocketHandler {

  private final SessionService sessionService;
  private final GameTokenValidator tokenValidator;
  private final PluginServiceClient grpcClient;
  private final ZmqSubscriber zmqSubscriber;
  private final MessagePackHelper messagePackHelper;
  private final WalletClient walletClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    log.info("[WS] Connection established: {}", session.getId());
    grpcClient.init();
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    String payload = message.getPayload();
    log.info("[WS] Received: {}", payload);

    try {
      List<Object> frame = objectMapper.readValue(payload, List.class);
      handleFrame(session, frame);
    } catch (Exception e) {
      log.error("[WS] Error parsing frame", e);
      sendError(session, "Invalid frame format");
    }
  }

  private void handleFrame(WebSocketSession session, List<Object> frame) throws Exception {
    if (frame.isEmpty()) {
      sendError(session, "Empty frame");
      return;
    }

    int frameType = ((Number) frame.get(0)).intValue();

    switch (frameType) {
      case 1: // Auth frame
        handleAuth(session, frame);
        break;
      case 6: // Transport command
        handleCommand(session, frame);
        break;
      case 7: // Transport ping
        handlePing(session, frame);
        break;
      default:
        log.warn("[WS] Unknown frame type: {}", frameType);
    }
  }

  private void handleAuth(WebSocketSession session, List<Object> frame) throws Exception {
    if (frame.size() < 5) {
      sendError(session, "Invalid auth frame");
      return;
    }

    String zone = (String) frame.get(1);
    String pluginName = (String) frame.get(2);
    @SuppressWarnings("unchecked")
    Map<String, Object> userParams = (Map<String, Object>) frame.get(4);

    String accessToken = (String) userParams.get("accessToken");
    if (accessToken == null) {
      sendError(session, "Missing accessToken");
      return;
    }

    try {
      Claims claims = tokenValidator.validateToken(accessToken);
      String userId = claims.getSubject();
      String gameId = claims.get("gid", String.class);
      String agencyId = claims.get("aid", String.class);

      log.info(
          "[WS] Auth success: userId={}, gameId={}, agencyId={}, zone={}, plugin={}",
          userId,
          gameId,
          agencyId,
          zone,
          pluginName);

      String effectivePlugin = (gameId != null && !gameId.isEmpty()) ? gameId : pluginName;
      Session wsSession = sessionService.createSession(
          zone, effectivePlugin, userId, userId, userId, agencyId, accessToken);

      zmqSubscriber.registerWebSocketSession(wsSession.getSessionId(), session);
      session.getAttributes().put("sessionId", wsSession.getSessionId());

      // Send auth success: [1, true, 0, sessionId, zone, null]
      List<Object> authResponse = Arrays.asList(1, true, 0, wsSession.getSessionId(), zone, null);
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(authResponse)));

      // Get balance from wallet and send cmd=100 initial state
      try {
        long balance = walletClient.getBalance(agencyId, userId);

        Map<String, Object> accountState = new LinkedHashMap<>();
        accountState.put("gold", balance);
        accountState.put("guarranteed_gold", 0);
        accountState.put("time", System.currentTimeMillis());

        Map<String, Object> cmd100 = new LinkedHashMap<>();
        cmd100.put("uid", wsSession.getSessionId());
        cmd100.put("a", "");
        cmd100.put("As", accountState);
        cmd100.put("u", wsSession.getSessionId());
        cmd100.put("g", 0);
        cmd100.put("dn", userId);
        cmd100.put("cmd", 100);
        cmd100.put("id", 1);

        List<Object> profileResponse = Arrays.asList(5, cmd100);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(profileResponse)));
        log.info("[WS] cmd=100 sent: sessionId={}, gold={}", wsSession.getSessionId(), balance);
      } catch (Exception ex) {
        log.warn("[WS] cmd=100 failed: {}", ex.getMessage());
      }

    } catch (Exception e) {
      log.error("[WS] Auth failed", e);
      sendError(session, "Authentication failed: " + e.getMessage());
    }
  }

  private void handleCommand(WebSocketSession session, List<Object> frame) throws Exception {
    if (frame.size() < 4) {
      sendError(session, "Invalid command frame");
      return;
    }

    String zone = (String) frame.get(1);
    String pluginName = (String) frame.get(2);
    @SuppressWarnings("unchecked")
    Map<String, Object> commandData = (Map<String, Object>) frame.get(3);

    String sessionId = (String) session.getAttributes().get("sessionId");
    if (sessionId == null) {
      sendError(session, "No authenticated session");
      return;
    }

    Session wsSession = sessionService
        .getSessionByUsernameOrSessionId(sessionId)
        .orElse(null);

    if (wsSession == null) {
      sendError(session, "Session not found");
      return;
    }

    // Normalize cmd to Integer (client may send as String)
    Object rawCmd = commandData.get("cmd");
    int cmd = -1;
    if (rawCmd instanceof Number n) {
      cmd = n.intValue();
    } else if (rawCmd instanceof String s) {
      try {
        cmd = Integer.parseInt(s);
      } catch (NumberFormatException ignored) {
      }
    }

    Map<String, Object> normalizedData = new java.util.LinkedHashMap<>(commandData);
    normalizedData.put("cmd", cmd);
    // Include agency_id for backend processing
    // user_id is already in the token and will be extracted by game backend
    normalizedData.put("agency_id", wsSession.getAgencyId());

    log.info("[WS] Command: sessionId={}, cmd={}, zone={}", wsSession.getSessionId(), cmd, zone);

    byte[] dataPayload = messagePackHelper.encode(normalizedData);

    if (cmd == 1005) {
      // JOIN: use connectAndCall to establish session in be-nagas-treasure
      Map<String, Object> authParams = new java.util.LinkedHashMap<>();
      authParams.put("token", wsSession.getGameToken());
      authParams.put("agencyId", wsSession.getAgencyId());
      authParams.put("userId", wsSession.getUserId());
      authParams.put("username", wsSession.getUserId());
      
      // Add extended user parameters to match staging environment
      String[] userIdParts = wsSession.getUserId().split(":");
      // Format: AGENCY_001:username:uuid
      String displayName = userIdParts.length >= 2 ? userIdParts[1] : wsSession.getUserId();
      String memberId = userIdParts.length >= 3 ? userIdParts[2] : UUID.randomUUID().toString();
      
      authParams.put("displayName", displayName);
      authParams.put("memberId", memberId);
      authParams.put("isBot", false);
      authParams.put("agentId", wsSession.getAgencyId());
      authParams.put("gender", "unknown");
      authParams.put("registeredDate", System.currentTimeMillis());
      authParams.put("source", "web");
      authParams.put("agencyCode", wsSession.getAgencyId());
      authParams.put("type", "player");
      authParams.put("gold", 0);
      authParams.put("loginTime", System.currentTimeMillis());
      authParams.put("fg_id", "");
      authParams.put("browser", "unknown");
      authParams.put("affId", "");
      authParams.put("userIdNum", 0);
      authParams.put("subi", "");
      authParams.put("os", "unknown");
      authParams.put("guarranteed_gold", 0);
      authParams.put("ipAddress", "192.168.1.1");
      authParams.put("userAgent", "\"Mozilla/5.0\"");
      authParams.put("avatar", "");
      authParams.put("wsProxyId", wsSession.getSessionId());
      authParams.put("accessToken", wsSession.getGameToken());
      authParams.put("phone", "");
      authParams.put("userNumId", 0);
      authParams.put("time", System.currentTimeMillis());
      authParams.put("device", "desktop");
      
      byte[] profileBytes = messagePackHelper.encode(authParams);

      // Generate separate pluginUserId different from userId
      String pluginUserId = UUID.randomUUID().toString();
      
      asia.rgp.mock.wsproxy.generated.PluginUser pluginUser = asia.rgp.mock.wsproxy.generated.PluginUser.newBuilder()
          .setId(pluginUserId)
          .setUsername(displayName)
          .setSessionId(wsSession.getSessionId())
          .setIp("192.168.1.1")
          .setParameters(com.google.protobuf.ByteString.copyFrom(profileBytes))
          .build();

      asia.rgp.mock.wsproxy.generated.ConnectAndCallRequest connectRequest = asia.rgp.mock.wsproxy.generated.ConnectAndCallRequest
          .newBuilder()
          .setZone(wsSession.getZone())
          .setUser(pluginUser)
          .setPluginName(wsSession.getPluginName())
          .setData(com.google.protobuf.ByteString.copyFrom(dataPayload))
          .build();

      grpcClient.connectAndCall(connectRequest);
      log.info("[WS] connectAndCall sent: sessionId={}", wsSession.getSessionId());
    } else {
      // All other commands: use call
      asia.rgp.mock.wsproxy.generated.PluginRequest grpcRequest = asia.rgp.mock.wsproxy.generated.PluginRequest
          .newBuilder()
          .setZone(zone)
          .setPluginName(pluginName)
          .setUsername(wsSession.getSessionId())
          .setData(com.google.protobuf.ByteString.copyFrom(dataPayload))
          .build();

      grpcClient.call(grpcRequest);
      log.info("[WS] call sent: sessionId={}, cmd={}", wsSession.getSessionId(), cmd);
    }
    // Response will come via ZMQ
  }

  private void handlePing(WebSocketSession session, List<Object> frame) throws Exception {
    // Transport ping: ["7", zone, "1", seq]
    String zone = (String) frame.get(1);
    int seq = ((Number) frame.get(3)).intValue();

    // Pong: [6, 1, seq]
    List<Object> pong = List.of(6, 1, seq);
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
    log.debug("[WS] Pong sent: seq={}", seq);
  }

  private void sendError(WebSocketSession session, String message) {
    try {
      List<Object> errorFrame = List.of(5, Map.of("c", -1, "message", message));
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorFrame)));
    } catch (Exception e) {
      log.error("[WS] Error sending error message", e);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    log.info("[WS] Connection closed: {}, status: {}", session.getId(), status);

    // Find and cleanup session
    sessionService
        .getSessionByUsernameOrSessionId(session.getId())
        .ifPresent(
            s -> {
              sessionService.removeSession(s.getSessionId());
              zmqSubscriber.unregisterWebSocketSession(s.getSessionId());

              // Notify game backend via gRPC
              try {
                asia.rgp.mock.wsproxy.generated.DisconnectRequest disconnectRequest = asia.rgp.mock.wsproxy.generated.DisconnectRequest
                    .newBuilder()
                    .setZone(s.getZone())
                    .setUsername(s.getUsername())
                    .setSessionId(s.getSessionId())
                    .build();
                grpcClient.disconnect(disconnectRequest);
              } catch (Exception e) {
                log.error("[WS] Error notifying disconnect", e);
              }
            });

    grpcClient.shutdown();
  }
}
