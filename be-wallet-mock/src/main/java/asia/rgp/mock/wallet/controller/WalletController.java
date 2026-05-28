package asia.rgp.mock.wallet.controller;

import asia.rgp.mock.wallet.dto.TransferRequest;
import asia.rgp.mock.wallet.dto.TransferResponse;
import asia.rgp.mock.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

  private final WalletService walletService;

  @PostMapping("/transfer")
  public ResponseEntity<TransferResponse> transfer(
      @Valid @RequestBody TransferRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
    
    // Log headers for debugging
    if (authorization != null) {
      System.out.println("Authorization header: " + authorization);
    }
    if (apiKey != null) {
      System.out.println("X-API-Key header: " + apiKey);
    }
    
    TransferResponse response = walletService.processTransfer(request);
    return ResponseEntity.ok(response);
  }
}
