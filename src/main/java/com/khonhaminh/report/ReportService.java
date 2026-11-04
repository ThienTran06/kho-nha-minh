package com.khonhaminh.report;

import com.khonhaminh.common.ApiException;
import com.khonhaminh.inventory.InventoryTransaction;
import com.khonhaminh.inventory.InventoryTransactionRepository;
import com.khonhaminh.product.Product;
import com.khonhaminh.product.ProductRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
  public enum Period { DAY, WEEK, MONTH }

  public record ProductReportRow(
      Long productId, String sku, String name, String unit, String category,
      BigDecimal openingStock, BigDecimal inQty, BigDecimal outQty,
      BigDecimal closingStock, BigDecimal currentStock) {}

  public record PeriodReport(
      String period, LocalDate anchorDate, LocalDate fromDate, LocalDate toDate,
      BigDecimal totalIn, BigDecimal totalOut, BigDecimal totalClosing,
      long selectedProducts, List<ProductReportRow> rows) {}

  public record ExportRequest(String period, LocalDate date, List<Long> productIds) {}

  private final ProductRepository products;
  private final InventoryTransactionRepository txs;

  public ReportService(ProductRepository products, InventoryTransactionRepository txs) {
    this.products = products;
    this.txs = txs;
  }

  @Transactional(readOnly = true)
  public PeriodReport build(String periodValue, LocalDate anchor, Collection<Long> productIds) {
    Period period = parsePeriod(periodValue);
    LocalDate date = anchor == null ? LocalDate.now() : anchor;
    Range range = range(period, date);

    List<Product> chosen = products.findAllByOrderByNameAsc();
    if (productIds != null && !productIds.isEmpty()) {
      Set<Long> wanted = new HashSet<>(productIds);
      chosen = chosen.stream().filter(p -> wanted.contains(p.getId())).toList();
    }

    LocalDateTime start = range.from().atStartOfDay();
    LocalDateTime end = range.to().plusDays(1).atStartOfDay();
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime laterStart = end.isAfter(now) ? now : end;
    LocalDateTime laterEnd = now.plusNanos(1);

    List<InventoryTransaction> periodTx = txs.findByCreatedAtBetweenOrderByCreatedAtAsc(start, end);
    List<InventoryTransaction> laterTx = laterStart.isBefore(laterEnd)
        ? txs.findByCreatedAtBetweenOrderByCreatedAtAsc(laterStart, laterEnd)
        : List.of();

    Map<Long, List<InventoryTransaction>> periodByProduct = completedByProduct(periodTx);
    Map<Long, List<InventoryTransaction>> laterByProduct = completedByProduct(laterTx);

    List<ProductReportRow> rows = new ArrayList<>();
    for (Product p : chosen) {
      List<InventoryTransaction> inPeriod = periodByProduct.getOrDefault(p.getId(), List.of());
      List<InventoryTransaction> afterPeriod = laterByProduct.getOrDefault(p.getId(), List.of());

      BigDecimal incoming = sum(inPeriod, InventoryTransaction.Type.IN);
      BigDecimal outgoing = sum(inPeriod, InventoryTransaction.Type.OUT);
      BigDecimal laterNet = net(afterPeriod);
      BigDecimal closing = p.getQuantity().subtract(laterNet);
      // OPENING là số tồn ban đầu, không tính vào "Nhập". Vì vậy nó được phản ánh trong Tồn đầu.
      BigDecimal opening = closing.subtract(incoming).add(outgoing);

      rows.add(new ProductReportRow(
          p.getId(), p.getSku(), p.getName(), p.getUnit(), p.getCategory(),
          opening, incoming, outgoing, closing, p.getQuantity()));
    }

    BigDecimal totalIn = rows.stream().map(ProductReportRow::inQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalOut = rows.stream().map(ProductReportRow::outQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalClosing = rows.stream().map(ProductReportRow::closingStock).reduce(BigDecimal.ZERO, BigDecimal::add);

    return new PeriodReport(period.name(), date, range.from(), range.to(), totalIn, totalOut,
        totalClosing, rows.size(), rows);
  }

  @Transactional(readOnly = true)
  public byte[] exportExcel(ExportRequest request) {
    if (request == null || request.productIds() == null || request.productIds().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Hãy chọn ít nhất một mặt hàng để đưa vào báo cáo");
    }
    PeriodReport report = build(request.period(), request.date(), request.productIds());
    if (report.rows().isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Không có mặt hàng phù hợp để xuất báo cáo");

    List<ProductReportRow> ingredients = report.rows().stream()
        .filter(r -> "Nguyên liệu / Gia vị".equalsIgnoreCase(r.category())).toList();
    List<ProductReportRow> tools = report.rows().stream()
        .filter(r -> "Công cụ / Dụng cụ".equalsIgnoreCase(r.category())).toList();
    List<ProductReportRow> drinks = report.rows().stream()
        .filter(r -> "Nước / Đồ uống".equalsIgnoreCase(r.category())).toList();
    List<ProductReportRow> other = report.rows().stream()
        .filter(r -> !"Nguyên liệu / Gia vị".equalsIgnoreCase(r.category())
            && !"Công cụ / Dụng cụ".equalsIgnoreCase(r.category())
            && !"Nước / Đồ uống".equalsIgnoreCase(r.category())).toList();

    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Styles styles = new Styles(wb);
      if (!ingredients.isEmpty()) writeSheet(wb, "Nguyên liệu - Gia vị", ingredients, report, styles);
      if (!tools.isEmpty()) writeSheet(wb, "Dụng cụ", tools, report, styles);
      if (!drinks.isEmpty()) writeSheet(wb, "Nước - Đồ uống", drinks, report, styles);
      if (!other.isEmpty()) writeSheet(wb, "Khác", other, report, styles);
      wb.write(out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được file Excel báo cáo");
    }
  }

  public String exportFilename(ExportRequest request) {
    Period p = parsePeriod(request == null ? null : request.period());
    LocalDate d = request == null || request.date() == null ? LocalDate.now() : request.date();
    String suffix = switch (p) {
      case DAY -> "ngay-" + d;
      case WEEK -> "tuan-" + range(p, d).from();
      case MONTH -> "thang-" + d.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    };
    return "bao-cao-kho-" + suffix + ".xlsx";
  }

  private Map<Long, List<InventoryTransaction>> completedByProduct(List<InventoryTransaction> list) {
    return list.stream()
        .filter(t -> t.getStatus() == InventoryTransaction.Status.COMPLETED)
        .collect(Collectors.groupingBy(t -> t.getProduct().getId()));
  }

  private BigDecimal sum(List<InventoryTransaction> list, InventoryTransaction.Type type) {
    return list.stream().filter(t -> t.getType() == type).map(InventoryTransaction::getQuantity)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal net(List<InventoryTransaction> list) {
    return list.stream().map(t -> t.getType() == InventoryTransaction.Type.OUT
        ? t.getQuantity().negate() : t.getQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Period parsePeriod(String value) {
    if (value == null || value.isBlank()) return Period.DAY;
    try { return Period.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
    catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Kỳ báo cáo phải là DAY, WEEK hoặc MONTH"); }
  }

  private record Range(LocalDate from, LocalDate to) {}
  private Range range(Period period, LocalDate anchor) {
    return switch (period) {
      case DAY -> new Range(anchor, anchor);
      case WEEK -> {
        LocalDate monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        yield new Range(monday, monday.plusDays(6));
      }
      case MONTH -> {
        YearMonth ym = YearMonth.from(anchor);
        yield new Range(ym.atDay(1), ym.atEndOfMonth());
      }
    };
  }

  private void writeSheet(Workbook wb, String name, List<ProductReportRow> rows, PeriodReport report, Styles s) {
    Sheet sh = wb.createSheet(name);
    sh.setDisplayGridlines(false);
    sh.createFreezePane(0, 7);

    Row title = sh.createRow(0);
    title.setHeightInPoints(28);
    Cell tc = title.createCell(0); tc.setCellValue("KHO NHÀ MÌNH — BÁO CÁO " + name.toUpperCase(Locale.ROOT)); tc.setCellStyle(s.title);
    sh.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

    Row period = sh.createRow(2);
    period.createCell(0).setCellValue("Kỳ báo cáo:"); period.getCell(0).setCellStyle(s.label);
    period.createCell(1).setCellValue(periodLabel(report)); period.getCell(1).setCellStyle(s.text);
    sh.addMergedRegion(new CellRangeAddress(2, 2, 1, 4));

    Row generated = sh.createRow(3);
    generated.createCell(0).setCellValue("Ngày tạo:"); generated.getCell(0).setCellStyle(s.label);
    generated.createCell(1).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))); generated.getCell(1).setCellStyle(s.text);

    BigDecimal catIn = rows.stream().map(ProductReportRow::inQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal catOut = rows.stream().map(ProductReportRow::outQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal catClosing = rows.stream().map(ProductReportRow::closingStock).reduce(BigDecimal.ZERO, BigDecimal::add);
    summaryCell(sh, 5, 0, "Đã nhập", catIn, s);
    summaryCell(sh, 5, 3, "Đã xuất", catOut, s);
    summaryCell(sh, 5, 6, "Tồn cuối kỳ", catClosing, s);

    String[] headers = {"STT", "Mã hàng", "Tên hàng", "ĐVT", "Tồn đầu", "Nhập", "Xuất", "Tồn cuối", "Ghi chú"};
    Row hr = sh.createRow(7);
    for (int i=0;i<headers.length;i++) { Cell c=hr.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(s.header); }

    int rowNum = 8;
    for (int i=0;i<rows.size();i++) {
      ProductReportRow r = rows.get(i);
      Row row = sh.createRow(rowNum++);
      set(row,0,i+1,s.center); set(row,1,blank(r.sku()),s.text); set(row,2,r.name(),s.text);
      set(row,3,r.unit(),s.center); setNumber(row,4,r.openingStock(),s.number); setNumber(row,5,r.inQty(),s.number);
      setNumber(row,6,r.outQty(),s.number); setNumber(row,7,r.closingStock(),s.number); set(row,8,"",s.text);
    }

    Row total = sh.createRow(rowNum);
    Cell label = total.createCell(0); label.setCellValue("TỔNG"); label.setCellStyle(s.totalLabel);
    sh.addMergedRegion(new CellRangeAddress(rowNum,rowNum,0,3));
    for (int col=4;col<=7;col++) {
      Cell c=total.createCell(col); c.setCellFormula("SUM("+colLetter(col)+(9)+":"+colLetter(col)+(rowNum)+")"); c.setCellStyle(s.totalNumber);
    }
    Cell note = total.createCell(8); note.setCellValue(""); note.setCellStyle(s.totalLabel);

    int[] widths = {7,16,34,12,14,14,14,14,28};
    for (int i=0;i<widths.length;i++) sh.setColumnWidth(i, widths[i]*256);
    sh.setAutoFilter(new CellRangeAddress(7, Math.max(7,rowNum-1), 0, 8));
  }

  private String periodLabel(PeriodReport r) {
    DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    if (r.fromDate().equals(r.toDate())) return r.fromDate().format(f);
    return r.fromDate().format(f) + " - " + r.toDate().format(f);
  }

  private void summaryCell(Sheet sh, int rowIndex, int col, String label, BigDecimal value, Styles s) {
    Row row = sh.getRow(rowIndex); if (row == null) row = sh.createRow(rowIndex);
    Cell lc=row.createCell(col); lc.setCellValue(label); lc.setCellStyle(s.summaryLabel);
    Cell vc=row.createCell(col+1); vc.setCellValue(value.doubleValue()); vc.setCellStyle(s.summaryNumber);
    sh.addMergedRegion(new CellRangeAddress(rowIndex,rowIndex,col+1,col+2));
  }

  private String blank(String s){ return s==null?"":s; }
  private void set(Row row,int col,Object value,CellStyle style){Cell c=row.createCell(col);c.setCellValue(String.valueOf(value));c.setCellStyle(style);}
  private void setNumber(Row row,int col,BigDecimal value,CellStyle style){Cell c=row.createCell(col);c.setCellValue(value.doubleValue());c.setCellStyle(style);}
  private String colLetter(int col){return Character.toString((char)('A'+col));}

  private static class Styles {
    final CellStyle title,label,text,center,number,header,summaryLabel,summaryNumber,totalLabel,totalNumber;
    Styles(Workbook wb){
      Font titleFont=wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short)16); titleFont.setColor(IndexedColors.WHITE.getIndex());
      title=wb.createCellStyle(); title.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); title.setFillPattern(FillPatternType.SOLID_FOREGROUND); title.setFont(titleFont); title.setVerticalAlignment(VerticalAlignment.CENTER);
      Font bold=wb.createFont();bold.setBold(true);
      label=wb.createCellStyle();label.setFont(bold);
      text=bordered(wb); text.setVerticalAlignment(VerticalAlignment.CENTER);
      center=bordered(wb);center.setAlignment(HorizontalAlignment.CENTER);center.setVerticalAlignment(VerticalAlignment.CENTER);
      number=bordered(wb);number.setDataFormat(wb.createDataFormat().getFormat("#,##0.###"));number.setAlignment(HorizontalAlignment.RIGHT);
      header=bordered(wb);header.setFont(bold);header.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);header.setAlignment(HorizontalAlignment.CENTER);header.setVerticalAlignment(VerticalAlignment.CENTER);
      summaryLabel=wb.createCellStyle();summaryLabel.setFont(bold);summaryLabel.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());summaryLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);summaryLabel.setBorderBottom(BorderStyle.THIN);summaryLabel.setBorderTop(BorderStyle.THIN);summaryLabel.setBorderLeft(BorderStyle.THIN);summaryLabel.setBorderRight(BorderStyle.THIN);
      summaryNumber=wb.createCellStyle();summaryNumber.cloneStyleFrom(summaryLabel);summaryNumber.setDataFormat(wb.createDataFormat().getFormat("#,##0.###"));summaryNumber.setAlignment(HorizontalAlignment.RIGHT);
      totalLabel=wb.createCellStyle();totalLabel.cloneStyleFrom(header);totalLabel.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      totalNumber=wb.createCellStyle();totalNumber.cloneStyleFrom(totalLabel);totalNumber.setDataFormat(wb.createDataFormat().getFormat("#,##0.###"));totalNumber.setAlignment(HorizontalAlignment.RIGHT);
    }
    private static CellStyle bordered(Workbook wb){CellStyle s=wb.createCellStyle();s.setBorderBottom(BorderStyle.THIN);s.setBorderTop(BorderStyle.THIN);s.setBorderLeft(BorderStyle.THIN);s.setBorderRight(BorderStyle.THIN);s.setWrapText(true);return s;}
  }
}
