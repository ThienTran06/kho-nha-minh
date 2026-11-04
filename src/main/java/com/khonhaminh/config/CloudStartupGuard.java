package com.khonhaminh.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("cloud")
public class CloudStartupGuard {
  public CloudStartupGuard(@Value("${app.security.setup-key:}") String setupKey) {
    if (setupKey == null || setupKey.isBlank()) {
      throw new IllegalStateException("Cloud mode requires APP_SETUP_KEY to protect first-time setup.");
    }
  }
}
