package com.khonhaminh;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class KhoNhaMinhApplication {
  private static final int PORT = 17865;
  private static final URI APP_URI = URI.create("http://127.0.0.1:" + PORT);
  private final Environment environment;

  public KhoNhaMinhApplication(Environment environment) { this.environment = environment; }

  public static void main(String[] args) throws Exception {
    boolean cloudMode = isCloudMode(args);
    if (!cloudMode && isAlreadyRunning()) {
      openApp();
      return;
    }

    if (!cloudMode) {
      Path base = Path.of(System.getProperty("user.home"), "KhoNhaMinh");
      Path data = base.resolve("data");
      Path logs = base.resolve("logs");
      Path backups = base.resolve("backups");
      Files.createDirectories(data);
      Files.createDirectories(logs);
      Files.createDirectories(backups);
      backupBeforeStart(data, backups);
    }

    SpringApplication.run(KhoNhaMinhApplication.class, args);
  }

  private static boolean isCloudMode(String[] args) {
    String profiles = System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "") + "," +
        System.getProperty("spring.profiles.active", "") + "," + String.join(",", args);
    return Arrays.stream(profiles.split("[,= ]" )).anyMatch("cloud"::equalsIgnoreCase);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    if (Arrays.asList(environment.getActiveProfiles()).contains("cloud")) return;
    openApp();
  }

  private static boolean isAlreadyRunning() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", PORT), 250);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static void openApp() {
    try {
      if (isWindows()) {
        String edge = findEdge();
        if (edge != null) {
          new ProcessBuilder(edge, "--app=" + APP_URI, "--start-maximized").start();
          return;
        }
      }
      if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(APP_URI);
      }
    } catch (Exception ignored) {
      // Backend remains available at http://127.0.0.1:17865.
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  private static String findEdge() {
    String[] candidates = {
      System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
      System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe"
    };
    for (String candidate : candidates) {
      if (candidate != null && !candidate.startsWith("null") && Files.exists(Path.of(candidate))) return candidate;
    }
    return null;
  }

  private static void backupBeforeStart(Path dataDir, Path backupDir) {
    Path db = dataDir.resolve("kho.mv.db");
    if (!Files.exists(db)) return;
    try {
      String today = LocalDate.now().toString();
      try (Stream<Path> files = Files.list(backupDir)) {
        if (files.anyMatch(p -> p.getFileName().toString().startsWith("kho-" + today))) return;
      }
      String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
      Files.copy(db, backupDir.resolve("kho-" + stamp + ".mv.db"), StandardCopyOption.COPY_ATTRIBUTES);
      cleanupOldBackups(backupDir, 30);
    } catch (Exception ignored) {
      // Backup failure must not prevent the mother from opening the application.
    }
  }

  private static void cleanupOldBackups(Path backupDir, int keep) throws IOException {
    try (Stream<Path> files = Files.list(backupDir)) {
      Path[] all = files
          .filter(p -> p.getFileName().toString().endsWith(".mv.db"))
          .sorted(Comparator.comparingLong(KhoNhaMinhApplication::lastModified).reversed())
          .toArray(Path[]::new);
      for (int i = keep; i < all.length; i++) Files.deleteIfExists(all[i]);
    }
  }

  private static long lastModified(Path p) {
    try { return Files.getLastModifiedTime(p).toMillis(); }
    catch (IOException e) { return 0L; }
  }
}
