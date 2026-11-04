package com.khonhaminh.product;
import com.khonhaminh.common.ApiException;
import com.khonhaminh.inventory.InventoryService;
import com.khonhaminh.audit.AuditLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/products")
public class ProductController {
 private final ProductRepository repo; private final InventoryService inventory; private final AuditLogService audit;
 public ProductController(ProductRepository r,InventoryService i,AuditLogService a){repo=r;inventory=i;audit=a;}
 public record ProductRequest(String sku,@NotBlank(message="Tên hàng không được để trống") @Size(max=180) String name,@NotBlank(message="Đơn vị không được để trống") @Size(max=60) String unit,@Size(max=80) String category,@DecimalMin(value="0.0") BigDecimal minStock,@DecimalMin(value="0.0") BigDecimal unitPrice,@DecimalMin(value="0.0") BigDecimal targetStock,@Size(max=60) String packageUnit,@DecimalMin(value="0.0") BigDecimal unitsPerPackage,@DecimalMin(value="0.0") BigDecimal openingStock,@Size(max=500) String note){}
 @GetMapping public List<Product> list(){return repo.findAllByOrderByNameAsc();}
 @PostMapping @Transactional public Product create(@Valid @RequestBody ProductRequest r){
  String sku=clean(r.sku()); if(sku!=null&&repo.existsBySkuIgnoreCase(sku))throw new ApiException(HttpStatus.CONFLICT,"Mã hàng đã tồn tại"); if(repo.existsByNameIgnoreCaseAndUnitIgnoreCase(r.name().trim(),r.unit().trim()))throw new ApiException(HttpStatus.CONFLICT,"Đã có mặt hàng cùng tên và đơn vị tính. Hãy tìm lại trong Hàng trong kho để tránh tạo trùng.");
  Product p=new Product();p.setSku(sku);p.setName(r.name().trim());p.setUnit(r.unit().trim());p.setCategory(category(r.category()));p.setCategoryLocked(true);p.setMinStock(nz(r.minStock()));p.setUnitPrice(nz(r.unitPrice()));p.setTargetStock(nz(r.targetStock()));applyPackage(p,r.packageUnit(),r.unitsPerPackage());p.setNote(clean(r.note()));p.setQuantity(BigDecimal.ZERO);p=repo.save(p);
  if(r.openingStock()!=null&&r.openingStock().signum()>0) inventory.createOpening(p.getId(),r.openingStock(),"Tồn đầu kỳ"); audit.log("PRODUCT_CREATE","PRODUCT",p.getId(),"Thêm mặt hàng "+p.getName()); return p;
 }
 @PutMapping("/{id}") @Transactional public Product update(@PathVariable Long id,@Valid @RequestBody ProductRequest r){Product p=repo.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy hàng hóa")); if(repo.existsByNameIgnoreCaseAndUnitIgnoreCaseAndIdNot(r.name().trim(),r.unit().trim(),id))throw new ApiException(HttpStatus.CONFLICT,"Đã có mặt hàng cùng tên và đơn vị tính. Hãy dùng mặt hàng đã có để tránh trùng."); String sku=clean(r.sku()); if(sku!=null&&!sku.equalsIgnoreCase(p.getSku()==null?"":p.getSku())&&repo.existsBySkuIgnoreCase(sku))throw new ApiException(HttpStatus.CONFLICT,"Mã hàng đã tồn tại");p.setSku(sku);p.setName(r.name().trim());p.setUnit(r.unit().trim());p.setCategory(category(r.category()));p.setCategoryLocked(true);p.setMinStock(nz(r.minStock()));p.setUnitPrice(nz(r.unitPrice()));p.setTargetStock(nz(r.targetStock()));applyPackage(p,r.packageUnit(),r.unitsPerPackage());p.setNote(clean(r.note()));audit.log("PRODUCT_UPDATE","PRODUCT",p.getId(),"Sửa mặt hàng "+p.getName());return p;}

 public record OpeningStockView(BigDecimal openingStock){}
 public record OpeningStockRequest(@NotNull @DecimalMin(value="0.0") BigDecimal openingStock,@Size(max=500) String note){}
 @GetMapping("/{id}/opening-stock") public OpeningStockView openingStock(@PathVariable Long id){return new OpeningStockView(inventory.getOpeningStock(id));}
 @PutMapping("/{id}/opening-stock") public OpeningStockView updateOpeningStock(@PathVariable Long id,@Valid @RequestBody OpeningStockRequest r){inventory.setOpeningStock(id,r.openingStock(),r.note());return new OpeningStockView(inventory.getOpeningStock(id));}
 @DeleteMapping("/{id}") public void delete(@PathVariable Long id){String name=repo.findById(id).map(Product::getName).orElse("#"+id);inventory.deleteProductIfNoInOutHistory(id);audit.log("PRODUCT_DELETE","PRODUCT",id,"Xóa mặt hàng "+name);}
 static void applyPackage(Product p,String unit,BigDecimal factor){String u=clean(unit);BigDecimal f=nz(factor);if(u==null||f.compareTo(BigDecimal.ONE)<=0){p.setPackageUnit(null);p.setUnitsPerPackage(BigDecimal.ZERO);}else{if(u.equalsIgnoreCase(p.getUnit()))throw new ApiException(HttpStatus.BAD_REQUEST,"Đơn vị thùng/két phải khác đơn vị tồn kho");p.setPackageUnit(u);p.setUnitsPerPackage(f);}}
 static BigDecimal nz(BigDecimal v){return v==null?BigDecimal.ZERO:v;} static String clean(String s){return s==null||s.trim().isEmpty()?null:s.trim();} static String category(String s){String v=clean(s);return v==null?"Khác":v;}
}
