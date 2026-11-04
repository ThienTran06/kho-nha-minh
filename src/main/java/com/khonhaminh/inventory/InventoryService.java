package com.khonhaminh.inventory;

import com.khonhaminh.common.ApiException;
import com.khonhaminh.audit.AuditLogService;
import com.khonhaminh.product.Product;
import com.khonhaminh.product.ProductRepository;
import com.khonhaminh.stocktake.StocktakeRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
  private final ProductRepository products;
  private final InventoryTransactionRepository txs;
  private final AuditLogService audit;
  private final StocktakeRepository stocktakes;

  public InventoryService(ProductRepository p, InventoryTransactionRepository t, AuditLogService a, StocktakeRepository s) {
    products = p;
    txs = t;
    audit = a;
    stocktakes = s;
  }

  @Transactional
  public InventoryTransaction transact(Long productId, InventoryTransaction.Type type, BigDecimal qty,
      BigDecimal unitCost, String note) {
    return transact(productId, type, qty, unitCost, note, null, null, false, null, null, null);
  }

  @Transactional
  public InventoryTransaction transact(Long productId, InventoryTransaction.Type type, BigDecimal qty,
      BigDecimal unitCost, String note, String supplier, String receiverName, boolean invoiceSent,
      String department, String employeeName) {
    return transact(productId, type, qty, unitCost, note, supplier, receiverName, invoiceSent,
        department, employeeName, null);
  }

  @Transactional
  public InventoryTransaction transact(Long productId, InventoryTransaction.Type type, BigDecimal qty,
      BigDecimal unitCost, String note, String supplier, String receiverName, boolean invoiceSent,
      String department, String employeeName, LocalDateTime occurredAt) {
    validateQty(qty);
    Product p = products.findLockedById(productId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa"));
    applyMovement(p, type, qty);

    LocalDateTime effectiveAt = normalizeOccurredAt(occurredAt);
    InventoryTransaction t = new InventoryTransaction();
    t.setCode(code(type));
    t.setType(type);
    t.setStatus(InventoryTransaction.Status.COMPLETED);
    t.setProduct(p);
    t.setProductNameSnapshot(p.getName());
    t.setUnitSnapshot(p.getUnit());
    t.setQuantity(qty);
    t.setEnteredQuantity(qty);
    t.setEnteredUnit(p.getUnit());
    t.setConversionFactor(BigDecimal.ONE);
    t.setUnitCost(unitCost);
    t.setNote(clean(note));
    t.setSupplier(clean(supplier));
    t.setReceiverName(clean(receiverName));
    t.setInvoiceSent(invoiceSent);
    t.setDepartment(clean(department));
    t.setEmployeeName(clean(employeeName));
    t.setCreatedAt(effectiveAt);
    t=txs.save(t);
    if(type==InventoryTransaction.Type.IN||type==InventoryTransaction.Type.OUT) audit.log(type==InventoryTransaction.Type.IN?"INVENTORY_IN":"INVENTORY_OUT","TRANSACTION",t.getId(),(type==InventoryTransaction.Type.IN?"Nhập ":"Xuất ")+qty+" "+p.getUnit()+" "+p.getName());
    return t;
  }

  @Transactional
  public InventoryTransaction transactUser(Long productId, InventoryTransaction.Type type, BigDecimal enteredQty,
      String entryUnit, BigDecimal unitCost, String note, String supplier, String receiverName, boolean invoiceSent,
      String department, String employeeName, LocalDateTime occurredAt) {
    validateQty(enteredQty);
    Product p = products.findLockedById(productId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa"));
    Conversion c = convert(p, enteredQty, entryUnit);
    applyMovement(p, type, c.baseQuantity());
    LocalDateTime effectiveAt = normalizeOccurredAt(occurredAt);
    InventoryTransaction t = new InventoryTransaction();
    t.setCode(code(type)); t.setType(type); t.setStatus(InventoryTransaction.Status.COMPLETED);
    t.setProduct(p); t.setProductNameSnapshot(p.getName()); t.setUnitSnapshot(p.getUnit());
    t.setQuantity(c.baseQuantity()); t.setEnteredQuantity(enteredQty); t.setEnteredUnit(c.enteredUnit()); t.setConversionFactor(c.factor());
    t.setUnitCost(unitCost); t.setNote(clean(note)); t.setSupplier(clean(supplier)); t.setReceiverName(clean(receiverName));
    t.setInvoiceSent(invoiceSent); t.setDepartment(clean(department)); t.setEmployeeName(clean(employeeName)); t.setCreatedAt(effectiveAt);
    t=txs.save(t);
    audit.log(type==InventoryTransaction.Type.IN?"INVENTORY_IN":"INVENTORY_OUT","TRANSACTION",t.getId(),
        (type==InventoryTransaction.Type.IN?"Nhập ":"Xuất ")+enteredQty+" "+c.enteredUnit()+
        (c.factor().compareTo(BigDecimal.ONE)>0?" (= "+c.baseQuantity()+" "+p.getUnit()+")":"")+" "+p.getName());
    return t;
  }

  private record Conversion(BigDecimal baseQuantity, String enteredUnit, BigDecimal factor) {}
  private Conversion convert(Product p, BigDecimal enteredQty, String entryUnit) {
    String u = clean(entryUnit);
    if (u == null || u.equalsIgnoreCase(p.getUnit())) return new Conversion(enteredQty, p.getUnit(), BigDecimal.ONE);
    if (p.getPackageUnit()!=null && u.equalsIgnoreCase(p.getPackageUnit()) && p.getUnitsPerPackage()!=null
        && p.getUnitsPerPackage().compareTo(BigDecimal.ONE)>0)
      return new Conversion(enteredQty.multiply(p.getUnitsPerPackage()), p.getPackageUnit(), p.getUnitsPerPackage());
    throw new ApiException(HttpStatus.BAD_REQUEST, "Đơn vị nhập/xuất không hợp lệ cho mặt hàng này");
  }

  @Transactional
  public InventoryTransaction createOpening(Long productId, BigDecimal qty, String note) {
    return transact(productId, InventoryTransaction.Type.OPENING, qty, null, note);
  }

  @Transactional(readOnly = true)
  public BigDecimal getOpeningStock(Long productId) {
    if (!products.existsById(productId))
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa");
    return txs.findByProductIdAndTypeAndStatusOrderByCreatedAtAsc(productId,
        InventoryTransaction.Type.OPENING, InventoryTransaction.Status.COMPLETED).stream()
        .map(InventoryTransaction::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Transactional
  public void setOpeningStock(Long productId, BigDecimal newOpening, String note) {
    if (newOpening == null || newOpening.signum() < 0)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Tồn đầu phải lớn hơn hoặc bằng 0");
    Product p = products.findLockedById(productId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa"));
    var openings = txs.findByProductIdAndTypeAndStatusOrderByCreatedAtAsc(productId,
        InventoryTransaction.Type.OPENING, InventoryTransaction.Status.COMPLETED);
    BigDecimal oldOpening = openings.stream().map(InventoryTransaction::getQuantity)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal delta = newOpening.subtract(oldOpening);
    if (delta.signum() < 0 && p.getQuantity().compareTo(delta.abs()) < 0)
      throw new ApiException(HttpStatus.BAD_REQUEST,
          "Không thể giảm tồn đầu xuống mức này vì hàng đã được xuất. Hiện chỉ còn " + p.getQuantity() + " " + p.getUnit());
    p.setQuantity(p.getQuantity().add(delta));
    txs.deleteAll(openings);
    if (newOpening.signum() > 0) {
      InventoryTransaction t = new InventoryTransaction();
      t.setCode(code(InventoryTransaction.Type.OPENING));
      t.setType(InventoryTransaction.Type.OPENING);
      t.setStatus(InventoryTransaction.Status.COMPLETED);
      t.setProduct(p); t.setProductNameSnapshot(p.getName()); t.setUnitSnapshot(p.getUnit());
      t.setQuantity(newOpening); t.setEnteredQuantity(newOpening); t.setEnteredUnit(p.getUnit());
      t.setConversionFactor(BigDecimal.ONE); t.setNote(clean(note) == null ? "Tồn đầu kỳ" : clean(note));
      t.setCreatedAt(openings.isEmpty() ? p.getCreatedAt() : openings.get(0).getCreatedAt());
      txs.save(t);
    }
    audit.log("OPENING_STOCK_UPDATE", "PRODUCT", productId,
        "Sửa tồn đầu " + p.getName() + ": " + oldOpening + " → " + newOpening + " " + p.getUnit());
  }

  @Transactional
  public boolean importMovement(Long productId, InventoryTransaction.Type type, BigDecimal qty,
      LocalDateTime occurredAt, String sourceKey, String note) {
    if (sourceKey != null && txs.existsBySourceKey(sourceKey)) return false;
    InventoryTransaction t = transact(productId, type, qty, null, note, null, null, false, null, null, occurredAt);
    t.setSourceKey(sourceKey);
    txs.save(t);
    return true;
  }

  @Transactional
  public void syncStockFromExcel(Long productId, BigDecimal target, String note) {
    if (target == null || target.signum() < 0)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Tồn Excel không hợp lệ");
    Product p = products.findLockedById(productId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa"));
    BigDecimal before = p.getQuantity();
    int cmp = target.compareTo(before);
    if (cmp == 0) return;
    BigDecimal diff = target.subtract(before).abs();
    InventoryTransaction.Type type = cmp > 0 ? InventoryTransaction.Type.ADJUST_IN : InventoryTransaction.Type.ADJUST_OUT;
    transact(productId, type, diff, null, note);
    audit.log("EXCEL_SYNC","PRODUCT",productId,"Đồng bộ tồn theo Excel cho "+p.getName()+": "+before+" → "+target+" "+p.getUnit());
  }

  @Transactional
  public InventoryTransaction updateTransaction(Long id, BigDecimal newQty, BigDecimal unitCost, String note,
      String supplier, String receiverName, boolean invoiceSent, String department, String employeeName,
      LocalDateTime occurredAt, String entryUnit) {
    InventoryTransaction t = txs.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu"));
    return updateExistingTransaction(t, newQty, unitCost, note, supplier, receiverName, invoiceSent,
        department, employeeName, occurredAt, entryUnit);
  }

  @Transactional
  public InventoryTransaction updateTransactionByCode(String code, BigDecimal newQty, BigDecimal unitCost, String note,
      String supplier, String receiverName, boolean invoiceSent, String department, String employeeName,
      LocalDateTime occurredAt, String entryUnit) {
    InventoryTransaction t = txs.findByCode(code)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu " + code));
    return updateExistingTransaction(t, newQty, unitCost, note, supplier, receiverName, invoiceSent,
        department, employeeName, occurredAt, entryUnit);
  }

  private InventoryTransaction updateExistingTransaction(InventoryTransaction t, BigDecimal newQty,
      BigDecimal unitCost, String note, String supplier, String receiverName, boolean invoiceSent,
      String department, String employeeName, LocalDateTime occurredAt, String entryUnit) {
    validateQty(newQty);
    ensureEditable(t);

    Product p = products.findLockedById(t.getProduct().getId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa của phiếu"));
    Conversion c = convert(p, newQty, entryUnit);
    BigDecimal newBaseQty = c.baseQuantity();
    BigDecimal oldQty = t.getQuantity();
    BigDecimal diff = newBaseQty.subtract(oldQty);

    if (t.getType() == InventoryTransaction.Type.IN) {
      if (diff.signum() > 0) {
        p.setQuantity(p.getQuantity().add(diff));
      } else if (diff.signum() < 0) {
        BigDecimal remove = diff.abs();
        if (p.getQuantity().compareTo(remove) < 0) {
          throw new ApiException(HttpStatus.BAD_REQUEST,
              "Không thể giảm phiếu nhập xuống mức này vì một phần hàng đã được xuất. Hiện còn "
                  + p.getQuantity() + " " + p.getUnit());
        }
        p.setQuantity(p.getQuantity().subtract(remove));
      }
    } else { // OUT
      if (diff.signum() > 0) {
        if (p.getQuantity().compareTo(diff) < 0) {
          throw new ApiException(HttpStatus.BAD_REQUEST,
              "Không đủ hàng để tăng số lượng xuất. Hiện còn " + p.getQuantity() + " " + p.getUnit());
        }
        p.setQuantity(p.getQuantity().subtract(diff));
      } else if (diff.signum() < 0) {
        p.setQuantity(p.getQuantity().add(diff.abs()));
      }
    }

    t.setQuantity(newBaseQty);
    t.setEnteredQuantity(newQty);
    t.setEnteredUnit(c.enteredUnit());
    t.setConversionFactor(c.factor());
    t.setUnitCost(unitCost);
    t.setNote(clean(note));
    t.setSupplier(clean(supplier));
    t.setReceiverName(clean(receiverName));
    t.setInvoiceSent(invoiceSent);
    t.setDepartment(clean(department));
    t.setEmployeeName(clean(employeeName));
    if (occurredAt != null) t.setCreatedAt(normalizeOccurredAt(occurredAt));
    audit.log("TRANSACTION_UPDATE","TRANSACTION",t.getId(),"Sửa phiếu "+t.getCode()+" của "+p.getName()+": "+oldQty+" → "+newBaseQty+" "+p.getUnit());
    return t;
  }

  @Transactional
  public DeleteResult deleteTransaction(Long id) {
    InventoryTransaction t = txs.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu"));
    Long productId = t.getProduct().getId();
    deleteExistingTransaction(t);
    // Flush ngay trong request xóa để response chỉ được trả về sau khi DELETE và
    // thay đổi tồn kho đã thực sự được ghi xuống database.
    txs.flush();
    Product p = products.findById(productId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa của phiếu"));
    audit.log("TRANSACTION_DELETE","TRANSACTION",id,"Xóa phiếu "+t.getCode()+" của "+t.getProductNameSnapshot()+"; tồn sau xóa "+p.getQuantity()+" "+p.getUnit());
    return new DeleteResult(id, productId, p.getQuantity());
  }

  public record DeleteResult(Long transactionId, Long productId, BigDecimal quantityAfterDelete) {}

  @Transactional
  public void deleteTransactionByCode(String code) {
    InventoryTransaction t = txs.findByCode(code)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu " + code));
    deleteExistingTransaction(t);
  }

  private void deleteExistingTransaction(InventoryTransaction t) {
    ensureDeletable(t);
    Product p = products.findLockedById(t.getProduct().getId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa của phiếu"));

    boolean decreasesStock = t.getType() == InventoryTransaction.Type.OUT
        || t.getType() == InventoryTransaction.Type.ADJUST_OUT;
    boolean increasesStock = t.getType() == InventoryTransaction.Type.IN
        || t.getType() == InventoryTransaction.Type.ADJUST_IN;

    if (decreasesStock) {
      // Xóa một biến động giảm => trả số lượng đó lại kho.
      p.setQuantity(p.getQuantity().add(t.getQuantity()));
    } else if (increasesStock) {
      // Xóa một biến động tăng => phải trừ lại khỏi tồn hiện tại. Không cho âm kho.
      if (p.getQuantity().compareTo(t.getQuantity()) < 0) {
        throw new ApiException(HttpStatus.BAD_REQUEST,
            "Không thể xóa điều chỉnh tăng này vì tồn hiện tại chỉ còn "
                + p.getQuantity() + " " + p.getUnit()
                + ". Nếu có điều chỉnh giảm mới hơn, hãy xóa điều chỉnh mới hơn trước.");
      }
      p.setQuantity(p.getQuantity().subtract(t.getQuantity()));
    } else {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Loại giao dịch này không được phép xóa");
    }
    txs.delete(t);
  }

  @Transactional
  public void voidTransaction(Long id) {
    InventoryTransaction t = txs.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu"));
    if (t.getStatus() == InventoryTransaction.Status.VOIDED)
      throw new ApiException(HttpStatus.CONFLICT, "Phiếu đã được hoàn tác");
    Product p = products.findLockedById(t.getProduct().getId()).orElseThrow();
    if (t.getType() == InventoryTransaction.Type.OUT || t.getType() == InventoryTransaction.Type.ADJUST_OUT) {
      p.setQuantity(p.getQuantity().add(t.getQuantity()));
    } else {
      if (p.getQuantity().compareTo(t.getQuantity()) < 0)
        throw new ApiException(HttpStatus.BAD_REQUEST,
            "Không thể hoàn tác vì số tồn hiện tại đã thấp hơn số lượng của phiếu");
      p.setQuantity(p.getQuantity().subtract(t.getQuantity()));
    }
    t.setStatus(InventoryTransaction.Status.VOIDED);
    t.setVoidedAt(LocalDateTime.now());
    audit.log("TRANSACTION_VOID","TRANSACTION",t.getId(),"Sửa sai phiếu "+t.getCode()+" của "+t.getProductNameSnapshot());
  }

  @Transactional
  public void deleteProductIfNoInOutHistory(Long id) {
    Product p = products.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa. Có thể dòng này đã được xóa trước đó; hãy tải lại danh sách."));

    // Quy tắc người dùng yêu cầu: chỉ phiếu NHẬP/XUẤT thật mới giữ mặt hàng lại.
    // Tồn đầu, điều chỉnh chốt Nước, điều chỉnh Excel và kiểm kê không được coi là
    // "lịch sử nhập/xuất". Nếu không có IN/OUT, xóa luôn các dữ liệu phụ rồi xóa hàng.
    boolean hasRealInOut = txs.existsByProductIdAndTypeIn(id,
        java.util.List.of(InventoryTransaction.Type.IN, InventoryTransaction.Type.OUT));
    if (hasRealInOut) {
      throw new ApiException(HttpStatus.BAD_REQUEST,
          "Mặt hàng đã có lịch sử Nhập/Xuất thật nên không thể xóa. Hãy giữ lại để bảo toàn sổ kho.");
    }

    stocktakes.deleteByProductId(id);
    txs.deleteByProductId(id);
    txs.flush();
    products.delete(p);
  }

  private void ensureEditable(InventoryTransaction t) {
    if (t.getStatus() != InventoryTransaction.Status.COMPLETED)
      throw new ApiException(HttpStatus.CONFLICT, "Phiếu này đã bị sửa sai nên không thể sửa nữa");
    if (t.getType() != InventoryTransaction.Type.IN && t.getType() != InventoryTransaction.Type.OUT)
      throw new ApiException(HttpStatus.BAD_REQUEST,
          "Chỉ có phiếu nhập và phiếu xuất do người dùng tạo mới được sửa");
  }

  private void ensureDeletable(InventoryTransaction t) {
    if (t.getStatus() != InventoryTransaction.Status.COMPLETED)
      throw new ApiException(HttpStatus.CONFLICT, "Phiếu này đã bị sửa sai nên không thể xóa nữa");
    if (t.getType() == InventoryTransaction.Type.IN || t.getType() == InventoryTransaction.Type.OUT) return;
    if (isDeletableDrinkAdjustment(t)) return;
    throw new ApiException(HttpStatus.BAD_REQUEST,
        "Chỉ phiếu nhập/xuất hoặc điều chỉnh chốt tồn Nước mới được xóa");
  }

  public boolean isDeletableDrinkAdjustment(InventoryTransaction t) {
    if (t == null || t.getStatus() != InventoryTransaction.Status.COMPLETED) return false;
    if (t.getType() != InventoryTransaction.Type.ADJUST_IN
        && t.getType() != InventoryTransaction.Type.ADJUST_OUT) return false;

    // Nhận diện theo NGUỒN GỐC giao dịch, không phụ thuộc nhóm hiện tại của hàng hóa.
    // Người dùng có thể đã đổi nhóm thủ công sau khi import; khi đó điều chỉnh Nước cũ
    // vẫn phải xóa được. sourceKey là dấu hiệu chắc nhất; note hỗ trợ dữ liệu từ các bản cũ.
    String sourceKey = t.getSourceKey();
    String note = t.getNote() == null ? "" : t.getNote().trim();
    return (sourceKey != null && sourceKey.startsWith("DRINK_"))
        || note.startsWith("Chốt tồn cuối Nước ngày")
        || note.startsWith("Chốt Tồn cuối Nước ngày");
  }

  private void validateQty(BigDecimal qty) {
    if (qty == null || qty.signum() <= 0)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Số lượng phải lớn hơn 0");
  }

  private LocalDateTime normalizeOccurredAt(LocalDateTime occurredAt) {
    LocalDateTime now = LocalDateTime.now();
    if (occurredAt == null) return now;
    if (occurredAt.isAfter(now.plusMinutes(5)))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể chọn thời gian ở tương lai");
    return occurredAt.withSecond(0).withNano(0);
  }

  private void applyMovement(Product p, InventoryTransaction.Type type, BigDecimal qty) {
    boolean outbound = type == InventoryTransaction.Type.OUT || type == InventoryTransaction.Type.ADJUST_OUT;
    if (outbound && p.getQuantity().compareTo(qty) < 0)
      throw new ApiException(HttpStatus.BAD_REQUEST,
          "Không đủ tồn kho. Hiện còn " + p.getQuantity() + " " + p.getUnit());
    p.setQuantity(outbound ? p.getQuantity().subtract(qty) : p.getQuantity().add(qty));
  }

  private String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

  private String code(InventoryTransaction.Type type) {
    String p = (type == InventoryTransaction.Type.OUT) ? "PX"
        : (type == InventoryTransaction.Type.IN) ? "PN"
        : (type == InventoryTransaction.Type.ADJUST_IN || type == InventoryTransaction.Type.ADJUST_OUT) ? "DC" : "DK";
    return p + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss")) + "-"
        + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }
}
