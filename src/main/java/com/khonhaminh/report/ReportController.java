package com.khonhaminh.report;

import com.khonhaminh.expense.ExpenseRepository;
import com.khonhaminh.inventory.*;
import com.khonhaminh.product.ProductRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
 private final ProductRepository products;
 private final InventoryTransactionRepository txs;
 private final ExpenseRepository expenses;
 private final ReportService reports;
 private final DailyMovementExcelService dailyExcel;
 private final ProductHistoryExcelService productHistoryExcel;
 public ReportController(ProductRepository p,InventoryTransactionRepository t,ExpenseRepository e,ReportService reports,DailyMovementExcelService dailyExcel,ProductHistoryExcelService productHistoryExcel){products=p;txs=t;expenses=e;this.reports=reports;this.dailyExcel=dailyExcel;this.productHistoryExcel=productHistoryExcel;}

 @GetMapping("/dashboard") public Map<String,Object> dashboard(@RequestParam(required=false) Integer year,@RequestParam(required=false) Integer month){
   LocalDate now=LocalDate.now();int y=year==null?now.getYear():year,m=month==null?now.getMonthValue():month;YearMonth ym=YearMonth.of(y,m);LocalDateTime start=ym.atDay(1).atStartOfDay(),end=ym.plusMonths(1).atDay(1).atStartOfDay();
   var ps=products.findAll();var monthTx=txs.findByCreatedAtBetweenOrderByCreatedAtAsc(start,end).stream().filter(t->t.getStatus()==InventoryTransaction.Status.COMPLETED).toList();
   BigDecimal in=monthTx.stream().filter(t->t.getType()==InventoryTransaction.Type.IN).map(InventoryTransaction::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal out=monthTx.stream().filter(t->t.getType()==InventoryTransaction.Type.OUT).map(InventoryTransaction::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);
   BigDecimal stockValue=ps.stream().map(p->p.getQuantity().multiply(p.getUnitPrice())).reduce(BigDecimal.ZERO,BigDecimal::add);long low=ps.stream().filter(p->p.getQuantity().compareTo(p.getMinStock())<=0).count();
   BigDecimal exp=expenses.findByExpenseDateBetween(ym.atDay(1),ym.atEndOfMonth()).stream().map(e->e.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add);
   return Map.of("productTypes",ps.size(),"totalUnits",ps.stream().map(p->p.getQuantity()).reduce(BigDecimal.ZERO,BigDecimal::add),"lowStock",low,"stockValue",stockValue,"monthIn",in,"monthOut",out,"monthExpenses",exp,"year",y,"month",m);
 }

 @GetMapping("/period")
 public ReportService.PeriodReport period(@RequestParam(defaultValue="DAY") String period,
                                          @RequestParam(required=false) LocalDate date,
                                          @RequestParam(required=false) List<Long> productIds){
   return reports.build(period,date,productIds);
 }

 @GetMapping("/daily-inbound-excel")
 public ResponseEntity<byte[]> dailyInbound(@RequestParam(required=false) LocalDate date){
   byte[] body=dailyExcel.exportInbound(date);
   return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+dailyExcel.inboundFilename(date)+"\"")
       .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(body);
 }

 @GetMapping("/daily-outbound-excel")
 public ResponseEntity<byte[]> dailyOutbound(@RequestParam(required=false) LocalDate date){
   byte[] body=dailyExcel.exportOutbound(date);
   return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+dailyExcel.outboundFilename(date)+"\"")
       .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(body);
 }

 @PostMapping("/excel")
 public ResponseEntity<byte[]> excel(@RequestBody ReportService.ExportRequest request){
   byte[] body=reports.exportExcel(request);
   String filename=reports.exportFilename(request);
   return ResponseEntity.ok()
       .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"")
       .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
       .body(body);
 }

 @GetMapping(value="/product-history.xlsx",produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
 public ResponseEntity<byte[]> productHistoryExcel(@RequestParam Long productId,
     @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
     @RequestParam(defaultValue="ALL") String type){
   byte[] data=productHistoryExcel.build(productId,from,to,type);
   String fn="LICH-SU-HANG-"+productId+".xlsx";
   return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+fn+"\"").body(data);
 }
}
