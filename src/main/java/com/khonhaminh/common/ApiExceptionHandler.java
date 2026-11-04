package com.khonhaminh.common;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e){ return ResponseEntity.status(e.status()).body(Map.of("message",e.getMessage(),"time",Instant.now().toString())); }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e){
    String msg=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getDefaultMessage()).orElse("Dữ liệu không hợp lệ");
    return ResponseEntity.badRequest().body(Map.of("message",msg,"time",Instant.now().toString()));
  }
  @ExceptionHandler(Exception.class)
  ResponseEntity<?> generic(Exception e){ return ResponseEntity.internalServerError().body(Map.of("message","Có lỗi xảy ra trên máy chủ","time",Instant.now().toString())); }
}
