package com.khonhaminh.audit;
import org.springframework.stereotype.Service;
@Service
public class AuditLogService {
  private final AuditEventRepository repo;
  public AuditLogService(AuditEventRepository r){repo=r;}
  public void log(String action,String entityType,Long entityId,String description){
    AuditEvent e=new AuditEvent();e.setAction(action);e.setEntityType(entityType);e.setEntityId(entityId);e.setDescription(description);repo.save(e);
  }
}
