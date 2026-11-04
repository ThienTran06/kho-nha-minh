package com.khonhaminh.product;

import com.khonhaminh.common.ApiException;
import com.khonhaminh.inventory.InventoryService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelImportService {
  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
  private final ProductRepository products;
  private final InventoryService inventory;

  public ExcelImportService(ProductRepository products, InventoryService inventory) {
    this.products = products;
    this.inventory = inventory;
  }

  public enum ImportMode { SKIP, UPDATE_INFO, SYNC_STOCK }

  public record ImportRow(String sheet, int row, String category, String sku, String name,
                          String unit, BigDecimal stock, String note) {}
  public record PreviewResult(int found, int canAdd, int existing, int invalid,
                              List<String> sheets, List<ImportRow> sample, List<String> warnings,
                              String detectedFormat) {}
  public record ImportResult(int added, int updated, int stockSynced, int skipped, int invalid,
                             List<String> warnings) {}
  private record ParseResult(List<ImportRow> rows, int invalid, List<String> sheets,
                             List<String> warnings, String detectedFormat) {}
  private record Match(Product product, boolean bySku) {}

  public PreviewResult preview(MultipartFile file) {
    ParseResult parsed = parse(file);
    int fresh = 0, existing = 0;
    Set<String> fileKeys = new HashSet<>();
    for (ImportRow r : parsed.rows()) {
      String key = identityKey(r);
      if (!fileKeys.add(key) || findExisting(r).isPresent()) existing++; else fresh++;
    }
    return new PreviewResult(parsed.rows().size(), fresh, existing, parsed.invalid(),
        parsed.sheets(), parsed.rows().stream().limit(10).toList(), parsed.warnings(), parsed.detectedFormat());
  }

  @Transactional
  public ImportResult importFile(MultipartFile file, ImportMode mode) {
    ParseResult parsed = parse(file);
    ImportMode safeMode = mode == null ? ImportMode.SKIP : mode;
    int added = 0, updated = 0, stockSynced = 0, skipped = 0;
    Set<String> processed = new HashSet<>();

    for (ImportRow r : parsed.rows()) {
      String key = identityKey(r);
      if (!processed.add(key)) { skipped++; continue; }

      Optional<Match> match = findExisting(r);
      if (match.isEmpty()) {
        Product p = new Product();
        p.setSku(clean(r.sku()));
        p.setName(r.name().trim());
        p.setUnit(r.unit().trim());
        p.setCategory(r.category());
        p.setCategoryLocked(false);
        p.setQuantity(BigDecimal.ZERO);
        p.setMinStock(BigDecimal.ZERO);
        p.setUnitPrice(BigDecimal.ZERO);
        p.setNote(clean(r.note()));
        p = products.save(p);
        if (r.stock().signum() > 0) {
          inventory.createOpening(p.getId(), r.stock(), "Tồn hiện tại nhập từ Excel - " + r.sheet());
        }
        added++;
        continue;
      }

      Product p = match.get().product();
      if (safeMode == ImportMode.SKIP) { skipped++; continue; }

      updateInfoSafely(p, r);
      updated++;
      if (safeMode == ImportMode.SYNC_STOCK && p.getQuantity().compareTo(r.stock()) != 0) {
        inventory.syncStockFromExcel(p.getId(), r.stock(), "Đồng bộ theo Excel - " + r.sheet() + " dòng " + r.row());
        stockSynced++;
      }
    }
    return new ImportResult(added, updated, stockSynced, skipped, parsed.invalid(), parsed.warnings());
  }

  private void updateInfoSafely(Product p, ImportRow r) {
    if (!p.isCategoryLocked()) p.setCategory(r.category());
    if (!blank(r.note())) p.setNote(r.note().trim());
    // Mã hàng chỉ điền vào khi hàng hiện tại chưa có mã, tránh đổi mã đang dùng.
    if (blank(p.getSku()) && !blank(r.sku()) && !products.existsBySkuIgnoreCase(r.sku())) p.setSku(r.sku().trim());
  }

  private Optional<Match> findExisting(ImportRow r) {
    if (!blank(r.sku())) {
      Optional<Product> bySku = products.findFirstBySkuIgnoreCase(r.sku().trim());
      if (bySku.isPresent()) return Optional.of(new Match(bySku.get(), true));
    }
    return products.findFirstByNameIgnoreCaseAndUnitIgnoreCase(r.name().trim(), r.unit().trim())
        .map(p -> new Match(p, false));
  }

  private String identityKey(ImportRow r) {
    if (!blank(r.sku())) return "sku|" + norm(r.sku());
    return "name|" + norm(r.name()) + "|" + norm(r.unit());
  }

  private ParseResult parse(MultipartFile file) {
    if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Chưa chọn file Excel");
    String original = Optional.ofNullable(file.getOriginalFilename()).orElse("");
    String name = original.toLowerCase(Locale.ROOT);
    if (!(name.endsWith(".xlsx") || name.endsWith(".xls")))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn file Excel .xlsx hoặc .xls");

    List<ImportRow> rows = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> sheetNames = new ArrayList<>();
    int invalid = 0;
    String format = name.endsWith(".xls") ? "Excel 97-2003 (.xls)" : "Excel (.xlsx)";

    try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
      FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
      DataFormatter formatter = new DataFormatter(new Locale("vi", "VN"));
      for (Sheet sheet : wb) {
        Header header = findHeader(sheet, formatter, evaluator);
        if (header == null) continue;
        sheetNames.add(sheet.getSheetName());
        String category = categoryFor(sheet.getSheetName());
        int blankRun = 0;
        for (int r = header.rowIndex + 1; r <= sheet.getLastRowNum(); r++) {
          Row row = sheet.getRow(r);
          if (row == null) { if (++blankRun > 25) break; continue; }
          String productName = text(row, header.name, formatter, evaluator);
          if (blank(productName) || looksLikeTotal(productName)) { if (++blankRun > 25) break; continue; }
          blankRun = 0;
          String unit = text(row, header.unit, formatter, evaluator);
          if (blank(unit)) {
            invalid++;
            addWarning(warnings, sheet.getSheetName()+" dòng "+(r+1)+": có tên hàng nhưng thiếu ĐVT");
            continue;
          }
          BigDecimal ending = number(row, header.ending, evaluator, formatter);
          BigDecimal opening = number(row, header.opening, evaluator, formatter);
          BigDecimal inQty = number(row, header.inQty, evaluator, formatter);
          BigDecimal outQty = number(row, header.outQty, evaluator, formatter);
          BigDecimal stock = ending != null ? ending : nz(opening).add(nz(inQty)).subtract(nz(outQty));
          if (stock.signum() < 0) {
            invalid++;
            addWarning(warnings, sheet.getSheetName()+" dòng "+(r+1)+": tồn cuối âm nên chưa nhập “"+productName.trim()+"”");
            continue;
          }
          rows.add(new ImportRow(sheet.getSheetName(), r+1, category,
              clean(text(row, header.sku, formatter, evaluator)), productName.trim(), unit.trim(),
              stock.setScale(Math.min(3, Math.max(0, stock.scale())), RoundingMode.HALF_UP).stripTrailingZeros(),
              clean(text(row, header.note, formatter, evaluator))));
        }
      }
    } catch (ApiException e) { throw e; }
      catch (Exception e) {
        throw new ApiException(HttpStatus.BAD_REQUEST,
            "Không đọc được file Excel “"+original+"”. File .xls/.xlsx phải là file Excel thật, không đặt mật khẩu và không bị hỏng.");
      }

    if (sheetNames.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,
        "Không tìm thấy bảng kho phù hợp. Phần mềm cần các cột Tên hàng/Tên NVL, ĐVT và Tồn cuối (hoặc Tồn đầu + Nhập - Xuất).");
    if (rows.isEmpty() && invalid == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "File Excel không có mặt hàng để nhập");
    return new ParseResult(rows, invalid, sheetNames, warnings, format);
  }

  private record Header(int rowIndex, int sku, int name, int unit, int opening, int inQty, int outQty, int ending, int note) {}
  private Header findHeader(Sheet sheet, DataFormatter f, FormulaEvaluator e) {
    int max = Math.min(sheet.getLastRowNum(), 60);
    for (int r = 0; r <= max; r++) {
      Row row = sheet.getRow(r); if (row == null) continue;
      Map<String,Integer> cols = new LinkedHashMap<>();
      short last = row.getLastCellNum();
      if (last < 0) continue;
      for (int c = 0; c < last; c++) {
        Cell cell = row.getCell(c);
        if (cell == null) continue;
        String h = norm(f.formatCellValue(cell, e));
        if (!h.isBlank()) cols.put(h, c);
      }
      int name = col(cols, "ten nvl", "ten nguyen vat lieu", "ten nguyen lieu", "ten gia vi",
          "ten ccdc", "ten cong cu", "ten dung cu", "ten hang", "ten vat tu", "mat hang");
      int unit = col(cols, "dvt", "don vi tinh", "don vi");
      int ending = col(cols, "ton cuoi", "ton cuoi ky", "so luong ton", "ton kho", "con lai");
      int opening = col(cols, "ton dau", "ton dau ky", "dau ky");
      int inQty = col(cols, "nhap", "nhap kho", "so luong nhap");
      int outQty = col(cols, "xuat", "xuat kho", "so luong xuat");
      // Chấp nhận file không có Tồn cuối nếu đủ ba cột để tự tính.
      boolean canCalculate = opening >= 0 || inQty >= 0 || outQty >= 0;
      if (name >= 0 && unit >= 0 && (ending >= 0 || canCalculate)) {
        return new Header(r, col(cols, "ma hang", "ma vat tu", "ma ccdc", "ma nvl", "code"),
            name, unit, opening, inQty, outQty, ending, col(cols, "ghi chu", "dien giai", "note"));
      }
    }
    return null;
  }

  private int col(Map<String,Integer> cols, String... names) {
    for (String n : names) {
      String key = norm(n);
      if (cols.containsKey(key)) return cols.get(key);
    }
    for (String n : names) {
      String key = norm(n);
      for (var e : cols.entrySet()) if (e.getKey().contains(key) || key.contains(e.getKey())) return e.getValue();
    }
    return -1;
  }

  private String text(Row row, int col, DataFormatter f, FormulaEvaluator e) {
    if (col < 0) return null; Cell c = row.getCell(col); if (c == null) return null;
    String s = f.formatCellValue(c,e); return s == null ? null : s.trim();
  }
  private BigDecimal number(Row row, int col, FormulaEvaluator e, DataFormatter f) {
    if (col < 0) return null; Cell c = row.getCell(col); if (c == null) return null;
    try {
      CellValue v = e.evaluate(c);
      if (v != null && v.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(v.getNumberValue());
      String s = f.formatCellValue(c,e); if (blank(s)) return null;
      s = s.trim().replace(" ", "");
      // Hỗ trợ cả 1.234,5 và 1,234.5; kho thường là số lượng nên ưu tiên phần thập phân cuối cùng.
      if (s.contains(",") && s.contains(".")) {
        if (s.lastIndexOf(',') > s.lastIndexOf('.')) s = s.replace(".", "").replace(',', '.');
        else s = s.replace(",", "");
      } else if (s.contains(",")) s = s.replace(',', '.');
      return new BigDecimal(s);
    } catch (Exception ignored) { return null; }
  }
  private String categoryFor(String sheet) {
    String n = norm(sheet);
    if (n.contains("nuoc") || n.contains("do uong") || n.contains("bia") || n.contains("beverage")) return "Nước / Đồ uống";
    if (n.contains("ccdc") || n.contains("cong cu") || n.contains("dung cu")) return "Công cụ / Dụng cụ";
    if (n.contains("nvl") || n.contains("nguyen vat lieu") || n.contains("nguyen lieu") || n.contains("gia vi"))
      return "Nguyên liệu / Gia vị";
    return sheet.trim().isEmpty() ? "Khác" : sheet.trim();
  }
  private boolean looksLikeTotal(String s) {
    String n = norm(s);
    return n.equals("tong") || n.startsWith("tong cong") || n.startsWith("cong") || n.startsWith("tong so");
  }
  private void addWarning(List<String> warnings, String warning) { if (warnings.size() < 12) warnings.add(warning); }
  private static BigDecimal nz(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
  private static boolean blank(String s){return s==null||s.trim().isEmpty();}
  private static String clean(String s){return blank(s)?null:s.trim();}
  private static String norm(String s){
    if(s==null) return "";
    String n=Normalizer.normalize(s,Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
    n=DIACRITICS.matcher(n).replaceAll("").replace('đ','d');
    return n.replaceAll("[^a-z0-9]+"," ").trim();
  }
}
