package com.khonhaminh.auth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {
  private final AuthService service;
  public AuthController(AuthService service){this.service=service;}
  public record SetupRequest(@NotBlank String password, String setupKey){}
  public record ChangeRequest(@NotBlank String currentPassword,@NotBlank String newPassword){}
  public record RecoveryCreateRequest(@NotBlank String currentPassword){}
  public record RecoverRequest(@NotBlank String recoveryCode,@NotBlank String newPassword){}
  @GetMapping("/status") public Map<String,Object> status(){var a=service.get();var auth=SecurityContextHolder.getContext().getAuthentication();boolean logged=auth!=null&&auth.isAuthenticated()&&!"anonymousUser".equals(auth.getPrincipal());return Map.of("configured",a.isInitialized(),"authenticated",logged,"recoveryConfigured",a.getRecoveryHash()!=null,"setupKeyRequired",service.setupKeyRequired(),"passwordMinLength",service.minimumPasswordLength());}
  @PostMapping("/setup") public Map<String,Object> setup(@Valid @RequestBody SetupRequest r,HttpServletRequest req){
    String recoveryCode=service.setup(r.password(),r.setupKey());
    var auth=new UsernamePasswordAuthenticationToken("owner",null,java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OWNER")));
    var ctx=SecurityContextHolder.createEmptyContext();ctx.setAuthentication(auth);SecurityContextHolder.setContext(ctx);req.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,ctx);
    return Map.of("ok",true,"recoveryCode",recoveryCode);
  }
  @PostMapping("/recovery/create") public Map<String,Object> createRecovery(@Valid @RequestBody RecoveryCreateRequest r){return Map.of("ok",true,"recoveryCode",service.createRecoveryCode(r.currentPassword()));}
  @PostMapping("/recover") public Map<String,Object> recover(@Valid @RequestBody RecoverRequest r){service.recover(r.recoveryCode(),r.newPassword());return Map.of("ok",true);}
  @PostMapping("/change-password") public Map<String,Object> change(@Valid @RequestBody ChangeRequest r){service.change(r.currentPassword(),r.newPassword());return Map.of("ok",true);}
}
