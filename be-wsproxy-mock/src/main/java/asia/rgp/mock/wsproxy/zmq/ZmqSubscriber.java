package asia.rgp.mock.wsproxy.zmq;

import asia.rgp.mock.wsproxy.service.MessagePackHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luigi.gaas.common.data.PuElement;
import com.luigi.gaas.common.data.msgpkg.v1.PuElementMessageFrame;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ValueType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZmqSubscriber {

  @Value("${app.zmq.sub-url}")
  private String subUrl;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MessagePackHelper messagePackHelper;
  private ZContext context;
  private ZMQ.Socket subscriber;
  private ExecutorService executor;
  private final ConcurrentHashMap<String, WebSocketSession> webSocketSessions = new ConcurrentHashMap<>();

  public void registerWebSocketSession(String sessionId, WebSocketSession session) {
    webSocketSessions.put(sessionId, session);
    log.info("[ZMQ] Registered WebSocket session: {}", sessionId);
  }

  public void unregisterWebSocketSession(String sessionId) {
    webSocketSessions.remove(sessionId);
    log.info("[ZMQ] Unregistered WebSocket session: {}", sessionId);
  }

  @PostConstruct
  public void init() {
    executor = Executors.newSingleThreadExecutor();
    executor.submit(this::subscribe);
    log.info("[ZMQ] Subscriber initialized, binding to: {}", subUrl);
  }

  @PreDestroy
  public void shutdown() {
    if (executor != null) {
      executor.shutdown();
    }
    if (context != null) {
      context.close();
    }
  }

  private void subscribe() {
    context = new ZContext();
    subscriber = context.createSocket(ZMQ.SUB);
    subscriber.bind(subUrl);
    subscriber.subscribe("");

    log.info("[ZMQ] SUB socket BOUND to: {}", subUrl);

    while (!Thread.currentThread().isInterrupted()) {
      try {
        byte[] rawFrame = subscriber.recv(0);
        if (rawFrame == null) {
          log.warn("[ZMQ] recv() returned null - no data received");
          continue;
        }

        log.info("[ZMQ] Received {} bytes from ZMQ", rawFrame.length);
        String hexPrefix = bytesToHex(Arrays.copyOf(rawFrame, Math.min(16, rawFrame.length)));
        log.info("[ZMQ] First 16 bytes (hex): {}", hexPrefix);

        String topic = null;
        String payloadJson = null;

        // Try parsing as raw MessagePack (skip version byte if present)
        try {
          byte[] dataToParse = rawFrame;
          // If first byte is 0x01 (version), skip it
          if (rawFrame.length > 1 && rawFrame[0] == 0x01) {
            dataToParse = Arrays.copyOfRange(rawFrame, 1, rawFrame.length);
            log.info("[ZMQ] Skipped version byte, parsing {} bytes", dataToParse.length);
          }

          // Inspect raw structure
          MessageUnpacker unpacker = org.msgpack.core.MessagePack.newDefaultUnpacker(dataToParse);
          if (unpacker.hasNext()) {
            ValueType type = unpacker.getNextFormat().getValueType();
            log.info("[ZMQ] MessagePack data start type: {}", type);
          }
          unpacker.close();

          Map<String, Object> unpacked = messagePackHelper.decode(dataToParse);
          if (unpacked != null && !unpacked.isEmpty()) {
            log.info("[ZMQ] MessagePack unpacked keys: {}", unpacked.keySet());
            if (unpacked.containsKey("topic")) {
              topic = (String) unpacked.get("topic");
              log.info("[ZMQ] Topic found in map: {}", topic);
            }
            if (unpacked.containsKey("data")) {
              Object data = unpacked.get("data");
              payloadJson = objectMapper.writeValueAsString(data);
              log.info("[ZMQ] Data found in map, length: {}", payloadJson.length());
            }
          }
        } catch (Throwable t) {
          log.warn("[ZMQ] Raw MessagePack parsing failed: {}", t.getMessage());
        }

        // Manual extraction based on hex dump: 01 00 0c d3 00 00 01 9e 63 d7 42 23 bc
        // 75 72 6e
        // Offset 12 is 0xbc (fixstr 28), 75 72 6e is "urn"
        if (topic == null && rawFrame.length > 13) {
          try {
            int offset = 12;
            byte prefix = rawFrame[offset];
            if ((prefix & 0xe0) == 0xa0) { // fixstr
              int len = prefix & 0x1f;
              if (rawFrame.length >= offset + 1 + len) {
                topic = new String(rawFrame, offset + 1, len, java.nio.charset.StandardCharsets.UTF_8);
                log.info("[ZMQ] Manually extracted topic: {}", topic);

                // Payload usually follows topic. If topic is followed by fixmap/fixarray, we
                // can decode it.
                int nextOffset = offset + 1 + len;
                if (rawFrame.length > nextOffset) {
                  byte[] remaining = Arrays.copyOfRange(rawFrame, nextOffset, rawFrame.length);
                  String payloadHex = bytesToHex(Arrays.copyOf(remaining, Math.min(16, remaining.length)));
                  log.info("[ZMQ] Payload start hex: {}", payloadHex);

                  Map<String, Object> payloadMap = messagePackHelper.decode(remaining);
                  if (payloadMap != null && !payloadMap.isEmpty()) {
                    payloadJson = objectMapper.writeValueAsString(payloadMap);
                    log.info("[ZMQ] Manually extracted payload from remaining bytes");
                  }
                }
              }
            }
          } catch (Throwable t) {
            log.warn("[ZMQ] Manual extraction failed: {}", t.getMessage());
          }
        }

        // Fallback to PuElementMessageFrame if raw parsing failed
        if (topic == null || payloadJson == null) {
          log.info("[ZMQ] Fallback to PuElementMessageFrame parsing");
          try {
            PuElementMessageFrame frame = new PuElementMessageFrame(ByteBuffer.wrap(rawFrame));
            log.info("[ZMQ] PuElementMessageFrame created successfully");
            topic = frame.getMetadata().getKey();
            PuElement payload = frame.getPayload();
            if (payload != null) {
              payloadJson = payload.toJSON();
              log.info("[ZMQ] PuElementMessageFrame parsed successfully");
            }
          } catch (Throwable t) {
            log.error("[ZMQ] PuElementMessageFrame parsing failed: {} - {}", t.getClass().getName(), t.getMessage());
            continue;
          }
        }

        if (topic == null || payloadJson == null) {
          log.error("[ZMQ] Could not extract topic/payload");
          continue;
        }

        log.info("[ZMQ] Final topic={} payloadLen={}", topic, payloadJson.length());

        String sessionId = extractSessionId(topic);
        if (sessionId != null) {
          forwardToWebSocket(sessionId, payloadJson);
        }
      } catch (Exception e) {
        log.error("[ZMQ] Error receiving/parsing ZMQ frame: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
      }
    }
  }

  private String extractSessionId(String topic) {
    // Topic format: urn:ws:z:{zone}:s:{sessionId}
    // Example: urn:ws:z:MiniGame:s:8045f784 → parts[5]
    if (topic == null)
      return null;
    String[] parts = topic.split(":");
    if (parts.length >= 6) {
      return parts[5];
    }
    return null;
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private void forwardToWebSocket(String sessionId, String payloadJson) {
    WebSocketSession session = webSocketSessions.get(sessionId);
    if (session != null && session.isOpen()) {
      try {
        String finalJson;
        if (payloadJson.trim().startsWith("[")) {
          // Already an envelope, send as is
          finalJson = payloadJson;
          log.info("[ZMQ] Sending existing envelope to WS session={}", sessionId);
        } else {
          // Needs wrapping
          Map<String, Object> data = objectMapper.readValue(payloadJson, Map.class);
          List<Object> envelope = Arrays.asList(5, data);
          finalJson = objectMapper.writeValueAsString(envelope);
          log.info("[ZMQ] Wrapped map into envelope for WS session={}", sessionId);
        }
        session.sendMessage(new TextMessage(finalJson));
      } catch (Exception e) {
        log.error("[ZMQ] Error forwarding to WS session={}: {}", sessionId, e.getMessage());
      }
    } else {
      log.warn("[ZMQ] No open WS session for sessionId={}", sessionId);
    }
  }
}
