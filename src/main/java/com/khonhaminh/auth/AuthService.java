package com.khonhaminh.auth;
import com.khonhaminh.common.ApiException;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AuthService {
  private final AppAuthRepository repo; private final PasswordEncoder encoder; private final SecureRandom random=new SecureRandom();
  @Value("${app.security.minimum-password-length:6}") private int minimumPasswordLength;
  @Value("${app.security.setup-key:}") private String expectedSetupKey;
  public AuthService(AppAuthRepository repo, PasswordEncoder encoder){this.repo=repo;this.encoder=encoder;}
  public AppAuth get(){return repo.findById(1L).orElseThrow();}
  @Transactional public String setup(String password){ return setup(password, null); }
  @Transactional public String setup(String password, String setupKey){
    AppAuth a=get(); if(a.isInitialized()) throw new ApiException(HttpStatus.CONFLICT,"Mật khẩu đã được thiết lập");
    if (!expectedSetupKey.isBlank()) {
      byte[] expected = expectedSetupKey.getBytes(StandardCharsets.UTF_8);
      byte[] supplied = (setupKey == null ? "" : setupKey).getBytes(StandardCharsets.UTF_8);
      if (!MessageDigest.isEqual(expected, supplied)) throw new ApiException(HttpStatus.FORBIDDEN,"Mã thiết lập ban đầu không đúng");
    }
    validate(password); a.setPasswordHash(encoder.encode(password)); String code=newRecoveryCode(); a.setRecoveryHash(encoder.encode(code)); a.setInitialized(true); a.setUpdatedAt(LocalDateTime.now()); return code;
  }
  public boolean setupKeyRequired(){ return !expectedSetupKey.isBlank(); }
  public int minimumPasswordLength(){ return minimumPasswordLength; }
  public boolean matches(String password){AppAuth a=get();return a.isInitialized() && encoder.matches(password,a.getPasswordHash());}
  @Transactional public void change(String current,String next){
    AppAuth a=get(); if(!encoder.matches(current,a.getPasswordHash())) throw new ApiException(HttpStatus.BAD_REQUEST,"Mật khẩu hiện tại không đúng");
    validate(next); a.setPasswordHash(encoder.encode(next)); a.setUpdatedAt(LocalDateTime.now());
  }

  @Transactional public String createRecoveryCode(String currentPassword){
    AppAuth a=get(); if(!a.isInitialized()||!encoder.matches(currentPassword,a.getPasswordHash())) throw new ApiException(HttpStatus.BAD_REQUEST,"Mật khẩu hiện tại không đúng");
    String code=newRecoveryCode();a.setRecoveryHash(encoder.encode(code));a.setUpdatedAt(LocalDateTime.now());return code;
  }
  @Transactional public void recover(String code,String next){
    AppAuth a=get();if(a.getRecoveryHash()==null||code==null||!encoder.matches(code.trim().toUpperCase(),a.getRecoveryHash())) throw new ApiException(HttpStatus.BAD_REQUEST,"Mã khôi phục không đúng");
    validate(next);a.setPasswordHash(encoder.encode(next));a.setUpdatedAt(LocalDateTime.now());
  }
  private String newRecoveryCode(){return "KHO-"+String.format("%04d",random.nextInt(10000))+"-"+String.format("%04d",random.nextInt(10000));}
  private void validate(String p){if(p==null||p.length()<minimumPasswordLength) throw new ApiException(HttpStatus.BAD_REQUEST,"Mật khẩu cần ít nhất "+minimumPasswordLength+" ký tự"); if(p.length()>100) throw new ApiException(HttpStatus.BAD_REQUEST,"Mật khẩu quá dài");}
}
