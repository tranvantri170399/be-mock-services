package asia.rgp.mock.agency.service;

import asia.rgp.mock.agency.dto.AuthResponse;
import asia.rgp.mock.agency.dto.DepositRequest;
import asia.rgp.mock.agency.dto.DepositResponse;
import asia.rgp.mock.agency.dto.GameTokenRequest;
import asia.rgp.mock.agency.dto.GameTokenResponse;
import asia.rgp.mock.agency.dto.LoginRequest;
import asia.rgp.mock.agency.dto.PlayGameRequest;
import asia.rgp.mock.agency.dto.RegisterRequest;
import asia.rgp.mock.agency.model.Transaction;
import asia.rgp.mock.agency.model.User;
import asia.rgp.mock.agency.repository.TransactionRepository;
import asia.rgp.mock.agency.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final TransactionRepository transactionRepository;
  private final JwtService jwtService;
  private final GameTokenService gameTokenService;
  private final PasswordEncoder passwordEncoder;
  private final WalletSyncClient walletSyncClient;

  @Value("${app.wallet.initial-balance}")
  private Long initialBalance;

  @Value("${app.wallet.agency-id}")
  private String agencyId;

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
      throw new IllegalArgumentException("Username already exists");
    }

    User user = User.builder()
        .username(request.getUsername())
        .password(passwordEncoder.encode(request.getPassword()))
        .displayName(request.getDisplayName() != null ? request.getDisplayName() : request.getUsername())
        .balance(initialBalance)
        .agencyId(agencyId)
        .build();

    user = userRepository.save(user);

    // Record initial balance as transaction
    Transaction initialTx = Transaction.builder()
        .userId(user.getId())
        .type("INITIAL")
        .amount(initialBalance)
        .balanceAfter(initialBalance)
        .transactionId(UUID.randomUUID().toString())
        .createdAt(LocalDateTime.now())
        .build();
    transactionRepository.save(initialTx);

    // Sync to be-wallet-mock
    walletSyncClient.registerUser(user.getId().toString(), agencyId);

    String token = jwtService.generateToken(user.getUsername(), user.getDisplayName(), user.getId());

    return AuthResponse.builder()
        .token(token)
        .username(user.getUsername())
        .displayName(user.getDisplayName())
        .build();
  }

  public AuthResponse login(LoginRequest request) {
    User user = userRepository
        .findByUsername(request.getUsername())
        .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new IllegalArgumentException("Invalid username or password");
    }

    String token = jwtService.generateToken(user.getUsername(), user.getDisplayName(), user.getId());

    return AuthResponse.builder()
        .token(token)
        .username(user.getUsername())
        .displayName(user.getDisplayName())
        .build();
  }

  @Transactional
  public DepositResponse deposit(UUID userId, DepositRequest request) {
    User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

    long amountCents;
    try {
      BigDecimal amount = new BigDecimal(request.getAmount());
      amountCents = amount.multiply(new BigDecimal("100")).longValue();
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid amount format");
    }

    if (amountCents <= 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }

    user.setBalance(user.getBalance() + amountCents);
    user = userRepository.save(user);

    // Sync to be-wallet-mock using new deposit endpoint
    double amountDisplay = amountCents / 100.0;
    walletSyncClient.deposit(user.getId().toString(), agencyId, amountDisplay);

    Transaction tx = Transaction.builder()
        .userId(user.getId())
        .type("DEPOSIT")
        .amount(amountCents)
        .balanceAfter(user.getBalance())
        .transactionId(UUID.randomUUID().toString())
        .createdAt(LocalDateTime.now())
        .build();
    transactionRepository.save(tx);

    return DepositResponse.builder()
        .balance(user.getBalance())
        .message("Deposit successful")
        .build();
  }

  public GameTokenResponse playGame(UUID userId, PlayGameRequest request, String clientIp) {
    User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

    GameTokenService.GameTokenResult result = gameTokenService.generateGameToken(user.getId(), request.getGameId(),
        clientIp);

    return GameTokenResponse.builder()
        .gameId(result.gameId())
        .refreshToken(result.refreshToken())
        .token(result.token())
        .gameUrl(result.gameUrl())
        .build();
  }

  public User getUserById(UUID userId) {
    return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  public Long getBalance(UUID userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
    return user.getBalance();
  }
}
