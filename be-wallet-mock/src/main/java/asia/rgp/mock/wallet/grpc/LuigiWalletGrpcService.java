package asia.rgp.mock.wallet.grpc;

import asia.rgp.mock.wallet.service.WalletBalanceService;
import com.luigi.outbound.integration.grpc.*;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.stereotype.Component;

/**
 * Mock implementation of Luigi TransferGateway gRPC service.
 * Used for local development/testing instead of calling real Luigi wallet
 * service.
 *
 * <p>
 * This mock aims to replicate Luigi wallet behavior including:
 * <ul>
 * <li>Response codes: 200 (OK), 404 (Agency not found), 409 (Duplicate), 605
 * (Player not found),
 * 606 (Player locked), 607 (Insufficient balance)</li>
 * <li>Action types: DEBIT, CREDIT</li>
 * <li>Type categories: BET, SETTLE, CANCEL, REFUND, RESERVE, ADJUST, RELEASE,
 * RESETTLE</li>
 * <li>Idempotency: Duplicate transaction detection</li>
 * <li>User validation: Player not found/locked checks</li>
 * <li>Agency validation: Agency existence check</li>
 * </ul>
 */
@Slf4j
@GrpcService
@Component
@RequiredArgsConstructor
public class LuigiWalletGrpcService extends TransferGatewayGrpc.TransferGatewayImplBase {

  // Luigi response codes
  private static final int RESPONSE_CODE_OK = 200;
  private static final int ERROR_AGENCY_NOT_FOUND = 404;
  private static final int ERROR_DUPLICATE = 409;
  private static final int ERROR_PLAYER_NOT_FOUND = 605;
  private static final int ERROR_PLAYER_LOCKED = 606;
  private static final int ERROR_INSUFFICIENT_BALANCE = 607;

  private final WalletBalanceService walletBalanceService;

  // Transaction records for idempotency (gRPC-only)
  private final Map<String, TransactionRecord> transactions = new ConcurrentHashMap<>();

  @PostConstruct
  void init() {
    log.info("[LuigiWalletMock] gRPC Service initialized");
    log.info("[LuigiWalletMock] Valid agencies: {}", walletBalanceService.isValidAgency("AGENCY_001"));
  }

  @PreDestroy
  void shutdown() {
    log.info("[LuigiWalletMock] gRPC Service shutting down");
    log.info("[LuigiWalletMock] Final state - users: {}, transactions: {}", walletBalanceService.getUserCount(),
        transactions.size());
  }

  @Override
  public void getBalance(BalanceRequest request, StreamObserver<BalanceResponse> responseObserver) {
    String userId = request.getUserId();
    log.info("[LuigiWalletMock] getBalance | userId={}", userId);

    // Check if user exists
    if (!walletBalanceService.userExists(userId)) {
      log.warn("[LuigiWalletMock] Player not found | userId={}", userId);
      BalanceResponse response = BalanceResponse.newBuilder()
          .setCode(ERROR_PLAYER_NOT_FOUND)
          .setMsg("Player not found")
          .setBalance(0.0)
          .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
      return;
    }

    long balance = walletBalanceService.getBalance(userId);

    // Check if player is locked
    if (walletBalanceService.isPlayerLocked(userId)) {
      log.warn("[LuigiWalletMock] Player locked | userId={}", userId);
      BalanceResponse response = BalanceResponse.newBuilder()
          .setCode(ERROR_PLAYER_LOCKED)
          .setMsg("Player locked")
          .setBalance(balance / 100.0)
          .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
      return;
    }

    BalanceResponse response = BalanceResponse.newBuilder()
        .setCode(RESPONSE_CODE_OK)
        .setMsg("Success")
        .setBalance(balance / 100.0)
        .build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
    log.info("[LuigiWalletMock] getBalance response | userId={} balance={}", userId, balance / 100.0);
  }

  @Override
  public void batchTransfer(
      BatchTransferRequest request, StreamObserver<BatchTransferResponse> responseObserver) {
    log.info(
        "[LuigiWalletMock] batchTransfer | gameId={} roundId={} ts={} transfersCount={}",
        request.getGameId(),
        request.getRoundId(),
        request.getTs(),
        request.getTransfersCount());

    BatchTransferResponse.Builder responseBuilder = BatchTransferResponse.newBuilder();

    for (TransferItem transfer : request.getTransfersList()) {
      TransferResult result = processTransfer(transfer, request.getGameId(), request.getRoundId());
      responseBuilder.addTransfers(result);
    }

    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
    log.info("[LuigiWalletMock] batchTransfer completed | processedCount={}", request.getTransfersCount());
  }

