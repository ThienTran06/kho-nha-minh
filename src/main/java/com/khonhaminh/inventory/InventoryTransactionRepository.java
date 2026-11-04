package com.khonhaminh.inventory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
  boolean existsByProductId(Long productId);

  boolean existsByProductIdAndTypeIn(Long productId, java.util.Collection<InventoryTransaction.Type> types);

  void deleteByProductId(Long productId);

  boolean existsBySourceKey(String sourceKey);

  List<InventoryTransaction> findBySourceKeyStartingWithOrderByIdAsc(String prefix);

  List<InventoryTransaction> findByNoteContainingOrderByIdAsc(String text);

  List<InventoryTransaction> findByProductIdAndTypeAndStatusOrderByCreatedAtAsc(Long productId, InventoryTransaction.Type type, InventoryTransaction.Status status);

  Optional<InventoryTransaction> findByCode(String code);

  @EntityGraph(attributePaths = "product")
  List<InventoryTransaction> findTop100ByOrderByCreatedAtDesc();

  @EntityGraph(attributePaths = "product")
  List<InventoryTransaction> findByProductIdOrderByCreatedAtAsc(Long productId);

  @EntityGraph(attributePaths = "product")
  List<InventoryTransaction> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime start, LocalDateTime end);
}
