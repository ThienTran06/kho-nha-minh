package com.khonhaminh.product;

import com.khonhaminh.audit.AuditLogService;
import com.khonhaminh.common.ApiException;
import com.khonhaminh.inventory.InventoryService;
import com.khonhaminh.inventory.InventoryTransaction;
import com.khonhaminh.inventory.InventoryTransactionRepository;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Import riêng file NƯỚC thực tế của quán.
 *
 * File có nhiều bảng phụ và nhiều vùng theo dõi. Chỉ đọc bảng
 * "BÁO CÁO XUẤT NHẬP TỒN" (khối màu vàng trong file mẫu), ví dụ NGÀY 7/9.
 * Không đọc các bảng kiểm đếm CÂY/TỦ/LẺ bên phải và không đọc sheet theo dõi tháng.
 */
@Service
public class DrinkMonthlyImportService {
  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
  private static final Pattern DATE_PATTERN = Pattern.compile("(?i)(?:ngay\\s*)?(\\d{1,2})\\s*/\\s*(\\d{1,2})");

  private final ProductRepository products;
  private final InventoryService inventory;
  private final InventoryTransactionRepository txs;
  private final AuditLogService audit;

  public DrinkMonthlyImportService(ProductRepository products, InventoryService inventory,
      InventoryTransactionRepository txs, AuditLogService audit) {
    this.products = products;
    this.inventory = inventory;
    this.txs = txs;
    this.audit = audit;
  }

  public record PreviewRow(String name, String unit, BigDecimal openingStock, BigDecimal inQty,
      BigDecimal outQty, BigDecimal htQty, BigDecimal closingStock, String note) {}
  public record PreviewResult(String sheet, String reportDate, int products, int newProducts,
      int existingProducts, BigDecimal totalIn, BigDecimal totalOut, BigDecimal totalHt,
      List<String> warnings, List<PreviewRow> sample) {}
  public record ImportResult(int productsCreated, int productsMatched, int openingStocksCreated,
      int movementsImported, int adjustmentsImported, int stocksSynced, int duplicatesSkipped,
      int rowsSkippedBecauseNewerHistory, String reportDate, List<String> warnings) {}

  private record DailyRow(String name, String unit, BigDecimal opening, BigDecimal inQty,
      BigDecimal outQty, BigDecimal htQty, BigDecimal closing, String note) {}
  private record Parsed(String sheet, LocalDate date, List<DailyRow> rows, List<String> warnings) {}
  private record Block(Sheet sheet, int titleRow, int headerRow, int nameCol, int unitCol,
      int openingCol, int inCol, int outCol, int htCol, int closingCol, int noteCol,
      int startCol, int endCol, int day, int month) {}

  public PreviewResult preview(MultipartFile file, String targetDate) {
    Parsed p = parse(file, targetDate);
    int newCount = 0, existing = 0;
    BigDecimal tin = BigDecimal.ZERO, tout = BigDecimal.ZERO, tht = BigDecimal.ZERO;
    List<PreviewRow> sample = new ArrayList<>();
    for (DailyRow row : p.rows()) {
      if (products.findFirstByNameIgnoreCase(row.name()).isPresent()) existing++; else newCount++;
      tin = tin.add(nz(row.inQty()));
      tout = tout.add(nz(row.outQty()));
      tht = tht.add(nz(row.htQty()));
      if (sample.size() < 10) {
        sample.add(new PreviewRow(row.name(), row.unit(), row.opening(), nz(row.inQty()),
            nz(row.outQty()), nz(row.htQty()), row.closing(), row.note()));
      }
    }
    return new PreviewResult(p.sheet(), p.date().toString(), p.rows().size(), newCount, existing,
        tin, tout, tht, p.warnings(), sample);
  }

