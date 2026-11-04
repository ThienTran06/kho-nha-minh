package com.khonhaminh.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="audit_events")
public class AuditEvent {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false,length=60) private String action;
  @Column(name="entity_type",length=60) private String entityType;
  @Column(name="entity_id") private Long entityId;
  @Column(nullable=false,length=1000) private String description;
  @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
  @PrePersist void pre(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
  public Long getId(){return id;} public String getAction(){return action;} public void setAction(String v){action=v;}
  public String getEntityType(){return entityType;} public void setEntityType(String v){entityType=v;}
  public Long getEntityId(){return entityId;} public void setEntityId(Long v){entityId=v;}
  public String getDescription(){return description;} public void setDescription(String v){description=v;}
  public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
