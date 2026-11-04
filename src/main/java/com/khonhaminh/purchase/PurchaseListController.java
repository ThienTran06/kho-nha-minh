package com.khonhaminh.purchase;
import com.khonhaminh.product.Product;
import com.khonhaminh.product.ProductRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/purchases")
public class PurchaseListController {
 private final ProductRepository repo; public PurchaseListController(ProductRepository r){repo=r;}
 public record Suggestion(Long id,String name,String sku,String unit,String category,BigDecimal currentStock,BigDecimal minStock,BigDecimal targetStock,BigDecimal suggestedQuantity){}
 @GetMapping("/suggestions") public List<Suggestion> suggestions(){return repo.findAllByOrderByNameAsc().stream().filter(p->p.getQuantity().compareTo(p.getMinStock())<=0).map(this::of).toList();}
 private Suggestion of(Product p){BigDecimal target=p.getTargetStock()!=null&&p.getTargetStock().signum()>0?p.getTargetStock():p.getMinStock().multiply(BigDecimal.valueOf(2));if(target.compareTo(p.getMinStock())<0)target=p.getMinStock();BigDecimal need=target.subtract(p.getQuantity()).max(BigDecimal.ZERO);return new Suggestion(p.getId(),p.getName(),p.getSku(),p.getUnit(),p.getCategory(),p.getQuantity(),p.getMinStock(),target,need);}
 @PostMapping("/excel") public ResponseEntity<byte[]> excel(@RequestBody List<Long> ids) throws Exception{
   List<Product> ps=repo.findAllById(ids);try(Workbook wb=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    Sheet sh=wb.createSheet("Danh sach can mua");Row title=sh.createRow(0);title.createCell(0).setCellValue("DANH SÁCH CẦN MUA - "+LocalDate.now());Row h=sh.createRow(2);String[] cols={"STT","Mã hàng","Tên hàng","Nhóm","ĐVT","Tồn hiện tại","Mức tối thiểu","Mức muốn có","Số lượng đề nghị mua","Ghi chú"};for(int i=0;i<cols.length;i++)h.createCell(i).setCellValue(cols[i]);int r=3,n=1;for(Product p:ps){Suggestion s=of(p);Row row=sh.createRow(r++);row.createCell(0).setCellValue(n++);row.createCell(1).setCellValue(p.getSku()==null?"":p.getSku());row.createCell(2).setCellValue(p.getName());row.createCell(3).setCellValue(p.getCategory());row.createCell(4).setCellValue(p.getUnit());row.createCell(5).setCellValue(p.getQuantity().doubleValue());row.createCell(6).setCellValue(p.getMinStock().doubleValue());row.createCell(7).setCellValue(s.targetStock().doubleValue());row.createCell(8).setCellValue(s.suggestedQuantity().doubleValue());row.createCell(9).setCellValue("");}for(int i=0;i<cols.length;i++)sh.autoSizeColumn(i);wb.write(out);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"danh-sach-can-mua.xlsx\"").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(out.toByteArray());}
 }
}
