package com.khonhaminh.stocktake;
import com.khonhaminh.audit.AuditLogService;
import com.khonhaminh.common.ApiException;
import com.khonhaminh.inventory.InventoryService;
import com.khonhaminh.inventory.InventoryTransaction;
import com.khonhaminh.product.Product;
import com.khonhaminh.product.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class StocktakeService {
 private final ProductRepository products; private final StocktakeRepository repo; private final InventoryService inventory; private final AuditLogService audit;
 public StocktakeService(ProductRepository p,StocktakeRepository r,InventoryService i,AuditLogService a){products=p;repo=r;inventory=i;audit=a;}
 @Transactional public Stocktake count(Long productId,BigDecimal actual,String note,LocalDateTime countedAt){
   if(actual==null||actual.signum()<0)throw new ApiException(HttpStatus.BAD_REQUEST,"Số đếm thực tế không hợp lệ");
   Product p=products.findLockedById(productId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy hàng hóa"));
   BigDecimal system=p.getQuantity();BigDecimal diff=actual.subtract(system);LocalDateTime at=countedAt==null?LocalDateTime.now():countedAt;
   if(at.isAfter(LocalDateTime.now().plusMinutes(5)))throw new ApiException(HttpStatus.BAD_REQUEST,"Ngày kiểm kê không thể ở tương lai");
   if(diff.signum()!=0){InventoryTransaction.Type type=diff.signum()>0?InventoryTransaction.Type.ADJUST_IN:InventoryTransaction.Type.ADJUST_OUT;inventory.transact(productId,type,diff.abs(),null,"Kiểm kê: "+(note==null?"":note),null,null,false,null,null,at);}
   Stocktake s=new Stocktake();s.setProduct(p);s.setProductNameSnapshot(p.getName());s.setUnitSnapshot(p.getUnit());s.setSystemQuantity(system);s.setActualQuantity(actual);s.setDifferenceQuantity(diff);s.setNote(note==null||note.isBlank()?null:note.trim());s.setCountedAt(at);s=repo.save(s);
   audit.log("STOCKTAKE","PRODUCT",productId,"Kiểm kê "+p.getName()+": hệ thống "+system+" "+p.getUnit()+", thực tế "+actual+" "+p.getUnit()+", chênh "+diff);
   return s;
 }
}
