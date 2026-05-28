package asia.rgp.mock.wallet.service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Centralized balance storage service shared between REST API and gRPC service.
 * This ensures both interfaces see the same wallet state.
 */
@Slf4j
@Service
public class WalletBalanceService {

  // Initial balance (1,000,000.00 in cents)
  private static final long INITIAL_BALANCE = 100000000L;

  // In-memory balance storage: userId -> balance (cents)
  private final Map<String, Long> balances = new ConcurrentHashMap<>();

  // Valid agencies
  private final Set<String> validAgencies = ConcurrentHashMap.newKeySet();

  // Locked players
  private final Set<String> lockedPlayers = ConcurrentHashMap.newKeySet();

  @PostConstruct
  void init() {
    log.info("[WalletBalanceService] Initialized");
    // Initialize with default agencies
    validAgencies.add("AGENCY_001");
    validAgencies.add("AGENCY_002");
    validAgencies.add("AGENCY_003");
    log.info("[WalletBalanceService] Valid agencies: {}", validAgencies);
  }

  /**
   * Register a new user with initial balance.
   * If user already exists, returns existing balance.
   *
   * @param userId the user ID (format: agencyId:uid:memberId)
   * @return the initial balance in cents
   */
  public long registerUser(String userId) {
    return balances.computeIfAbsent(userId, k -> {
      log.info("[WalletBalanceService] Registering new user | userId={} initialBalance={}", 
          k, INITIAL_BALANCE / 100.0);
      return INITIAL_BALANCE;
    });
  }

  /**
   * Register a new user with custom initial balance.
   *
   * @param userId the user ID
   * @param initialBalance the initial balance in cents
   * @return the initial balance
   */
  public long registerUser(String userId, long initialBalance) {
    Long existing = balances.putIfAbsent(userId, initialBalance);
    if (existing == null) {
      log.info("[WalletBalanceService] Registering new user | userId={} initialBalance={}", 
          userId, initialBalance / 100.0);
      return initialBalance;
    } else {
      log.info("[WalletBalanceService] User already exists | userId={} balance={}", 
          userId, existing / 100.0);
      return existing;
    }
  }

  /**
   * Deposit money into user's account.
   *
   * @param userId the user ID
   * @param amountCents amount to deposit in cents
   * @return new balance after deposit
   */
  public long deposit(String userId, long amountCents) {
    long newBalance = balances.merge(userId, amountCents, Long::sum);
    log.info("[WalletBalanceService] Deposit | userId={} amount={} newBalance={}",
        userId, amountCents / 100.0, newBalance / 100.0);
    return newBalance;
  }

  /**
   * Get current balance.
   *
   * @param userId the user ID
   * @return balance in cents, or null if user not found
   */
  public Long getBalance(String userId) {
    return balances.get(userId);
  }

  /**
   * Check if user exists.
   *
   * @param userId the user ID
   * @return true if user exists
   */
  public boolean userExists(String userId) {
    return balances.containsKey(userId);
  }

  /**
   * Debit from user account.
   *
   * @param userId the user ID
   * @param amountCents amount to debit in cents
   * @return new balance after debit
   * @throws IllegalStateException if insufficient balance
   */
  public long debit(String userId, long amountCents) {
    Long currentBalance = balances.get(userId);
    if (currentBalance == null) {
      throw new IllegalStateException("User not found: " + userId);
    }
    if (currentBalance < amountCents) {
      throw new IllegalStateException(
          "Insufficient balance: required " + amountCents + ", available " + currentBalance);
    }
    long newBalance = currentBalance - amountCents;
    balances.put(userId, newBalance);
    log.info("[WalletBalanceService] Debit | userId={} amount={} newBalance={}",
        userId, amountCents / 100.0, newBalance / 100.0);
    return newBalance;
  }

  /**
   * Credit to user account.
   *
   * @param userId the user ID
   * @param amountCents amount to credit in cents
   * @return new balance after credit
   */
  public long credit(String userId, long amountCents) {
    Long currentBalance = balances.get(userId);
    if (currentBalance == null) {
      throw new IllegalStateException("User not found: " + userId);
    }
    long newBalance = currentBalance + amountCents;
    balances.put(userId, newBalance);
    log.info("[WalletBalanceService] Credit | userId={} amount={} newBalance={}",
        userId, amountCents / 100.0, newBalance / 100.0);
    return newBalance;
  }

  /**
   * Check if agency is valid.
   *
   * @param agencyId the agency ID
   * @return true if valid
   */
  public boolean isValidAgency(String agencyId) {
    return validAgencies.contains(agencyId);
  }

  /**
   * Add a valid agency.
   *
   * @param agencyId the agency ID
   */
  public void addValidAgency(String agencyId) {
    validAgencies.add(agencyId);
  }

  /**
   * Check if player is locked.
   *
   * @param userId the user ID
   * @return true if locked
   */
  public boolean isPlayerLocked(String userId) {
    return lockedPlayers.contains(userId);
  }

  /**
   * Lock a player.
   *
   * @param userId the user ID
   */
  public void lockPlayer(String userId) {
    lockedPlayers.add(userId);
    log.info("[WalletBalanceService] Player locked | userId={}", userId);
  }

  /**
   * Unlock a player.
   *
   * @param userId the user ID
   */
  public void unlockPlayer(String userId) {
    lockedPlayers.remove(userId);
    log.info("[WalletBalanceService] Player unlocked | userId={}", userId);
  }

  /**
   * Get total number of registered users.
   *
   * @return user count
   */
  public int getUserCount() {
    return balances.size();
  }
}
