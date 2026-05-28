package asia.rgp.mock.wsproxy.grpc;

import asia.rgp.mock.wsproxy.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PluginServiceClient {

  @Value("${app.grpc.game-backend.host}")
  private String host;

  @Value("${app.grpc.game-backend.port}")
  private int port;

  @Value("${app.grpc.game-backend.deadline-seconds:30}")
  private int deadlineSeconds;

  private ManagedChannel channel;
  private PluginServiceGrpc.PluginServiceBlockingStub blockingStub;

  public void init() {
    this.channel =
        ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    this.blockingStub = PluginServiceGrpc.newBlockingStub(channel);
    log.info("[gRPC] PluginServiceClient initialized: {}:{}", host, port);
  }

  public void shutdown() {
    if (channel != null) {
      try {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public ZmqResponse connectAndCall(ConnectAndCallRequest request) {
    log.info("[gRPC] connectAndCall: zone={}, pluginName={}", request.getZone(), request.getPluginName());
    return blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS).connectAndCall(request);
  }

  public PluginResponse call(PluginRequest request) {
    log.info("[gRPC] call: zone={}, username={}", request.getZone(), request.getUsername());
    return blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS).call(request);
  }

  public ZmqResponse disconnect(DisconnectRequest request) {
    log.info("[gRPC] disconnect: sessionId={}", request.getSessionId());
    return blockingStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS).disconnect(request);
  }
}
