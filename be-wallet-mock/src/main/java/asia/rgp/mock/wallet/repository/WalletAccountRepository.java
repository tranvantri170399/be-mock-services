package asia.rgp.mock.wallet.repository;

import asia.rgp.mock.wallet.model.WalletAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletAccountRepository extends JpaRepository<WalletAccount, UUID> {

  Optional<WalletAccount> findByUserId(String userId);

  boolean existsByUserId(String userId);
}