  @Transactional
  public ImportResult importFile(MultipartFile file, String targetDate) {
    Parsed parsed = parse(file, targetDate);
    int created = 0, matched = 0, openings = 0, synced = 0, duplicates = 0, newerSkipped = 0;
    List<String> warnings = new ArrayList<>(parsed.warnings());
    String dateLabel = String.format("%02d/%02d/%d", parsed.date().getDayOfMonth(),
        parsed.date().getMonthValue(), parsed.date().getYear());
    String batchNote = "Chốt tồn cuối Nước ngày " + dateLabel;

    for (DailyRow row : parsed.rows()) {
      if (row.closing() == null || row.closing().signum() < 0) {
        addWarning(warnings, "Bỏ qua “" + row.name() + "” vì không có Tồn cuối hợp lệ.");
        continue;
      }

      Optional<Product> found = products.findFirstByNameIgnoreCase(row.name());
      Product p;
      boolean isNew = found.isEmpty();
      if (isNew) {
        if (blank(row.unit())) {
          addWarning(warnings, "Bỏ qua “" + row.name() + "” vì không có ĐVT.");
          continue;
        }
        p = new Product();
        p.setName(row.name());
        p.setUnit(row.unit());
        p.setCategory("Nước / Đồ uống");
        p.setCategoryLocked(false);
        p.setQuantity(BigDecimal.ZERO);
        p.setMinStock(BigDecimal.ZERO);
        p.setUnitPrice(BigDecimal.ZERO);
        p.setTargetStock(BigDecimal.ZERO);
        p.setUnitsPerPackage(BigDecimal.ZERO);
        p.setNote("Tạo từ Tồn cuối báo cáo Nước ngày " + dateLabel);
        p = products.save(p);
        created++;
      } else {
        p = found.get();
        matched++;
        if (!p.isCategoryLocked()) p.setCategory("Nước / Đồ uống");
      }

      // Khối vàng là một SNAPSHOT chốt kho. Yêu cầu nghiệp vụ: cột "Còn lại"
      // trong phần mềm phải bằng CHÍNH XÁC cột "Tồn cuối" của NGÀY ghi trên khối.
      // Không dựng lại Tồn đầu/Nhập/Xuất/HT thành các phiếu vì file có nhiều vùng
      // công thức phụ và việc cộng lại các cột đó từng làm sai số liệu.
      boolean hadNewerHistory = hasCompletedHistoryAfter(p.getId(), parsed.date());
      if (hadNewerHistory) {
        newerSkipped++;
        addWarning(warnings, "Không ghi đè “" + row.name() + "” vì đã có phiếu sau ngày "
            + dateLabel + ". Tồn cuối trong file là " + cleanQty(row.closing()) + " " + p.getUnit() + ".");
        continue;
      }

      BigDecimal target = cleanQty(row.closing());
      BigDecimal before = p.getQuantity() == null ? BigDecimal.ZERO : p.getQuantity();
      int cmp = target.compareTo(before);
      if (cmp == 0) {
        duplicates++;
        continue;
      }

      String sourceKey = "DRINK_DAILY_CLOSING|" + parsed.date() + "|" + norm(row.name());
      BigDecimal diff = target.subtract(before).abs();
      InventoryTransaction.Type type;
      if (isNew && before.signum() == 0 && target.signum() > 0 && !txs.existsByProductId(p.getId())) {
        type = InventoryTransaction.Type.OPENING;
        if (inventory.importMovement(p.getId(), type, target,
            parsed.date().atTime(23, 59), sourceKey, batchNote + " - Tồn cuối")) openings++;
        else duplicates++;
      } else {
        type = cmp > 0 ? InventoryTransaction.Type.ADJUST_IN : InventoryTransaction.Type.ADJUST_OUT;
        if (inventory.importMovement(p.getId(), type, diff,
            parsed.date().atTime(23, 59), sourceKey, batchNote + ": " + before + " → " + target)) synced++;
        else {
          // Nếu sourceKey đã tồn tại do import lại, vẫn bảo đảm tồn hiện tại đúng snapshot.
          // Không tạo thêm phiếu trùng; chỉ cảnh báo nếu số hiện tại đã khác.
          duplicates++;
        }
      }
    }

    audit.log("DRINK_DAILY_IMPORT", "SYSTEM", null,
        "Chốt Tồn cuối Nước ngày " + dateLabel + ": " + created + " hàng mới, "
            + openings + " tồn đầu snapshot, " + synced + " điều chỉnh về Tồn cuối");
    return new ImportResult(created, matched, openings, 0, synced, synced, duplicates,
        newerSkipped, parsed.date().toString(), warnings);
  }


  public record UndoPreview(boolean available, String reportDate, int transactions,
      int productsAffected, int removableProducts, List<String> warnings) {}
  public record UndoResult(String reportDate, int transactionsRemoved,
      int productsRestored, int productsRemoved, List<String> warnings) {}

