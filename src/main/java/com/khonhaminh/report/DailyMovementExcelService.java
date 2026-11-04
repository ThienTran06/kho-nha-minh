package com.khonhaminh.report;

import com.khonhaminh.common.ApiException;
import com.khonhaminh.inventory.InventoryTransaction;
import com.khonhaminh.inventory.InventoryTransactionRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyMovementExcelService {
  private final InventoryTransactionRepository txs;
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  public DailyMovementExcelService(InventoryTransactionRepository txs) { this.txs = txs; }

  @Transactional(readOnly = true)
  public byte[] exportInbound(LocalDate date) {
    LocalDate d = date == null ? LocalDate.now() : date;
    List<InventoryTransaction> rows = daily(d, InventoryTransaction.Type.IN);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày " + d.format(DATE) + " chưa có hàng nhập để xuất báo cáo");
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      writeInbound(wb, rows, d);
      wb.write(out);
      return out.toByteArray();
    } catch (ApiException e) { throw e; }
    catch (Exception e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được file Excel nhập hàng"); }
  }

  @Transactional(readOnly = true)
  public byte[] exportOutbound(LocalDate date) {
    LocalDate d = date == null ? LocalDate.now() : date;
    List<InventoryTransaction> rows = daily(d, InventoryTransaction.Type.OUT);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày " + d.format(DATE) + " chưa có hàng xuất để xuất báo cáo");
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      writeOutbound(wb, rows, d);
      wb.write(out);
      return out.toByteArray();
    } catch (ApiException e) { throw e; }
    catch (Exception e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được file Excel xuất kho"); }
  }

  public String inboundFilename(LocalDate date) {
    LocalDate d = date == null ? LocalDate.now() : date;
    return "BANG-NHAP-HANG-VI-BIEN-" + d.format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
  }
  public String outboundFilename(LocalDate date) {
    LocalDate d = date == null ? LocalDate.now() : date;
    return "PHIEU-XUAT-KHO-VI-BIEN-" + d.format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
  }

  private List<InventoryTransaction> daily(LocalDate d, InventoryTransaction.Type type) {
    LocalDateTime start = d.atStartOfDay(), end = d.plusDays(1).atStartOfDay();
    return txs.findByCreatedAtBetweenOrderByCreatedAtAsc(start, end).stream()
        .filter(t -> t.getStatus() == InventoryTransaction.Status.COMPLETED && t.getType() == type)
        .toList();
  }

  private void writeInbound(Workbook wb, List<InventoryTransaction> rows, LocalDate date) {
    Sheet sh = wb.createSheet("Bảng nhập hàng");
    sh.setDisplayGridlines(false);
    sh.setPrintGridlines(false);
    sh.setFitToPage(true);
    sh.getPrintSetup().setLandscape(true);
    sh.getPrintSetup().setFitWidth((short)1);
    sh.getPrintSetup().setFitHeight((short)0);
    sh.setMargin(Sheet.LeftMargin, 0.3); sh.setMargin(Sheet.RightMargin, 0.3);
    Forms f = new Forms(wb);

    mergeValue(sh, 0,0,0,4,"BẢNG NHẬP HÀNG",f.bigTitle);
    mergeValue(sh, 1,1,0,4,"VỊ BIỂN",f.subTitle);
    mergeValue(sh, 2,2,0,4,"Ngày " + date.getDayOfMonth() + " tháng " + date.getMonthValue() + " năm " + date.getYear(),f.italicCenter);

    String[] headers={"NGÀY","NHÀ CUNG CẤP / HÀNG NHẬP","SỐ TIỀN TRÊN HĐ","NGƯỜI KÍ NHẬN","ĐÃ GỬI HÌNH HĐ\nĐÁNH DẤU"};
    Row hr=sh.createRow(4);hr.setHeightInPoints(42);
    for(int i=0;i<headers.length;i++){Cell c=hr.createCell(i);c.setCellValue(headers[i]);c.setCellStyle(f.header);}

    int r=5;
    for(InventoryTransaction t:rows){
      Row row=sh.createRow(r++);row.setHeightInPoints(32);
      text(row,0,date.format(DATE),f.center);
      String supplier=blank(t.getSupplier());
      String item=t.getProductNameSnapshot()+" - "+fmt(t.getQuantity())+" "+t.getUnitSnapshot();
      text(row,1,supplier.isBlank()?item:supplier+"\n"+item,f.text);
      Cell amount=row.createCell(2); if(t.getUnitCost()!=null) amount.setCellValue(t.getUnitCost().multiply(t.getQuantity()).doubleValue()); amount.setCellStyle(f.money);
      text(row,3,blank(t.getReceiverName()),f.center);
      text(row,4,t.isInvoiceSent()?"X":"",f.bigMark);
    }
    Row total=sh.createRow(r); mergeValue(sh,r,r,0,1,"TỔNG",f.total);
    Cell sum=total.createCell(2);sum.setCellFormula("SUM(C6:C"+r+")");sum.setCellStyle(f.totalMoney);
    text(total,3,"",f.total);text(total,4,"",f.total);

    int[] widths={14,48,22,22,22}; for(int i=0;i<widths.length;i++)sh.setColumnWidth(i,widths[i]*256);
    sh.setRepeatingRows(new CellRangeAddress(4,4,-1,-1));
    sh.setAutobreaks(true);
  }

  private void writeOutbound(Workbook wb, List<InventoryTransaction> rows, LocalDate date) {
    Sheet sh = wb.createSheet("Phiếu xuất kho");
    sh.setDisplayGridlines(false); sh.setPrintGridlines(false); sh.setFitToPage(true);
    sh.getPrintSetup().setLandscape(true);sh.getPrintSetup().setFitWidth((short)1);sh.getPrintSetup().setFitHeight((short)0);
    sh.setMargin(Sheet.LeftMargin,0.25);sh.setMargin(Sheet.RightMargin,0.25);
    Forms f=new Forms(wb);

    mergeValue(sh,0,0,0,4,"CÔNG TY TNHH CLOUD VỊ BIỂN",f.company);
    mergeValue(sh,1,1,0,4,"SHB5-03, Lô B2, Đường Độc Lập, Khu phố Liên Trì 2, Phường Bình Kiến, Tỉnh Đắk Lắk, Việt Nam",f.small);
    mergeValue(sh,0,0,6,7,"Mẫu số: 02 - VT",f.rightBold);
    mergeValue(sh,1,2,6,7,"(Ban hành theo Thông tư số 133/2016/TT-BTC ngày 26/08/2016 của Bộ Tài chính)",f.smallRight);
    mergeValue(sh,3,3,0,7,"PHIẾU XUẤT KHO VỊ BIỂN",f.bigTitleDark);
    mergeValue(sh,4,4,0,7,"Ngày " + String.format("%02d",date.getDayOfMonth()) + " tháng " + String.format("%02d",date.getMonthValue()) + " năm " + date.getYear(),f.italicCenter);
    mergeValue(sh,5,5,0,7,"Số: ................",f.centerNoBorder);
    mergeValue(sh,6,6,0,4,"- Họ và tên người nhận hàng: " + common(rows, InventoryTransaction::getReceiverName),f.info);
    mergeValue(sh,7,7,0,4,"- Lý do xuất kho: " + commonNote(rows),f.info);
    mergeValue(sh,6,6,5,7,"Xuất tại kho (ngăn lô): KHO CCDC NVL NƯỚC BIA",f.info);
    mergeValue(sh,7,7,5,7,"Địa điểm: NHÀ HÀNG VỊ BIỂN",f.info);

    String[] headers={"STT","Tên hàng hóa","Phân\nLoại","Đơn vị tính","SL","Bộ phận","Tên NV","Ghi Chú"};
    Row hr=sh.createRow(9);hr.setHeightInPoints(38);
    for(int i=0;i<headers.length;i++){Cell c=hr.createCell(i);c.setCellValue(headers[i]);c.setCellStyle(f.header);}

    int r=10,stt=1;
    for(InventoryTransaction t:rows){
      Row row=sh.createRow(r++);row.setHeightInPoints(28);
      numCell(row,0,stt++,f.center);
      text(row,1,t.getProductNameSnapshot(),f.text);
      text(row,2,blank(t.getProduct().getCategory()),f.center);
      text(row,3,t.getUnitSnapshot(),f.center);
      decimal(row,4,t.getQuantity(),f.numberCenter);
      text(row,5,blank(t.getDepartment()),f.center);
      text(row,6,blank(t.getEmployeeName()),f.center);
      text(row,7,blank(t.getNote()),f.text);
    }
    Row total=sh.createRow(r);mergeValue(sh,r,r,0,3,"TỔNG",f.total);
    Cell qty=total.createCell(4);qty.setCellFormula("SUM(E11:E"+r+")");qty.setCellStyle(f.totalNumber);
    for(int c=5;c<=7;c++)text(total,c,"",f.total);

    int[] widths={7,35,18,14,12,20,18,28};for(int i=0;i<widths.length;i++)sh.setColumnWidth(i,widths[i]*256);
    sh.setRepeatingRows(new CellRangeAddress(9,9,-1,-1));
  }

  private interface TextPick { String get(InventoryTransaction t); }
  private String common(List<InventoryTransaction> rows, TextPick pick){
    LinkedHashSet<String> vals=new LinkedHashSet<>();for(var t:rows){String v=blank(pick.get(t));if(!v.isBlank())vals.add(v);}
    if(vals.isEmpty())return "........................................................";
    if(vals.size()==1)return vals.iterator().next();
    return "Theo chi tiết các dòng hàng trong ngày";
  }
  private String commonNote(List<InventoryTransaction> rows){return "Xuất hàng ngày "+rows.get(0).getCreatedAt().toLocalDate().format(DATE);}
  private String blank(String s){return s==null?"":s.trim();}
  private String fmt(BigDecimal n){return n.stripTrailingZeros().toPlainString();}
  private void mergeValue(Sheet sh,int r1,int r2,int c1,int c2,String v,CellStyle s){Row r=sh.getRow(r1);if(r==null)r=sh.createRow(r1);Cell c=r.createCell(c1);c.setCellValue(v);c.setCellStyle(s);if(r1!=r2||c1!=c2)sh.addMergedRegion(new CellRangeAddress(r1,r2,c1,c2));}
  private void text(Row r,int c,String v,CellStyle s){Cell x=r.createCell(c);x.setCellValue(v);x.setCellStyle(s);}
  private void numCell(Row r,int c,int v,CellStyle s){Cell x=r.createCell(c);x.setCellValue(v);x.setCellStyle(s);}
  private void decimal(Row r,int c,BigDecimal v,CellStyle s){Cell x=r.createCell(c);x.setCellValue(v.doubleValue());x.setCellStyle(s);}

  private static class Forms {
    final CellStyle bigTitle,subTitle,italicCenter,header,center,text,money,bigMark,total,totalMoney,company,small,rightBold,smallRight,bigTitleDark,centerNoBorder,info,numberCenter,totalNumber;
    Forms(Workbook wb){
      Font bold=wb.createFont();bold.setBold(true);Font title=wb.createFont();title.setBold(true);title.setFontHeightInPoints((short)18);Font sub=wb.createFont();sub.setBold(true);sub.setFontHeightInPoints((short)12);Font italic=wb.createFont();italic.setItalic(true);Font smallF=wb.createFont();smallF.setFontHeightInPoints((short)9);
      bigTitle=base(wb,false);bigTitle.setFont(title);bigTitle.setAlignment(HorizontalAlignment.CENTER);bigTitle.setVerticalAlignment(VerticalAlignment.CENTER);
      bigTitleDark=base(wb,false);bigTitleDark.cloneStyleFrom(bigTitle);
      subTitle=base(wb,false);subTitle.setFont(sub);subTitle.setAlignment(HorizontalAlignment.CENTER);
      italicCenter=base(wb,false);italicCenter.setFont(italic);italicCenter.setAlignment(HorizontalAlignment.CENTER);
      header=base(wb,true);header.setFont(bold);header.setAlignment(HorizontalAlignment.CENTER);header.setVerticalAlignment(VerticalAlignment.CENTER);header.setWrapText(true);
      center=base(wb,true);center.setAlignment(HorizontalAlignment.CENTER);center.setVerticalAlignment(VerticalAlignment.CENTER);center.setWrapText(true);
      text=base(wb,true);text.setVerticalAlignment(VerticalAlignment.CENTER);text.setWrapText(true);
      money=base(wb,true);money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));money.setAlignment(HorizontalAlignment.RIGHT);money.setVerticalAlignment(VerticalAlignment.CENTER);
      bigMark=base(wb,true);bigMark.setFont(title);bigMark.setAlignment(HorizontalAlignment.CENTER);bigMark.setVerticalAlignment(VerticalAlignment.CENTER);
      total=base(wb,true);total.setFont(bold);total.setAlignment(HorizontalAlignment.CENTER);total.setVerticalAlignment(VerticalAlignment.CENTER);
      totalMoney=base(wb,true);totalMoney.setFont(bold);totalMoney.setDataFormat(wb.createDataFormat().getFormat("#,##0"));totalMoney.setAlignment(HorizontalAlignment.RIGHT);
      company=base(wb,false);company.setFont(bold);company.setAlignment(HorizontalAlignment.CENTER);
      small=base(wb,false);small.setFont(smallF);small.setAlignment(HorizontalAlignment.CENTER);small.setWrapText(true);
      rightBold=base(wb,false);rightBold.setFont(bold);rightBold.setAlignment(HorizontalAlignment.RIGHT);
      smallRight=base(wb,false);smallRight.setFont(smallF);smallRight.setAlignment(HorizontalAlignment.RIGHT);smallRight.setWrapText(true);
      centerNoBorder=base(wb,false);centerNoBorder.setAlignment(HorizontalAlignment.CENTER);
      info=base(wb,false);info.setWrapText(true);info.setVerticalAlignment(VerticalAlignment.CENTER);
      numberCenter=base(wb,true);numberCenter.setDataFormat(wb.createDataFormat().getFormat("#,##0.###"));numberCenter.setAlignment(HorizontalAlignment.CENTER);numberCenter.setVerticalAlignment(VerticalAlignment.CENTER);
      totalNumber=base(wb,true);totalNumber.setFont(bold);totalNumber.setDataFormat(wb.createDataFormat().getFormat("#,##0.###"));totalNumber.setAlignment(HorizontalAlignment.CENTER);
    }
    static CellStyle base(Workbook wb,boolean border){CellStyle s=wb.createCellStyle();if(border){s.setBorderTop(BorderStyle.THIN);s.setBorderBottom(BorderStyle.THIN);s.setBorderLeft(BorderStyle.THIN);s.setBorderRight(BorderStyle.THIN);}return s;}
  }
}