  private TransferResult processTransfer(TransferItem transfer, String gameId, String roundId) {
    String agencyId = transfer.getAgencyId();
    String userId = transfer.getUserId();
    String action = transfer.getAction();
    String type = transfer.getType();
    String option = transfer.getOption();
    double amount = transfer.getAmount();
    String transactionId = transfer.getTransactionId();
    String refTransactionId = transfer.getRefTransactionId();

    log.info(
        "[LuigiWalletMock] processTransfer | agencyId={} userId={} action={} type={} option={} amount={} transactionId={} refTransactionId={}",
        agencyId,
        userId,
        action,
        type,
        option,
        amount,
        transactionId,
        refTransactionId);

    // Validate agency exists
    if (!walletBalanceService.isValidAgency(agencyId)) {
      log.warn("[LuigiWalletMock] Agency not found | agencyId={}", agencyId);
      return buildErrorResult(
          transfer, ERROR_AGENCY_NOT_FOUND, "Agency not found: " + agencyId, 0.0, 0.0);
    }

    // Validate user exists (must register via REST API first)
    if (!walletBalanceService.userExists(userId)) {
      log.warn("[LuigiWalletMock] Player not found | userId={}", userId);
      return buildErrorResult(
          transfer, ERROR_PLAYER_NOT_FOUND, "Player not found: " + userId, 0.0, 0.0);
    }

    // Check if player is locked
    if (walletBalanceService.isPlayerLocked(userId)) {
      log.warn("[LuigiWalletMock] Player locked | userId={}", userId);
      return buildErrorResult(
          transfer, ERROR_PLAYER_LOCKED, "Player locked: " + userId, 0.0, 0.0);
    }

    // Check for duplicate transaction (idempotency)
    TransactionRecord existingTx = transactions.get(transactionId);
    if (existingTx != null) {
      log.warn(
          "[LuigiWalletMock] Duplicate transaction | transactionId={} existing={}",
          transactionId,
          existingTx);
      return buildErrorResult(
          transfer,
          ERROR_DUPLICATE,
          "Duplicate transaction: " + transactionId,
          existingTx.balanceAfter,
          existingTx.payoutAmount);
    }

    long currentBalance = walletBalanceService.getBalance(userId);
    long amountInCents = (long) (amount * 100);
    long newBalance = currentBalance;
    double payoutAmount = 0.0;

    // Process based on action and type
    try {
      switch (action.toUpperCase()) {
        case "DEBIT":
          newBalance = processDebit(currentBalance, amountInCents, type, option);
          walletBalanceService.debit(userId, amountInCents);
          payoutAmount = 0.0;
          break;
        case "CREDIT":
          newBalance = processCredit(currentBalance, amountInCents, type);
          walletBalanceService.credit(userId, amountInCents);
          payoutAmount = amount;
          break;
        default:
          log.error("[LuigiWalletMock] Unknown action | action={}", action);
          return buildErrorResult(
              transfer, 400, "Unknown action: " + action, currentBalance / 100.0, 0.0);
      }

      // Record transaction for idempotency
      TransactionRecord record = new TransactionRecord(transactionId, action, type, amountInCents, newBalance,
          payoutAmount);
      transactions.put(transactionId, record);

      log.info(
          "[LuigiWalletMock] Transfer success | userId={} action={} type={} amount={} balanceBefore={} balanceAfter={}",
          userId,
          action,
          type,
          amount,
          currentBalance / 100.0,
          newBalance / 100.0);

      return buildSuccessResult(transfer, newBalance, payoutAmount);

    } catch (InsufficientBalanceException e) {
      log.warn("[LuigiWalletMock] Insufficient balance | userId={} required={} current={}", userId, amountInCents,
          currentBalance);
      return buildErrorResult(
          transfer, ERROR_INSUFFICIENT_BALANCE, e.getMessage(), currentBalance / 100.0, 0.0);
    }
  }

  private long processDebit(long currentBalance, long amountInCents, String type, String option)
      throws InsufficientBalanceException {
    // Check for DUES option (allows overdraft for RESERVE/RESETTLE)
    boolean allowOverdraft = "DUES".equalsIgnoreCase(option);
    if (allowOverdraft && ("RESERVE".equalsIgnoreCase(type) || "RESETTLE".equalsIgnoreCase(type))) {
      // Allow overdraft for RESERVE/RESETTLE with DUES option
      return currentBalance - amountInCents;
    }

    // Normal balance check
    if (currentBalance < amountInCents) {
      throw new InsufficientBalanceException(
          "Insufficient balance for " + type + ": required " + amountInCents + ", available " + currentBalance);
    }

    return currentBalance - amountInCents;
  }

  private long processCredit(long currentBalance, long amountInCents, String type) {
    // Credit always adds to balance
    return currentBalance + amountInCents;
  }

  private TransferResult buildSuccessResult(
      TransferItem transfer, long balanceAfter, double payoutAmount) {
    return TransferResult.newBuilder()
        .setTransactionId(transfer.getTransactionId())
        .setCode(RESPONSE_CODE_OK)
        .setMsg("Success")
        .setTs(System.currentTimeMillis() / 1000)
        .setRequestAmount(transfer.getAmount())
        .setPayoutAmount(payoutAmount)
        .setBalanceAfter(balanceAfter / 100.0)
        .build();
  }

  private TransferResult buildErrorResult(
      TransferItem transfer, int code, String msg, double balanceAfter, double payoutAmount) {
    return TransferResult.newBuilder()
        .setTransactionId(transfer.getTransactionId())
        .setCode(code)
        .setMsg(msg)
        .setTs(System.currentTimeMillis() / 1000)
        .setRequestAmount(transfer.getAmount())
        .setPayoutAmount(payoutAmount)
        .setBalanceAfter(balanceAfter)
        .build();
  }

  /**
   * Record of a transaction for idempotency checking.
   */
  private static class TransactionRecord {
    final String transactionId;
    final String action;
    final String type;
    final long amount;
    final long balanceAfter;
    final double payoutAmount;

    TransactionRecord(
        String transactionId, String action, String type, long amount, long balanceAfter, double payoutAmount) {
      this.transactionId = transactionId;
      this.action = action;
      this.type = type;
      this.amount = amount;
      this.balanceAfter = balanceAfter;
      this.payoutAmount = payoutAmount;
    }

    @Override
    public String toString() {
      return "TransactionRecord{"
          + "transactionId='"
          + transactionId
          + '\''
          + ", action='"
          + action
          + '\''
          + ", type='"
          + type
          + '\''
          + ", amount="
          + amount
          + ", balanceAfter="
          + balanceAfter
          + ", payoutAmount="
          + payoutAmount
          + '}';
    }
  }

  private static class InsufficientBalanceException extends Exception {
    InsufficientBalanceException(String message) {
      super(message);
    }
  }
}