  public UndoPreview previewUndoLast() {
    Batch batch = findLatestDrinkBatch();
    if (batch == null) return new UndoPreview(false, null, 0, 0, 0,
        List.of("Chưa tìm thấy lần import Nước nào có thể hoàn tác."));
    List<String> warnings = new ArrayList<>();
    Set<Long> productIds = new LinkedHashSet<>();
    int removable = 0;
    long maxBatchId = batch.transactions().stream().mapToLong(InventoryTransaction::getId).max().orElse(0L);
    for (InventoryTransaction t : batch.transactions()) productIds.add(t.getProduct().getId());
    for (Long productId : productIds) {
      Product p = products.findById(productId).orElse(null);
      if (p == null) continue;
      boolean hasNewer = txs.findByProductIdOrderByCreatedAtAsc(productId).stream()
          .anyMatch(t -> t.getId() > maxBatchId && !belongsToBatch(t, batch));
      if (hasNewer) addWarning(warnings, "“" + p.getName()
          + "” đã có phiếu phát sinh sau lần import này; thao tác hoàn tác sẽ bị chặn để tránh làm sai tồn.");
      if (isImportCreatedProduct(p) && txs.findByProductIdOrderByCreatedAtAsc(productId).stream()
          .allMatch(t -> belongsToBatch(t, batch))) removable++;
    }
    return new UndoPreview(true, batch.date().toString(), batch.transactions().size(),
        productIds.size(), removable, warnings);
  }

  @Transactional
  public UndoResult undoLastImport() {
    Batch batch = findLatestDrinkBatch();
    if (batch == null) throw new ApiException(HttpStatus.NOT_FOUND,
        "Chưa tìm thấy lần import Nước nào để hoàn tác");

    List<String> warnings = new ArrayList<>();
    long maxBatchId = batch.transactions().stream().mapToLong(InventoryTransaction::getId).max().orElse(0L);
    Set<Long> productIds = new LinkedHashSet<>();
    for (InventoryTransaction t : batch.transactions()) productIds.add(t.getProduct().getId());

    // Nếu đã có thao tác mới sau import thì không tự sửa ngược nữa.
    for (Long productId : productIds) {
      Product p = products.findById(productId).orElse(null);
      if (p == null) continue;
      boolean hasNewer = txs.findByProductIdOrderByCreatedAtAsc(productId).stream()
          .anyMatch(t -> t.getId() > maxBatchId && !belongsToBatch(t, batch));
      if (hasNewer) throw new ApiException(HttpStatus.CONFLICT,
          "Không thể hoàn tác tự động vì “" + p.getName()
              + "” đã có phiếu mới sau lần import Nước. Hãy backup rồi xử lý riêng mặt hàng này.");
    }

    // Hoàn tác theo thứ tự ngược với lúc tạo để đưa tồn về đúng trạng thái trước import.
    List<InventoryTransaction> reversed = new ArrayList<>(batch.transactions());
    reversed.sort(Comparator.comparing(InventoryTransaction::getId).reversed());
    int removedTx = 0;
    for (InventoryTransaction t : reversed) {
      if (t.getStatus() != InventoryTransaction.Status.COMPLETED) continue;
      Product p = products.findLockedById(t.getProduct().getId())
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hàng hóa khi hoàn tác"));
      BigDecimal q = nz(t.getQuantity());
      switch (t.getType()) {
        case OUT, ADJUST_OUT -> p.setQuantity(nz(p.getQuantity()).add(q));
        case IN, OPENING, ADJUST_IN -> {
          if (nz(p.getQuantity()).compareTo(q) < 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                "Không thể hoàn tác “" + p.getName() + "” vì tồn hiện tại thấp hơn số cần trả ngược.");
          }
          p.setQuantity(nz(p.getQuantity()).subtract(q));
        }
      }
      txs.delete(t);
      removedTx++;
    }
    txs.flush();

    int removedProducts = 0;
    for (Long productId : productIds) {
      Product p = products.findById(productId).orElse(null);
      if (p == null) continue;
      if (isImportCreatedProduct(p) && !txs.existsByProductId(productId)) {
        products.delete(p);
        removedProducts++;
      }
    }

