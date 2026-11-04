package com.khonhaminh.product;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products/import-drink-monthly")
public class DrinkMonthlyImportController {
  private final DrinkMonthlyImportService service;
  public DrinkMonthlyImportController(DrinkMonthlyImportService service){this.service=service;}

  @PostMapping("/preview")
  public DrinkMonthlyImportService.PreviewResult preview(@RequestParam("file") MultipartFile file,
      @RequestParam("targetDate") String targetDate){return service.preview(file,targetDate);}

  @PostMapping
  public DrinkMonthlyImportService.ImportResult importFile(@RequestParam("file") MultipartFile file,
      @RequestParam("targetDate") String targetDate){return service.importFile(file,targetDate);}

  @GetMapping("/undo-last/preview")
  public DrinkMonthlyImportService.UndoPreview previewUndoLast(){return service.previewUndoLast();}

  @PostMapping("/undo-last")
  public DrinkMonthlyImportService.UndoResult undoLast(){return service.undoLastImport();}
}
