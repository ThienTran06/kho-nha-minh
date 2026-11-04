package com.khonhaminh.sync;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class SyncChangeInterceptor implements HandlerInterceptor, WebMvcConfigurer {
  private static final Logger log = LoggerFactory.getLogger(SyncChangeInterceptor.class);
  private final SyncRevisionService revisions;

  public SyncChangeInterceptor(SyncRevisionService revisions) {
    this.revisions = revisions;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this).addPathPatterns("/api/**");
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    if (ex != null || response.getStatus() >= 400) return;
    String method = request.getMethod();
    if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) return;
    String path = request.getRequestURI();
    if (path.startsWith("/api/auth/") || path.startsWith("/api/sync/") ||
        path.startsWith("/api/reports/") || path.equals("/api/purchases/excel") ||
        path.equals("/api/backup/preview")) return;
    try {
      revisions.markChanged();
    } catch (Exception e) {
      // Do not change the outcome of a successful stock operation; log for diagnostics.
      log.error("Could not increment cloud sync revision after {} {}", method, path, e);
    }
  }
}
