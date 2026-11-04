package com.khonhaminh.sync;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SyncRevisionService {
  private final JdbcTemplate jdbc;

  public SyncRevisionService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long currentRevision() {
    Long revision = jdbc.queryForObject("SELECT revision FROM sync_state WHERE id = 1", Long.class);
    return revision == null ? 0L : revision;
  }

  public void markChanged() {
    jdbc.update("UPDATE sync_state SET revision = revision + 1, updated_at = CURRENT_TIMESTAMP WHERE id = 1");
  }
}
