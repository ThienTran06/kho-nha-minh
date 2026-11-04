package com.khonhaminh.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.khonhaminh.audit.AuditLogService;
import com.khonhaminh.common.ApiException;
import com.khonhaminh.product.Product;
import com.khonhaminh.product.ProductRepository;
import com.khonhaminh.stocktake.StocktakeRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
  @Mock ProductRepository products;
  @Mock InventoryTransactionRepository txs;
  @Mock AuditLogService audit;
  @Mock StocktakeRepository stocktakes;
  InventoryService service;

  @BeforeEach void setup() {
    service = new InventoryService(products, txs, audit, stocktakes);
  }

  private Product product(BigDecimal qty) {
    Product p = new Product();
    ReflectionTestUtils.setField(p, "id", 1L);
    p.setName("Pepsi lon 330ml");
    p.setUnit("LON");
    p.setQuantity(qty);
    p.setPackageUnit("THÙNG");
    p.setUnitsPerPackage(new BigDecimal("24"));
    return p;
  }

  @Test void importTwoCasesConvertsToFortyEightCans() {
    Product p = product(new BigDecimal("10"));
    when(products.findLockedById(1L)).thenReturn(Optional.of(p));
    when(txs.save(any(InventoryTransaction.class))).thenAnswer(i -> i.getArgument(0));

    InventoryTransaction t = service.transactUser(1L, InventoryTransaction.Type.IN,
        new BigDecimal("2"), "THÙNG", null, null, null, null, false, null, null, null);

    assertEquals(0, t.getQuantity().compareTo(new BigDecimal("48")));
    assertEquals(0, p.getQuantity().compareTo(new BigDecimal("58")));
    assertEquals("THÙNG", t.getEnteredUnit());
    assertEquals(0, t.getConversionFactor().compareTo(new BigDecimal("24")));
  }

  @Test void exportPackageIsBlockedWhenStockIsInsufficient() {
    Product p = product(new BigDecimal("20"));
    when(products.findLockedById(1L)).thenReturn(Optional.of(p));

    assertThrows(ApiException.class, () -> service.transactUser(1L, InventoryTransaction.Type.OUT,
        BigDecimal.ONE, "THÙNG", null, null, null, null, false, null, null, null));
    assertEquals(0, p.getQuantity().compareTo(new BigDecimal("20")));
    verify(txs, never()).save(any());
  }

  @Test void deletingOutTransactionReturnsStock() {
    Product p = product(new BigDecimal("8"));
    InventoryTransaction t = tx(11L, p, InventoryTransaction.Type.OUT, new BigDecimal("2"));
    when(txs.findById(11L)).thenReturn(Optional.of(t));
    when(products.findLockedById(1L)).thenReturn(Optional.of(p));
    when(products.findById(1L)).thenReturn(Optional.of(p));

    InventoryService.DeleteResult r = service.deleteTransaction(11L);

    assertEquals(0, p.getQuantity().compareTo(new BigDecimal("10")));
    assertEquals(0, r.quantityAfterDelete().compareTo(new BigDecimal("10")));
    verify(txs).delete(t);
    verify(txs).flush();
  }

  @Test void deletingInTransactionSubtractsStock() {
    Product p = product(new BigDecimal("12"));
    InventoryTransaction t = tx(12L, p, InventoryTransaction.Type.IN, new BigDecimal("2"));
    when(txs.findById(12L)).thenReturn(Optional.of(t));
    when(products.findLockedById(1L)).thenReturn(Optional.of(p));
    when(products.findById(1L)).thenReturn(Optional.of(p));

    service.deleteTransaction(12L);

    assertEquals(0, p.getQuantity().compareTo(new BigDecimal("10")));
    verify(txs).delete(t);
  }

  @Test void deletingInTransactionCannotMakeStockNegative() {
    Product p = product(BigDecimal.ONE);
    InventoryTransaction t = tx(13L, p, InventoryTransaction.Type.IN, new BigDecimal("2"));
    when(txs.findById(13L)).thenReturn(Optional.of(t));
    when(products.findLockedById(1L)).thenReturn(Optional.of(p));

    assertThrows(ApiException.class, () -> service.deleteTransaction(13L));
    assertEquals(0, p.getQuantity().compareTo(BigDecimal.ONE));
    verify(txs, never()).delete(any());
  }

  @Test void deletingDrinkAdjustmentWorksEvenIfProductCategoryWasChangedLater() {
    Product p = product(new BigDecimal("356"));
    p.setCategory("Nguyên liệu / Gia vị");
    InventoryTransaction t = tx(21L, p, InventoryTransaction.Type.ADJUST_OUT, new BigDecimal("117"));
    t.setSourceKey("DRINK_DAILY_CLOSING|2026-09-13|aquafina");
    t.setNote("Chốt tồn cuối Nước ngày 13/09/2026: 473 → 356");
    when(txs.findById(21L)).thenReturn(Optional.of(t));
    when(products.findLockedById(1L)).thenReturn(Optional.of(p));
    when(products.findById(1L)).thenReturn(Optional.of(p));

    service.deleteTransaction(21L);

    assertEquals(0, p.getQuantity().compareTo(new BigDecimal("473")));
    verify(txs).delete(t);
  }

  @Test void ordinaryAdjustmentIsStillProtectedFromDelete() {
    Product p = product(new BigDecimal("100"));
    p.setCategory("Nước / Đồ uống");
    InventoryTransaction t = tx(22L, p, InventoryTransaction.Type.ADJUST_IN, new BigDecimal("10"));
    t.setNote("Kiểm kê cuối tuần");
    when(txs.findById(22L)).thenReturn(Optional.of(t));

    assertThrows(ApiException.class, () -> service.deleteTransaction(22L));
    verify(txs, never()).delete(any());
  }


  @Test void deletingProductWithoutRealInOutAlsoDeletesOpeningAdjustmentsAndStocktakes() {
    Product p = product(new BigDecimal("356"));
    when(products.findById(1L)).thenReturn(Optional.of(p));
    when(txs.existsByProductIdAndTypeIn(eq(1L), anyCollection())).thenReturn(false);

    service.deleteProductIfNoInOutHistory(1L);

    verify(stocktakes).deleteByProductId(1L);
    verify(txs).deleteByProductId(1L);
    verify(txs).flush();
    verify(products).delete(p);
  }

  @Test void deletingProductWithRealInOutIsBlocked() {
    Product p = product(new BigDecimal("356"));
    when(products.findById(1L)).thenReturn(Optional.of(p));
    when(txs.existsByProductIdAndTypeIn(eq(1L), anyCollection())).thenReturn(true);

    assertThrows(ApiException.class, () -> service.deleteProductIfNoInOutHistory(1L));

    verify(stocktakes, never()).deleteByProductId(anyLong());
    verify(txs, never()).deleteByProductId(anyLong());
    verify(products, never()).delete(any());
  }

  private InventoryTransaction tx(Long id, Product p, InventoryTransaction.Type type, BigDecimal qty) {
    InventoryTransaction t = new InventoryTransaction();
    ReflectionTestUtils.setField(t, "id", id);
    t.setCode((type == InventoryTransaction.Type.IN ? "PN" : "PX") + "-TEST");
    t.setType(type);
    t.setStatus(InventoryTransaction.Status.COMPLETED);
    t.setProduct(p);
    t.setProductNameSnapshot(p.getName());
    t.setUnitSnapshot(p.getUnit());
    t.setQuantity(qty);
    return t;
  }
}
