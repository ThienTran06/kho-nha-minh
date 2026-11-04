package com.khonhaminh.config;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("cloud")
public class CloudCsrfController {
  @GetMapping("/api/auth/csrf")
  public Map<String, String> csrf(CsrfToken csrfToken) {
    // Calling getToken() forces CookieCsrfTokenRepository to issue the XSRF-TOKEN cookie.
    return Map.of("token", csrfToken.getToken());
  }
}
