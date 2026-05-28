package asia.rgp.mock.wallet.controller;

import asia.rgp.mock.wallet.dto.RegisterRequest;
import asia.rgp.mock.wallet.dto.RegisterResponse;
import asia.rgp.mock.wallet.dto.DepositRequest;
import asia.rgp.mock.wallet.dto.DepositResponse;
import asia.rgp.mock.wallet.service.WalletBalanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User management controller for wallet operations.
 * Provides endpoints to register users and deposit funds.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

  private final WalletBalanceService walletBalanceService;

  /**
   * Register a new user with initial balance.
   * The userId format should be: agencyId:uid:memberId
   * Example: AGENCY_001:12345:67890
   *
   * @param request the registration request
   * @return the registration response with initial balance
   */
  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    String userId = buildUserId(request.getAgencyId(), request.getUid(), request.getMemberId());
    
    log.info("[UserController] Register user | agencyId={} uid={} memberId={} userId={}",
        request.getAgencyId(), request.getUid(), request.getMemberId(), userId);

    // Register user with initial balance (1,000,000.00)
    long initialBalance = walletBalanceService.registerUser(userId);

    RegisterResponse response = RegisterResponse.builder()
        .success(true)
        .userId(userId)
        .agencyId(request.getAgencyId())
        .uid(request.getUid())
        .memberId(request.getMemberId())
        .balance(initialBalance / 100.0) // Convert cents to display units
        .currency("VND")
        .message("User registered successfully")
        .build();

    log.info("[UserController] Register success | userId={} balance={}", userId, initialBalance / 100.0);
    return ResponseEntity.ok(response);
  }

  /**
   * Deposit funds into a user's account.
   *
   * @param request the deposit request
   * @return the deposit response with new balance
   */
  @PostMapping("/deposit")
  public ResponseEntity<DepositResponse> deposit(@Valid @RequestBody DepositRequest request) {
    String userId = buildUserId(request.getAgencyId(), request.getUid(), request.getMemberId());
    long amountCents = (long) (request.getAmount() * 100); // Convert to cents

    log.info("[UserController] Deposit | agencyId={} uid={} memberId={} userId={} amount={}",
        request.getAgencyId(), request.getUid(), request.getMemberId(), userId, request.getAmount());

    // Check if user exists, auto-create if not
    if (!walletBalanceService.userExists(userId)) {
      log.warn("[UserController] User not found, auto-creating | userId={}", userId);
      walletBalanceService.registerUser(userId);
    }

    // Deposit the amount
    long newBalance = walletBalanceService.deposit(userId, amountCents);

    DepositResponse response = DepositResponse.builder()
        .success(true)
        .userId(userId)
        .agencyId(request.getAgencyId())
        .uid(request.getUid())
        .memberId(request.getMemberId())
        .depositedAmount(request.getAmount())
        .newBalance(newBalance / 100.0) // Convert cents to display units
        .currency("VND")
        .message("Deposit successful")
        .build();

    log.info("[UserController] Deposit success | userId={} amount={} newBalance={}",
        userId, request.getAmount(), newBalance / 100.0);
    return ResponseEntity.ok(response);
  }

  private String buildUserId(String agencyId, String uid, String memberId) {
    return String.format("%s:%s:%s", agencyId, uid, memberId);
  }
}
