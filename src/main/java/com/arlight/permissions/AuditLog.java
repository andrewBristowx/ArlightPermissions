package com.arlight.permissions;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.time.*;import java.time.format.DateTimeFormatter;import java.util.*;
final class AuditLog {
 private final Path file; AuditLog(JavaPlugin p){file=p.getDataFolder().toPath().resolve("audit.log");}
 synchronized void add(String actor,String action){try{Files.createDirectories(file.getParent());String line=DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now())+" | "+actor+" | "+action+System.lineSeparator();Files.writeString(file,line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(IOException e){throw new UncheckedIOException(e);}}
 List<String> latest(int max){if(!Files.exists(file))return List.of();try{List<String> all=Files.readAllLines(file,StandardCharsets.UTF_8);return all.subList(Math.max(0,all.size()-max),all.size());}catch(IOException e){return List.of("No se pudo leer audit.log");}}
}
