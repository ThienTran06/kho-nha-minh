package com.khonhaminh.report;

import com.khonhaminh.common.ApiException;
import com.khonhaminh.inventory.InventoryTransaction;
import com.khonhaminh.inventory.InventoryTransactionRepository;
import com.khonhaminh.product.Product;
import com.khonhaminh.product.ProductRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductHistoryExcelService {
  private final ProductRepository products;
  private final InventoryTransactionRepository txs;
  private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  public ProductHistoryExcelService(ProductRepository products, InventoryTransactionRepository txs) {
    this.products = products; this.txs = txs;
  }

  private record Row(InventoryTransaction t, BigDecimal balanceAfter) {}

  @Transactional(readOnly = true)
  public byte[] build(Long productId, LocalDate from, LocalDate to, String type) {
    Product p = products.findById(productId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa"));
    List<InventoryTransaction> all = txs.findByProductIdOrderByCreatedAtAsc(productId);
    List<Row> computed = new ArrayList<>();
    BigDecimal balance = BigDecimal.ZERO;
    for (InventoryTransaction t : all) {
      if (t.getStatus() == InventoryTransaction.Status.COMPLETED) {
        boolean outbound = t.getType() == InventoryTransaction.Type.OUT || t.getType() == InventoryTransaction.Type.ADJUST_OUT;
        balance = outbound ? balance.subtract(t.getQuantity()) : balance.add(t.getQuantity());
      }
      computed.add(new Row(t, balance));
    }
    String wanted = type == null ? "ALL" : type.toUpperCase();
    List<Row> rows = computed.stream().filter(r -> {
      InventoryTransaction t = r.t();
      LocalDate d = t.getCreatedAt().toLocalDate();
      if (from != null && d.isBefore(from)) return false;
      if (to != null && d.isAfter(to)) return false;
      if ("IN".equals(wanted) && t.getType() != InventoryTransaction.Type.IN) return false;
      if ("OUT".equals(wanted) && t.getType() != InventoryTransaction.Type.OUT) return false;
      if ("OTHER".equals(wanted) && (t.getType() == InventoryTransaction.Type.IN || t.getType() == InventoryTransaction.Type.OUT)) return false;
      return true;
    }).toList();

    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sh = wb.createSheet("Lịch sử " + safeSheet(p.getName()));
      Font titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short)16);
      CellStyle title = wb.createCellStyle(); title.setFont(titleFont);
      CellStyle header = wb.createCellStyle();
      Font hf = wb.createFont(); hf.setBold(true); header.setFont(hf); header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      header.setBorderBottom(BorderStyle.THIN); header.setBorderTop(BorderStyle.THIN); header.setBorderLeft(BorderStyle.THIN); header.setBorderRight(BorderStyle.THIN);
      CellStyle number = wb.createCellStyle(); number.setDataFormat(wb.createDataFormat().getFormat("#,##0.###"));
      CellStyle money = wb.createCellStyle(); money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

      org.apache.poi.ss.usermodel.Row r0 = sh.createRow(0); Cell c0 = r0.createCell(0); c0.setCellValue("LỊCH SỬ NHẬP XUẤT - " + p.getName().toUpperCase()); c0.setCellStyle(title);
      sh.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,0,0,10));
      sh.createRow(1).createCell(0).setCellValue("Mã hàng: " + (p.getSku()==null?"":p.getSku()) + " | Đơn vị: " + p.getUnit() + " | Tồn hiện tại: " + p.getQuantity());
      sh.createRow(2).createCell(0).setCellValue("Khoảng ngày: " + (from==null?"Từ đầu":from.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) + " - " + (to==null?"Hiện tại":to.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));

      String[] heads={"STT","Ngày giờ","Mã phiếu","Loại","Số lượng","ĐVT","Tồn sau phiếu","Đơn giá","Thành tiền","Thông tin","Ghi chú","Trạng thái"};
      org.apache.poi.ss.usermodel.Row hr=sh.createRow(4);
      for(int i=0;i<heads.length;i++){Cell c=hr.createCell(i);c.setCellValue(heads[i]);c.setCellStyle(header);}
      int rn=5, stt=1;
      for(Row x:rows){
        InventoryTransaction t=x.t(); org.apache.poi.ss.usermodel.Row rr=sh.createRow(rn++);
        rr.createCell(0).setCellValue(stt++);
        rr.createCell(1).setCellValue(t.getCreatedAt().format(DT));
        rr.createCell(2).setCellValue(t.getCode());
        rr.createCell(3).setCellValue(label(t.getType()));
        Cell q=rr.createCell(4);q.setCellValue(t.getQuantity().doubleValue());q.setCellStyle(number);
        rr.createCell(5).setCellValue(t.getUnitSnapshot());
        Cell b=rr.createCell(6);b.setCellValue(x.balanceAfter().doubleValue());b.setCellStyle(number);
        if(t.getUnitCost()!=null){Cell uc=rr.createCell(7);uc.setCellValue(t.getUnitCost().doubleValue());uc.setCellStyle(money);Cell total=rr.createCell(8);total.setCellValue(t.getUnitCost().multiply(t.getQuantity()).doubleValue());total.setCellStyle(money);}
        rr.createCell(9).setCellValue(info(t));
        rr.createCell(10).setCellValue(t.getNote()==null?"":t.getNote());
        rr.createCell(11).setCellValue(t.getStatus()==InventoryTransaction.Status.COMPLETED?"Hoàn tất":"Đã sửa sai");
      }
      int[] widths={7,19,24,16,12,10,15,15,16,28,30,14};
      for(int i=0;i<widths.length;i++)sh.setColumnWidth(i,widths[i]*256);
      sh.createFreezePane(0,5);
      wb.write(out); return out.toByteArray();
    } catch (Exception e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"Không tạo được file lịch sử Excel"); }
  }

  private String label(InventoryTransaction.Type t){return switch(t){case IN->"Nhập";case OUT->"Xuất";case OPENING->"Tồn đầu";case ADJUST_IN,ADJUST_OUT->"Điều chỉnh";};}
  private String info(InventoryTransaction t){
    if(t.getType()==InventoryTransaction.Type.IN) return t.getSupplier()==null?"":t.getSupplier();
    if(t.getType()==InventoryTransaction.Type.OUT){String a=t.getDepartment()==null?"":t.getDepartment();String b=t.getEmployeeName()==null?"":t.getEmployeeName();return (a+(a.isBlank()||b.isBlank()?"":" - ")+b).trim();}
    return "";
  }
  private String safeSheet(String s){String v=s.replaceAll("[\\\\/?*\\[\\]:]"," ").trim();return v.length()>22?v.substring(0,22):v;}
}
