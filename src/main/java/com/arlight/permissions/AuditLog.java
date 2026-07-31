package com.arlight.permissions;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class AuditLog implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Path file;
    private final long maxBytes;
    private final ExecutorService writer;

    AuditLog(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("audit.log");
        this.maxBytes = Math.max(65_536L,
                plugin.getConfig().getLong("audit.max-bytes", 1_048_576L));
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ArlightPermissions-Audit");
            thread.setDaemon(true);
            return thread;
        });
    }

    void add(String actor, String action) {
        String line = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now())
                + " | " + actor + " | " + action + System.lineSeparator();
        writer.execute(() -> append(line));
    }

    private synchronized void append(String line) {
        try {
            Files.createDirectories(file.getParent());
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            if (Files.exists(file) && Files.size(file) + bytes.length > maxBytes) {
                Files.move(file, file.resolveSibling("audit.log.1"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            plugin.getLogger().warning("No se pudo escribir audit.log: " + error.getMessage());
        }
    }

    List<String> latest(int max) {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
            return all.subList(Math.max(0, all.size() - max), all.size());
        } catch (IOException error) {
            return List.of("No se pudo leer audit.log");
        }
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }
}
