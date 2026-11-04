package com.khonhaminh.product;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ProductRepository extends JpaRepository<Product,Long>{
  boolean existsBySkuIgnoreCase(String sku);
  boolean existsByNameIgnoreCaseAndUnitIgnoreCase(String name,String unit);
  boolean existsByNameIgnoreCaseAndUnitIgnoreCaseAndIdNot(String name,String unit,Long id);
  Optional<Product> findFirstBySkuIgnoreCase(String sku);
  Optional<Product> findFirstByNameIgnoreCaseAndUnitIgnoreCase(String name,String unit);
  Optional<Product> findFirstByNameIgnoreCase(String name);
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Product p where p.id=:id") Optional<Product> findLockedById(@Param("id") Long id);
  List<Product> findAllByOrderByNameAsc();
}
