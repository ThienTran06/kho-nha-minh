package com.khonhaminh.backup;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/backup")
public class BackupController {
  private final BackupService service;
  private final BackupSnapshotService snapshots;

  public BackupController(BackupService service, BackupSnapshotService snapshots) {
    this.service = service;
    this.snapshots = snapshots;
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> export() throws Exception {
    byte[] bytes = service.exportJson();
    String name = "KhoNhaMinh-chuyen-may-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";
    return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
        .contentType(MediaType.APPLICATION_JSON).body(bytes);
  }

  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public BackupService.BackupPreview preview(@RequestPart("file") MultipartFile file) throws Exception {
    return service.preview(file.getBytes());
  }

  @PostMapping(value = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public BackupService.RestoreResult restore(@RequestPart("file") MultipartFile file) throws Exception {
    return service.restore(file.getBytes());
  }

  @GetMapping("/snapshots")
  public java.util.List<BackupSnapshotService.SnapshotInfo> snapshots() {
    return snapshots.list();
  }

  @GetMapping("/snapshots/{id}/export")
  public ResponseEntity<byte[]> exportSnapshot(@PathVariable long id) {
    byte[] json = snapshots.readPayload(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"KhoNhaMinh-sao-luu-an-toan-" + id + ".json\"")
        .contentType(MediaType.APPLICATION_JSON).body(json);
  }
}
