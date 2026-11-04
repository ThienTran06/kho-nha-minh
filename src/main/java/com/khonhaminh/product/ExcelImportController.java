package com.khonhaminh.product;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products/import-excel")
public class ExcelImportController {
  private final ExcelImportService service;
  public ExcelImportController(ExcelImportService service){this.service=service;}

  @PostMapping("/preview")
  public ExcelImportService.PreviewResult preview(@RequestParam("file") MultipartFile file){return service.preview(file);}

  @PostMapping
  public ExcelImportService.ImportResult importFile(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value="mode", defaultValue="SKIP") ExcelImportService.ImportMode mode){
    return service.importFile(file, mode);
  }
}
