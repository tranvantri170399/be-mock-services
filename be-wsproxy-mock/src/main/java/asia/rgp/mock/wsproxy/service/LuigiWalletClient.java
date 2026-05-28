package asia.rgp.mock.wsproxy.service;

import com.luigi.outbound.integration.grpc.TransferGatewayGrpc;
import com.luigi.outbound.integration.grpc.BalanceRequest;
import com.luigi.outbound.integration.grpc.BalanceResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LuigiWalletClient {

  private final ManagedChannel channel;
  private final TransferGatewayGrpc.TransferGatewayBlockingStub stub;

  public LuigiWalletClient(@Value("${app.luigi-wallet.host}") String host,
                           @Value("${app.luigi-wallet.port}") int port) {
    this.channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    this.stub = TransferGatewayGrpc.newBlockingStub(channel);
    log.info("[LuigiWalletClient] Initialized | host={} port={}", host, port);
  }

  public double getBalance(String userId) {
    try {
      BalanceRequest request = BalanceRequest.newBuilder()
          .setUserId(userId)
          .build();

      BalanceResponse response = stub.getBalance(request);
      
      if (response.getCode() == 200) {
        double balance = response.getBalance();
        log.info("[LuigiWalletClient] getBalance | userId={} balance={}", userId, balance);
        return balance;
      } else {
        log.warn("[LuigiWalletClient] getBalance failed | userId={} code={} msg={}", 
            userId, response.getCode(), response.getMsg());
        return 0.0;
      }
    } catch (Exception e) {
      log.warn("[LuigiWalletClient] getBalance error | userId={} error={}", userId, e.getMessage());
      return 0.0;
    }
  }
}
