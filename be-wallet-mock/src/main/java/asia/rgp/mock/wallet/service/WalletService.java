package asia.rgp.mock.wallet.service;

import asia.rgp.mock.wallet.dto.TransferItem;
import asia.rgp.mock.wallet.dto.TransferRequest;
import asia.rgp.mock.wallet.dto.TransferResponse;
import asia.rgp.mock.wallet.dto.TransferResultItem;
import asia.rgp.mock.wallet.model.WalletAccount;
import asia.rgp.mock.wallet.repository.WalletAccountRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

  private final WalletAccountRepository walletAccountRepository;

  @Value("${app.wallet.initial-balance}")
  private Long initialBalance;

  @Value("${app.wallet.currency}")
  private String currency;

  @Transactional
  public TransferResponse processTransfer(TransferRequest request) {
    List<TransferResultItem> results = new ArrayList<>();

    if (request.getTransfers() == null || request.getTransfers().isEmpty()) {
      return TransferResponse.builder()
          .status("error")
          .code("400")
          .message("No transfers provided")
          .data(results)
          .build();
    }

    for (TransferItem item : request.getTransfers()) {
      TransferResultItem result = processTransferItem(item);
      results.add(result);
    }

    return TransferResponse.builder()
        .status("success")
        .code("200")
        .message("Transfer processed")
        .data(results)
        .build();
  }

  private TransferResultItem processTransferItem(TransferItem item) {
    String userId = buildUserId(item.getAgencyId(), item.getUid(), item.getMemberId());
    
    WalletAccount account =
        walletAccountRepository
            .findByUserId(userId)
            .orElseGet(
                () ->
                    WalletAccount.builder()
                        .userId(userId)
                        .balance(initialBalance)
                        .currency(currency)
                        .build());

    long amountBefore = account.getBalance();
    long reqAmount = item.getAmount();

    try {
      switch (item.getAction().toUpperCase()) {
        case "BET":
        case "LOSE":
          if (account.getBalance() < reqAmount) {
            return TransferResultItem.builder()
                .amountBefore(amountBefore)
                .amountAfter(amountBefore)
                .reqAmount(reqAmount)
                .duesAmount(0L)
                .transactionId(item.getTransactionId())
                .status("failed")
                .errorCode("INSUFFICIENT_BALANCE")
                .message("Insufficient balance")
                .time(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .wallets(buildWalletList(account.getBalance()))
                .build();
          }
          account.setBalance(account.getBalance() - reqAmount);
          break;

        case "WIN":
          account.setBalance(account.getBalance() + reqAmount);
          break;

        default:
          return TransferResultItem.builder()
              .amountBefore(amountBefore)
              .amountAfter(amountBefore)
              .reqAmount(reqAmount)
              .duesAmount(0L)
              .transactionId(item.getTransactionId())
              .status("failed")
              .errorCode("INVALID_ACTION")
              .message("Invalid action: " + item.getAction())
              .time(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
              .wallets(buildWalletList(account.getBalance()))
              .build();
      }

      account = walletAccountRepository.save(account);

      return TransferResultItem.builder()
          .amountBefore(amountBefore)
          .amountAfter(account.getBalance())
          .reqAmount(reqAmount)
          .duesAmount(0L)
          .transactionId(item.getTransactionId())
          .agencyTransactionId("AGENCY-" + item.getTransactionId())
          .status("success")
          .errorCode(null)
          .message("Transfer successful")
          .time(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
          .wallets(buildWalletList(account.getBalance()))
          .build();

    } catch (Exception e) {
      return TransferResultItem.builder()
          .amountBefore(amountBefore)
          .amountAfter(amountBefore)
          .reqAmount(reqAmount)
          .duesAmount(0L)
          .transactionId(item.getTransactionId())
          .status("failed")
          .errorCode("INTERNAL_ERROR")
          .message(e.getMessage())
          .time(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
          .wallets(buildWalletList(account.getBalance()))
          .build();
    }
  }

  private String buildUserId(String agencyId, String uid, String memberId) {
    return String.format("%s:%s:%s", agencyId, uid, memberId);
  }

  private List<Map<String, Object>> buildWalletList(Long balance) {
    List<Map<String, Object>> wallets = new ArrayList<>();
    
    // Main wallet (type 99)
    Map<String, Object> mainWallet = new HashMap<>();
    mainWallet.put("type", 99);
    mainWallet.put("amount", balance);
    mainWallet.put("currency", currency);
    wallets.add(mainWallet);
    
    return wallets;
  }
}
