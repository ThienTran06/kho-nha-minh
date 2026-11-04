package com.khonhaminh.audit;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/audit")
public class AuditController {
  private final AuditEventRepository repo;
  public AuditController(AuditEventRepository r){repo=r;}
  @GetMapping public List<AuditEvent> list(){return repo.findTop200ByOrderByCreatedAtDesc();}
}
