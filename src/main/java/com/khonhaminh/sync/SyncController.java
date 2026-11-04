package com.khonhaminh.sync;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
  private final SyncRevisionService revisions;

  public SyncController(SyncRevisionService revisions) {
    this.revisions = revisions;
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of("revision", revisions.currentRevision());
  }
}
