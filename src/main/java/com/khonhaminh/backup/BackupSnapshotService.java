package com.khonhaminh.backup;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import com.khonhaminh.common.ApiException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Stores a safety copy in the database using its own transaction, so a failed restore cannot roll it back. */
@Service
public class BackupSnapshotService {
  private final JdbcTemplate jdbc;
  private final PlatformTransactionManager transactionManager;

  public BackupSnapshotService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.transactionManager = transactionManager;
  }

  public record SnapshotInfo(long id, String createdAt, String reason) {}

  public List<SnapshotInfo> list() {
    return jdbc.query("SELECT id, created_at, reason FROM backup_snapshots ORDER BY id DESC",
        (rs, row) -> new SnapshotInfo(rs.getLong("id"), rs.getTimestamp("created_at").toLocalDateTime().toString(), rs.getString("reason")));
  }

  public byte[] readPayload(long id) {
    try {
      String payload = jdbc.queryForObject("SELECT payload FROM backup_snapshots WHERE id = ?", String.class, id);
      return payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
    } catch (EmptyResultDataAccessException e) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bản sao lưu an toàn này");
    }
  }

  public void saveBeforeRestore(byte[] json) {
    TransactionTemplate independent = new TransactionTemplate(transactionManager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    independent.executeWithoutResult(status -> {
      jdbc.update("INSERT INTO backup_snapshots(created_at, reason, payload) VALUES (?, ?, ?)",
          Timestamp.valueOf(LocalDateTime.now()), "TRUOC_KHOI_PHUC", new String(json, StandardCharsets.UTF_8));
      jdbc.update("DELETE FROM backup_snapshots WHERE id NOT IN (SELECT id FROM backup_snapshots ORDER BY id DESC LIMIT 10)");
    });
  }
}
