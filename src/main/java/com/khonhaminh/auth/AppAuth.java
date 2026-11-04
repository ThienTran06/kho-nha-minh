package com.khonhaminh.auth;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="app_auth")
public class AppAuth {
  @Id private Long id;
  @Column(name="password_hash") private String passwordHash;
  @Column(name="recovery_hash") private String recoveryHash;
  private boolean initialized;
  @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
  @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
  protected AppAuth(){}
  public Long getId(){return id;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;} public String getRecoveryHash(){return recoveryHash;} public void setRecoveryHash(String v){recoveryHash=v;}
  public boolean isInitialized(){return initialized;} public void setInitialized(boolean v){initialized=v;}
  public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
  public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
