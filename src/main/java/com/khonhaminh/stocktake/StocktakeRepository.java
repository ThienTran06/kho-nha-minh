package com.khonhaminh.stocktake;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StocktakeRepository extends JpaRepository<Stocktake,Long>{
 boolean existsByProductId(Long productId);
 void deleteByProductId(Long productId);
 @EntityGraph(attributePaths="product") List<Stocktake> findTop100ByOrderByCountedAtDesc();
}