    audit.log("DRINK_IMPORT_UNDO", "SYSTEM", null,
        "Hoàn tác import Nước ngày " + batch.date() + ": xóa " + removedTx
            + " phiếu import, xóa " + removedProducts + " hàng chỉ được tạo bởi import");
    return new UndoResult(batch.date().toString(), removedTx, productIds.size(), removedProducts, warnings);
  }

  private record Batch(LocalDate date, List<InventoryTransaction> transactions) {}

  private Batch findLatestDrinkBatch() {
    List<InventoryTransaction> candidates = new ArrayList<>();
    candidates.addAll(txs.findBySourceKeyStartingWithOrderByIdAsc("DRINK_DAILY_YELLOW|"));
    candidates.addAll(txs.findBySourceKeyStartingWithOrderByIdAsc("DRINK_DAILY_YELLOW_CLOSING|"));
    candidates.addAll(txs.findBySourceKeyStartingWithOrderByIdAsc("DRINK_DAILY_CLOSING|"));
    candidates = candidates.stream()
        .filter(t -> t.getStatus() == InventoryTransaction.Status.COMPLETED)
        .sorted(Comparator.comparing(InventoryTransaction::getId))
        .toList();
    if (candidates.isEmpty()) return null;

    InventoryTransaction last = candidates.get(candidates.size() - 1);
    LocalDate date = extractBatchDate(last.getSourceKey());
    if (date == null) date = last.getCreatedAt().toLocalDate();
    String iso = date.toString();
    String vi = String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());

    LinkedHashMap<Long, InventoryTransaction> batch = new LinkedHashMap<>();
    for (InventoryTransaction t : candidates) {
      if (sourceKeyHasDate(t.getSourceKey(), iso)) batch.put(t.getId(), t);
    }
    // v1.8.3 có bước đồng bộ Tồn cuối tạo ADJUST không có sourceKey; nhận diện bằng note của cùng ngày.
    for (InventoryTransaction t : txs.findByNoteContainingOrderByIdAsc(vi)) {
      String n = t.getNote() == null ? "" : t.getNote();
      if (t.getStatus() == InventoryTransaction.Status.COMPLETED
          && (n.contains("Báo cáo Nước") || n.contains("khối vàng") || n.contains("Tồn cuối"))) {
        batch.put(t.getId(), t);
      }
    }
    return new Batch(date, new ArrayList<>(batch.values()));
  }

  private boolean belongsToBatch(InventoryTransaction t, Batch batch) {
    return batch.transactions().stream().anyMatch(x -> Objects.equals(x.getId(), t.getId()));
  }

  private LocalDate extractBatchDate(String sourceKey) {
    if (sourceKey == null) return null;
    Matcher m = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(sourceKey);
    if (!m.find()) return null;
    try { return LocalDate.parse(m.group(1)); } catch (Exception e) { return null; }
  }

  private boolean sourceKeyHasDate(String sourceKey, String iso) {
    return sourceKey != null && sourceKey.contains("|" + iso + "|");
  }

  private boolean isImportCreatedProduct(Product p) {
    String n = p.getNote() == null ? "" : p.getNote();
    return n.startsWith("Tạo từ báo cáo Nước ngày ")
        || n.startsWith("Tạo từ Tồn cuối báo cáo Nước ngày ");
  }

  private Parsed parse(MultipartFile file, String targetDate) {
    if (file == null || file.isEmpty())
      throw new ApiException(HttpStatus.BAD_REQUEST, "Chưa chọn file Nước Excel");

    LocalDate wantedDate;
    try { wantedDate = LocalDate.parse(targetDate); }
    catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Hãy chọn đúng ngày báo cáo cần nhập, ví dụ 07/09/2026");
    }

    LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    if (wantedDate.isAfter(today)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày " + wantedDate + " nằm trong tương lai. Hôm nay là " + today);
    }

    List<String> warnings = new ArrayList<>();
    try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
      FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
      DataFormatter fmt = new DataFormatter(new Locale("vi", "VN"));
      List<Block> blocks = findReportBlocks(wb, fmt, ev);
      Block b = blocks.stream()
          .filter(x -> x.day() == wantedDate.getDayOfMonth() && x.month() == wantedDate.getMonthValue())
          .max(Comparator.comparingInt(Block::titleRow))
          .orElse(null);

      boolean simpleSnapshot = false;
      if (b == null) {
        // Một số file Nước chỉ còn bảng hàng hóa + Tồn cuối, không có tiêu đề
        // “BÁO CÁO XUẤT NHẬP TỒN” hoặc không có đủ các cột Tồn đầu/Nhập/Xuất/HT.
        // Với dạng này, ngày do người dùng chọn trên giao diện là ngày chốt tồn.
        b = findSimpleSnapshotBlock(wb, fmt, ev, wantedDate);
        simpleSnapshot = b != null;
      }
      if (b == null) {
        String available = blocks.stream()
            .sorted(Comparator.comparingInt(Block::month).thenComparingInt(Block::day))
            .map(x -> x.day() + "/" + x.month())
            .distinct()
            .reduce((a,c) -> a + ", " + c).orElse("không có");
        throw new ApiException(HttpStatus.BAD_REQUEST,
            "Không tìm thấy bảng Nước có cột Tên hàng và Tồn cuối. "
                + "Nếu file có ngày, các ngày tìm thấy là: " + available);
      }

      LocalDate reportDate = wantedDate;
      List<DailyRow> rows = new ArrayList<>();
      int blankRun = 0;
      for (int r = b.headerRow() + 1; r <= b.sheet().getLastRowNum(); r++) {
        Row row = b.sheet().getRow(r);
        String name = text(row, b.nameCol(), fmt, ev);
        if (blank(name)) {
          blankRun++;
          if (blankRun >= 1) break;
          continue;
        }
        blankRun = 0;
        name = name.trim().replaceAll("\\s+", " ");
        if (looksTotal(name)) break;
        String unit = clean(text(row, b.unitCol(), fmt, ev));
        BigDecimal opening = number(row, b.openingCol(), ev, fmt);
        BigDecimal inQty = number(row, b.inCol(), ev, fmt);
        BigDecimal outQty = number(row, b.outCol(), ev, fmt);
        BigDecimal htQty = number(row, b.htCol(), ev, fmt);
        BigDecimal closing = number(row, b.closingCol(), ev, fmt);
        String note = clean(text(row, b.noteCol(), fmt, ev));

        if (opening == null && inQty == null && outQty == null && htQty == null && closing == null) continue;
        rows.add(new DailyRow(name, unit, cleanQtyNullable(opening), cleanQtyNullable(inQty),
            cleanQtyNullable(outQty), cleanQtyNullable(htQty), cleanQtyNullable(closing), note));
      }
      if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Báo cáo Nước không có dòng hàng hóa để nhập");
      if (simpleSnapshot) {
        addWarning(warnings, "File Nước dạng bảng đơn giản: dùng ngày bạn đã chọn là "
            + String.format("%02d/%02d/%d", reportDate.getDayOfMonth(), reportDate.getMonthValue(), reportDate.getYear())
            + ". Chỉ cột Tồn cuối/Còn lại được nhập vào kho.");
      } else {
        addWarning(warnings, "Đã chọn đúng báo cáo Nước ngày "
            + String.format("%02d/%02d/%d", reportDate.getDayOfMonth(), reportDate.getMonthValue(), reportDate.getYear())
            + "; không tự chọn ngày khác. Chỉ Tồn cuối được đưa vào Còn lại.");
      }
      return new Parsed(b.sheet().getSheetName(), reportDate, rows, warnings);
    } catch (ApiException e) { throw e; }
    catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Không đọc được file Nước Excel: " + e.getMessage());
    }
  }

  private List<Block> findReportBlocks(Workbook wb, DataFormatter fmt, FormulaEvaluator ev) {
    List<Block> candidates = new ArrayList<>();
    for (Sheet s : wb) {
      for (int r = 0; r <= s.getLastRowNum(); r++) {
        Row title = s.getRow(r); if (title == null) continue;
        for (int c = 0; c < Math.max(0, title.getLastCellNum()); c++) {
          String titleText = norm(text(title, c, fmt, ev));
          if (!titleText.contains("bao cao xuat nhap ton")) continue;

          int headerRow = -1;
          int[] cols = null;
          for (int rr = r + 1; rr <= Math.min(s.getLastRowNum(), r + 6); rr++) {
            Row h = s.getRow(rr); if (h == null) continue;
            int nameCol = findHeader(h, c, c + 12, Set.of("ten nvl", "ten hang", "mat hang"), fmt, ev);
            int unitCol = findHeader(h, c, c + 12, Set.of("dvt", "don vi tinh"), fmt, ev);
            int openingCol = findHeader(h, c, c + 12, Set.of("ton dau"), fmt, ev);
            int inCol = findHeader(h, c, c + 12, Set.of("nhap"), fmt, ev);
            int outCol = findHeader(h, c, c + 12, Set.of("xuat"), fmt, ev);
            int htCol = findHeader(h, c, c + 12, Set.of("ht"), fmt, ev);
            int closingCol = findHeader(h, c, c + 12, Set.of("ton cuoi"), fmt, ev);
            int noteCol = findHeader(h, c, c + 12, Set.of("ghi chu"), fmt, ev);
            if (nameCol >= 0 && unitCol >= 0 && openingCol >= 0 && inCol >= 0
                && outCol >= 0 && htCol >= 0 && closingCol >= 0) {
              headerRow = rr;
              cols = new int[]{nameCol, unitCol, openingCol, inCol, outCol, htCol, closingCol,
                  noteCol >= 0 ? noteCol : closingCol + 1};
              break;
            }
          }
          if (headerRow < 0) continue;

          int day = -1, month = -1;
          for (int rr = r; rr <= Math.min(headerRow, r + 3) && day < 0; rr++) {
            Row dr = s.getRow(rr); if (dr == null) continue;
            for (int cc = c; cc <= Math.min(c + 12, Math.max(0, dr.getLastCellNum() - 1)); cc++) {
              String raw = text(dr, cc, fmt, ev);
              if (blank(raw)) continue;
              Matcher m = DATE_PATTERN.matcher(normDateText(raw));
              if (m.find()) { day = Integer.parseInt(m.group(1)); month = Integer.parseInt(m.group(2)); break; }
            }
          }
          if (day < 0) continue;
          candidates.add(new Block(s, r, headerRow, cols[0], cols[1], cols[2], cols[3], cols[4],
              cols[5], cols[6], cols[7], c, c + 12, day, month));
        }
      }
    }
    return candidates;
  }

  private record SnapshotCandidate(Block block, int score) {}

  /**
   * Fallback cho file Nước dạng rút gọn: chỉ cần một hàng tiêu đề có Tên hàng + Tồn cuối.
   * ĐVT là tùy chọn đối với hàng đã tồn tại; hàng mới vẫn cần ĐVT để tạo đúng dữ liệu.
   * Ngày chốt được lấy từ ngày người dùng chọn trên giao diện.
   */
  private Block findSimpleSnapshotBlock(Workbook wb, DataFormatter fmt, FormulaEvaluator ev,
      LocalDate wantedDate) {
    List<SnapshotCandidate> candidates = new ArrayList<>();
    Set<String> nameHeaders = Set.of("ten nvl", "ten nguyen vat lieu", "ten hang", "ten hang hoa",
        "mat hang", "hang hoa", "san pham");
    Set<String> unitHeaders = Set.of("dvt", "don vi tinh", "don vi");
    Set<String> closingHeaders = Set.of("ton cuoi", "con lai", "ton kho", "sl ton", "so luong ton",
        "ton hien tai");

    for (Sheet s : wb) {
      for (int r = 0; r <= s.getLastRowNum(); r++) {
        Row h = s.getRow(r); if (h == null) continue;
        int last = Math.max(0, h.getLastCellNum() - 1);
        int nameCol = findHeader(h, 0, last, nameHeaders, fmt, ev);
        int closingCol = findHeader(h, 0, last, closingHeaders, fmt, ev);
        if (nameCol < 0 || closingCol < 0 || nameCol == closingCol) continue;

        int unitCol = findHeader(h, 0, last, unitHeaders, fmt, ev);
        int openingCol = findHeader(h, 0, last, Set.of("ton dau"), fmt, ev);
        int inCol = findHeader(h, 0, last, Set.of("nhap"), fmt, ev);
        int outCol = findHeader(h, 0, last, Set.of("xuat"), fmt, ev);
        int htCol = findHeader(h, 0, last, Set.of("ht"), fmt, ev);
        int noteCol = findHeader(h, 0, last, Set.of("ghi chu"), fmt, ev);

        // Chấm điểm bằng số dòng thực sự có tên + Tồn cuối số học để tránh bắt nhầm bảng phụ.
        int score = 0;
        int blankRun = 0;
        for (int rr = r + 1; rr <= Math.min(s.getLastRowNum(), r + 120); rr++) {
          Row data = s.getRow(rr);
          String name = text(data, nameCol, fmt, ev);
          if (blank(name)) {
            blankRun++;
            if (score > 0 && blankRun >= 2) break;
            continue;
          }
          blankRun = 0;
          if (looksTotal(name)) break;
          if (number(data, closingCol, ev, fmt) != null) score++;
        }
        if (score < 2) continue;

        int startCol = Math.min(nameCol, closingCol);
        int endCol = Math.max(nameCol, closingCol);
        candidates.add(new SnapshotCandidate(new Block(s, r, r, nameCol, unitCol,
            openingCol, inCol, outCol, htCol, closingCol, noteCol,
            startCol, endCol, wantedDate.getDayOfMonth(), wantedDate.getMonthValue()), score));
      }
    }
    return candidates.stream()
        .max(Comparator.comparingInt(SnapshotCandidate::score))
        .map(SnapshotCandidate::block)
        .orElse(null);
  }

  private int findHeader(Row row, int from, int to, Set<String> targets,
      DataFormatter fmt, FormulaEvaluator ev) {
    int last = Math.max(0, row.getLastCellNum() - 1);
    for (int c = Math.max(0, from); c <= Math.min(to, last); c++) {
      if (targets.contains(norm(text(row, c, fmt, ev)))) return c;
    }
    return -1;
  }

  private boolean hasCompletedHistoryAfter(Long productId, LocalDate d) {
    return txs.findByProductIdOrderByCreatedAtAsc(productId).stream()
        .anyMatch(t -> t.getStatus() == InventoryTransaction.Status.COMPLETED
            && t.getCreatedAt() != null && t.getCreatedAt().toLocalDate().isAfter(d));
  }


  private String text(Row row, int col, DataFormatter fmt, FormulaEvaluator ev) {
    if (row == null || col < 0) return null;
    Cell cell = row.getCell(col); if (cell == null) return null;
    try { return fmt.formatCellValue(cell, ev).trim(); }
    catch (Exception e) { return fmt.formatCellValue(cell).trim(); }
  }

  private BigDecimal number(Row row, int col, FormulaEvaluator ev, DataFormatter fmt) {
    if (row == null || col < 0) return null;
    Cell cell = row.getCell(col); if (cell == null) return null;
    try {
      CellValue v = ev.evaluate(cell);
      if (v != null && v.getCellType() == CellType.NUMERIC)
        return BigDecimal.valueOf(v.getNumberValue());
      String s = fmt.formatCellValue(cell, ev);
      if (blank(s)) return null;
      s = s.trim().replace(" ", "").replace(",", ".");
      return new BigDecimal(s);
    } catch (Exception e) { return null; }
  }

  private static String noteSuffix(String note) { return blank(note) ? "" : " - Ghi chú Excel: " + note.trim(); }
  private static BigDecimal cleanQty(BigDecimal v) {
    return v.setScale(Math.min(3, Math.max(0, v.scale())), RoundingMode.HALF_UP).stripTrailingZeros();
  }
  private static BigDecimal cleanQtyNullable(BigDecimal v) { return v == null ? null : cleanQty(v); }
  private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
  private static boolean positive(BigDecimal v) { return v != null && v.signum() > 0; }
  private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
  private static String clean(String s) { return blank(s) ? null : s.trim(); }
  private static boolean looksTotal(String s) {
    String n = norm(s); return n.equals("tong") || n.startsWith("tong cong") || n.startsWith("cong");
  }
  private static void addWarning(List<String> warnings, String w) {
    if (warnings.size() < 20 && !warnings.contains(w)) warnings.add(w);
  }
  private static String normDateText(String s) {
    if (s == null) return "";
    String n = Normalizer.normalize(s, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
    n = DIACRITICS.matcher(n).replaceAll("").replace('đ', 'd');
    return n.replaceAll("[^a-z0-9/]+", " ").trim();
  }
  private static String norm(String s) {
    if (s == null) return "";
    String n = Normalizer.normalize(s, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
    n = DIACRITICS.matcher(n).replaceAll("").replace('đ', 'd');
    return n.replaceAll("[^a-z0-9]+", " ").trim();
  }
}
