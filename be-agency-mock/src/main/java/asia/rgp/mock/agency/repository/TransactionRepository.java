package asia.rgp.mock.agency.repository;

import asia.rgp.mock.agency.model.Transaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  List<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
