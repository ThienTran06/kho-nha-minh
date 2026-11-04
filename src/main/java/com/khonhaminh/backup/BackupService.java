package com.khonhaminh.backup;

import tools.jackson.databind.json.JsonMapper;
import com.khonhaminh.audit.AuditLogService;
import com.khonhaminh.audit.AuditEvent;
import com.khonhaminh.audit.AuditEventRepository;
import com.khonhaminh.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import com.khonhaminh.expense.Expense;
import com.khonhaminh.expense.ExpenseRepository;
import com.khonhaminh.inventory.InventoryTransaction;
import com.khonhaminh.inventory.InventoryTransactionRepository;
import com.khonhaminh.product.Product;
import com.khonhaminh.product.ProductRepository;
import com.khonhaminh.stocktake.Stocktake;
import com.khonhaminh.stocktake.StocktakeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BackupService {
  private final ProductRepository products; private final InventoryTransactionRepository txs; private final ExpenseRepository expenses; private final StocktakeRepository stocktakes; private final AuditEventRepository auditEvents; private final AuditLogService audit; private final JsonMapper mapper; private final BackupSnapshotService snapshots;
  @Value("${app.storage.mode:local}") private String storageMode;
  public BackupService(ProductRepository p,InventoryTransactionRepository t,ExpenseRepository e,StocktakeRepository s,AuditEventRepository ar,AuditLogService a,JsonMapper m,BackupSnapshotService snapshots){products=p;txs=t;expenses=e;stocktakes=s;auditEvents=ar;audit=a;mapper=m;this.snapshots=snapshots;}

  public record ProductData(Long id,String sku,String name,String unit,String category,boolean categoryLocked,BigDecimal quantity,BigDecimal minStock,BigDecimal unitPrice,BigDecimal targetStock,String packageUnit,BigDecimal unitsPerPackage,String note){}
  public record TxData(String code,String type,String status,Long productId,String productName,String unit,BigDecimal quantity,BigDecimal enteredQuantity,String enteredUnit,BigDecimal conversionFactor,String sourceKey,BigDecimal unitCost,String note,String supplier,String receiverName,boolean invoiceSent,String department,String employeeName,LocalDateTime createdAt,LocalDateTime voidedAt){}
  public record ExpenseData(LocalDate expenseDate,String category,BigDecimal amount,String note){}
  public record StocktakeData(Long productId,String productName,String unit,BigDecimal systemQuantity,BigDecimal actualQuantity,BigDecimal differenceQuantity,String note,LocalDateTime countedAt,LocalDateTime createdAt){}
  public record AuditData(String action,String entityType,Long entityId,String description,LocalDateTime createdAt){}
  public record BackupData(String formatVersion,LocalDateTime exportedAt,List<ProductData> products,List<TxData> transactions,List<ExpenseData> expenses,List<StocktakeData> stocktakes,List<AuditData> auditEvents){}

  @Transactional(readOnly=true)
  public BackupData exportData(){
    List<ProductData> ps=products.findAllByOrderByNameAsc().stream().map(p->new ProductData(p.getId(),p.getSku(),p.getName(),p.getUnit(),p.getCategory(),p.isCategoryLocked(),p.getQuantity(),p.getMinStock(),p.getUnitPrice(),p.getTargetStock(),p.getPackageUnit(),p.getUnitsPerPackage(),p.getNote())).toList();
    List<TxData> ts=txs.findAll().stream().map(t->new TxData(t.getCode(),t.getType().name(),t.getStatus().name(),t.getProduct().getId(),t.getProductNameSnapshot(),t.getUnitSnapshot(),t.getQuantity(),t.getEnteredQuantity(),t.getEnteredUnit(),t.getConversionFactor(),t.getSourceKey(),t.getUnitCost(),t.getNote(),t.getSupplier(),t.getReceiverName(),t.isInvoiceSent(),t.getDepartment(),t.getEmployeeName(),t.getCreatedAt(),t.getVoidedAt())).toList();
    List<ExpenseData> es=expenses.findAll().stream().map(e->new ExpenseData(e.getExpenseDate(),e.getCategory(),e.getAmount(),e.getNote())).toList();
    List<StocktakeData> ss=stocktakes.findAll().stream().map(x->new StocktakeData(x.getProduct().getId(),x.getProductNameSnapshot(),x.getUnitSnapshot(),x.getSystemQuantity(),x.getActualQuantity(),x.getDifferenceQuantity(),x.getNote(),x.getCountedAt(),x.getCreatedAt())).toList();
    List<AuditData> as=auditEvents.findAll().stream().map(x->new AuditData(x.getAction(),x.getEntityType(),x.getEntityId(),x.getDescription(),x.getCreatedAt())).toList();
    return new BackupData("1.4",LocalDateTime.now(),ps,ts,es,ss,as);
  }

  public byte[] exportJson() throws Exception{return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(exportData());}

  private BackupData readAndValidate(byte[] bytes) {
    if (bytes == null || bytes.length == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "File đang trống.");
    try {
      BackupData b = mapper.readValue(bytes, BackupData.class);
      if (b == null || b.products() == null || b.transactions() == null || b.expenses() == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "File này không phải file dữ liệu Kho Nhà Mình hoặc bị thiếu dữ liệu bắt buộc.");
      }
      return b;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Không đọc được file. Hãy chọn file .json được xuất từ Kho Nhà Mình.");
    }
  }

  @Transactional
  public RestoreResult restore(byte[] bytes) throws Exception {
    BackupData b=readAndValidate(bytes);
    saveSafetyBackupBeforeRestore();
    stocktakes.deleteAllInBatch(); txs.deleteAllInBatch(); expenses.deleteAllInBatch(); products.deleteAllInBatch(); auditEvents.deleteAllInBatch();
    Map<Long,Product> map=new HashMap<>();
    for(ProductData d:b.products()){
      Product p=new Product();p.setSku(d.sku());p.setName(d.name());p.setUnit(d.unit());p.setCategory(d.category());p.setCategoryLocked(d.categoryLocked());p.setQuantity(nz(d.quantity()));p.setMinStock(nz(d.minStock()));p.setUnitPrice(nz(d.unitPrice()));p.setTargetStock(nz(d.targetStock()));p.setPackageUnit(d.packageUnit());p.setUnitsPerPackage(nz(d.unitsPerPackage()));p.setNote(d.note());p=products.save(p);map.put(d.id(),p);
    }
    for(TxData d:b.transactions()){
      Product p=map.get(d.productId());if(p==null)continue;
      InventoryTransaction t=new InventoryTransaction();t.setCode(d.code());t.setType(InventoryTransaction.Type.valueOf(d.type()));t.setStatus(InventoryTransaction.Status.valueOf(d.status()));t.setProduct(p);t.setProductNameSnapshot(d.productName()==null?p.getName():d.productName());t.setUnitSnapshot(d.unit()==null?p.getUnit():d.unit());t.setQuantity(nz(d.quantity()));t.setEnteredQuantity(d.enteredQuantity()==null?nz(d.quantity()):d.enteredQuantity());t.setEnteredUnit(d.enteredUnit()==null?t.getUnitSnapshot():d.enteredUnit());t.setConversionFactor(d.conversionFactor()==null?BigDecimal.ONE:d.conversionFactor());t.setSourceKey(d.sourceKey());t.setUnitCost(d.unitCost());t.setNote(d.note());t.setSupplier(d.supplier());t.setReceiverName(d.receiverName());t.setInvoiceSent(d.invoiceSent());t.setDepartment(d.department());t.setEmployeeName(d.employeeName());t.setCreatedAt(d.createdAt());t.setVoidedAt(d.voidedAt());txs.save(t);
    }
    for(ExpenseData d:b.expenses()){Expense e=new Expense();e.setExpenseDate(d.expenseDate());e.setCategory(d.category());e.setAmount(d.amount());e.setNote(d.note());expenses.save(e);}
    if(b.stocktakes()!=null)for(StocktakeData d:b.stocktakes()){Product p=map.get(d.productId());if(p==null)continue;Stocktake x=new Stocktake();x.setProduct(p);x.setProductNameSnapshot(d.productName()==null?p.getName():d.productName());x.setUnitSnapshot(d.unit()==null?p.getUnit():d.unit());x.setSystemQuantity(nz(d.systemQuantity()));x.setActualQuantity(nz(d.actualQuantity()));x.setDifferenceQuantity(nz(d.differenceQuantity()));x.setNote(d.note());x.setCountedAt(d.countedAt());x.setCreatedAt(d.createdAt());stocktakes.save(x);}
    if(b.auditEvents()!=null)for(AuditData d:b.auditEvents()){AuditEvent x=new AuditEvent();x.setAction(d.action());x.setEntityType(d.entityType());x.setEntityId(d.entityId());x.setDescription(d.description());x.setCreatedAt(d.createdAt());auditEvents.save(x);}
    audit.log("BACKUP_RESTORE","SYSTEM",null,"Khôi phục dữ liệu từ file backup: "+b.products().size()+" mặt hàng, "+b.transactions().size()+" giao dịch, "+b.expenses().size()+" chi phí");
    return new RestoreResult(b.products().size(),b.transactions().size(),b.expenses().size());
  }
  private void saveSafetyBackupBeforeRestore() throws Exception {
    byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(exportData());
    // This copy is committed independently, so it survives if the restore transaction fails.
    snapshots.saveBeforeRestore(json);
    // Desktop keeps a user-visible JSON file. Cloud deployments use the durable database snapshot table
    // rather than an ephemeral container filesystem.
    if (!"cloud".equalsIgnoreCase(storageMode)) {
      Path dir=Path.of(System.getProperty("user.home"),"KhoNhaMinh","backups");
      Files.createDirectories(dir);
      String stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
      Path out=dir.resolve("TRUOC-KHOI-PHUC-"+stamp+".json");
      Files.write(out,json);
    }
  }
  private static BigDecimal nz(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
  public record RestoreResult(int products,int transactions,int expenses){}
  public record BackupPreview(String formatVersion, LocalDateTime exportedAt, int products, int transactions, int expenses, int stocktakes, int auditEvents){}

  public BackupPreview preview(byte[] bytes) {
    BackupData b = readAndValidate(bytes);
    return new BackupPreview(b.formatVersion(), b.exportedAt(), b.products().size(), b.transactions().size(), b.expenses().size(),
        b.stocktakes()==null?0:b.stocktakes().size(), b.auditEvents()==null?0:b.auditEvents().size());
  }
}
