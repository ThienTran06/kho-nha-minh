package com.khonhaminh.inventory;

import com.khonhaminh.common.ApiException;
import com.khonhaminh.product.Product;
import com.khonhaminh.product.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
  private final InventoryService service;
  private final InventoryTransactionRepository repo;
  private final ProductRepository products;

  public InventoryController(InventoryService s, InventoryTransactionRepository r, ProductRepository p) {
    service = s;
    repo = r;
    products = p;
  }

  public record TxRequest(@NotNull Long productId, @NotNull InventoryTransaction.Type type,
      @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
      @DecimalMin(value = "0.0") BigDecimal unitCost, @Size(max = 500) String note,
      @Size(max = 180) String supplier, @Size(max = 180) String receiverName, Boolean invoiceSent,
      @Size(max = 180) String department, @Size(max = 180) String employeeName,
      LocalDateTime occurredAt, @Size(max = 60) String entryUnit) {}

  public record TxEditRequest(@NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
      @DecimalMin(value = "0.0") BigDecimal unitCost, @Size(max = 500) String note,
      @Size(max = 180) String supplier, @Size(max = 180) String receiverName, Boolean invoiceSent,
      @Size(max = 180) String department, @Size(max = 180) String employeeName,
      LocalDateTime occurredAt, @Size(max = 60) String entryUnit) {}

  public record TxView(Long id, String code, String type, String status, Long productId,
      String productName, String unit, BigDecimal quantity, BigDecimal unitCost, String note,
      String supplier, String receiverName, boolean invoiceSent, String department,
      String employeeName, BigDecimal enteredQuantity, String enteredUnit, BigDecimal conversionFactor, LocalDateTime createdAt, LocalDateTime voidedAt,
      boolean deletableDrinkAdjustment) {
  }

  private TxView toTxView(InventoryTransaction t) {
    return new TxView(t.getId(), t.getCode(), t.getType().name(), t.getStatus().name(),
        t.getProduct().getId(), t.getProductNameSnapshot(), t.getUnitSnapshot(), t.getQuantity(),
        t.getUnitCost(), t.getNote(), t.getSupplier(), t.getReceiverName(), t.isInvoiceSent(),
        t.getDepartment(), t.getEmployeeName(), t.getEnteredQuantity(), t.getEnteredUnit(), t.getConversionFactor(),
        t.getCreatedAt(), t.getVoidedAt(), service.isDeletableDrinkAdjustment(t));
  }


  public record ProductHistoryRow(Long id, String code, String type, String status, BigDecimal quantity,
      BigDecimal unitCost, String note, String supplier, String receiverName, boolean invoiceSent,
      String department, String employeeName, BigDecimal enteredQuantity, String enteredUnit, BigDecimal conversionFactor, LocalDateTime createdAt, LocalDateTime voidedAt,
      BigDecimal balanceAfter, boolean deletableDrinkAdjustment) {}

  public record ProductHistoryView(Long productId, String sku, String productName, String category,
      String unit, BigDecimal currentStock, BigDecimal totalIn, BigDecimal totalOut,
      BigDecimal netMovement, long transactionCount, LocalDateTime lastActivity,
      List<ProductHistoryRow> transactions) {}

  @GetMapping("/product/{productId}/history")
  public ProductHistoryView productHistory(@PathVariable Long productId) {
    Product p = products.findById(productId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa"));
    List<InventoryTransaction> all = repo.findByProductIdOrderByCreatedAtAsc(productId);
    List<ProductHistoryRow> rows = new ArrayList<>();
    BigDecimal balance = BigDecimal.ZERO;
    BigDecimal totalIn = BigDecimal.ZERO;
    BigDecimal totalOut = BigDecimal.ZERO;
    long userCount = 0;
    LocalDateTime lastActivity = null;

    for (InventoryTransaction t : all) {
      if (t.getStatus() == InventoryTransaction.Status.COMPLETED) {
        boolean outbound = t.getType() == InventoryTransaction.Type.OUT
            || t.getType() == InventoryTransaction.Type.ADJUST_OUT;
        balance = outbound ? balance.subtract(t.getQuantity()) : balance.add(t.getQuantity());
        if (t.getType() == InventoryTransaction.Type.IN) {
          totalIn = totalIn.add(t.getQuantity());
          userCount++;
        } else if (t.getType() == InventoryTransaction.Type.OUT) {
          totalOut = totalOut.add(t.getQuantity());
          userCount++;
        }
      }
      if (lastActivity == null || t.getCreatedAt().isAfter(lastActivity)) lastActivity = t.getCreatedAt();
      rows.add(new ProductHistoryRow(t.getId(), t.getCode(), t.getType().name(), t.getStatus().name(),
          t.getQuantity(), t.getUnitCost(), t.getNote(), t.getSupplier(), t.getReceiverName(),
          t.isInvoiceSent(), t.getDepartment(), t.getEmployeeName(), t.getEnteredQuantity(), t.getEnteredUnit(), t.getConversionFactor(), t.getCreatedAt(), t.getVoidedAt(),
          balance, service.isDeletableDrinkAdjustment(t)));
    }
    Collections.reverse(rows);
    return new ProductHistoryView(p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getUnit(),
        p.getQuantity(), totalIn, totalOut, totalIn.subtract(totalOut), userCount, lastActivity, rows);
  }

  @GetMapping
  public List<TxView> list() {
    return repo.findTop100ByOrderByCreatedAtDesc().stream().map(this::toTxView).toList();
  }

  @PostMapping
  public TxView create(@Valid @RequestBody TxRequest r) {
    if (r.type() == InventoryTransaction.Type.OPENING || r.type() == InventoryTransaction.Type.ADJUST_IN
        || r.type() == InventoryTransaction.Type.ADJUST_OUT)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Loại giao dịch này chỉ dùng nội bộ");
    return toTxView(service.transactUser(r.productId(), r.type(), r.quantity(), r.entryUnit(), r.unitCost(), r.note(),
        r.supplier(), r.receiverName(), Boolean.TRUE.equals(r.invoiceSent()), r.department(),
        r.employeeName(), r.occurredAt()));
  }

  @PutMapping("/{id}")
  public TxView update(@PathVariable Long id, @Valid @RequestBody TxEditRequest r) {
    return toTxView(service.updateTransaction(id, r.quantity(), r.unitCost(), r.note(), r.supplier(),
        r.receiverName(), Boolean.TRUE.equals(r.invoiceSent()), r.department(), r.employeeName(),
        r.occurredAt(), r.entryUnit()));
  }

  @PutMapping("/by-code/{code}")
  public TxView updateByCode(@PathVariable String code, @Valid @RequestBody TxEditRequest r) {
    return toTxView(service.updateTransactionByCode(code, r.quantity(), r.unitCost(), r.note(), r.supplier(),
        r.receiverName(), Boolean.TRUE.equals(r.invoiceSent()), r.department(), r.employeeName(),
        r.occurredAt(), r.entryUnit()));
  }

  public record DeleteTxResponse(Long transactionId, Long productId, BigDecimal quantityAfterDelete) {}

  @DeleteMapping("/{id}")
  public DeleteTxResponse delete(@PathVariable Long id) {
    InventoryService.DeleteResult r = service.deleteTransaction(id);
    return new DeleteTxResponse(r.transactionId(), r.productId(), r.quantityAfterDelete());
  }

  @DeleteMapping("/by-code/{code}")
  public void deleteByCode(@PathVariable String code) {
    service.deleteTransactionByCode(code);
  }

  @PostMapping("/{id}/void")
  public void voidTx(@PathVariable Long id) {
    service.voidTransaction(id);
  }
}
