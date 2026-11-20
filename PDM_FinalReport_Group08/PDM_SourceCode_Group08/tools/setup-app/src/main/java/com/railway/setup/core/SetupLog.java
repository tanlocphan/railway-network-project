package com.railway.setup.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Everything the setup tool prints also goes to logs/setup-app.log, so a team
 * member can send the file when something does not work.
 */
public final class SetupLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static BufferedWriter writer;
    private static boolean failed;

    private SetupLog() {
    }

    public static synchronized void write(Output.Kind kind, String text) {
        BufferedWriter w = writer();
        if (w == null) {
            return;
        }
        try {
            String level = switch (kind) {
                case ERROR -> "SEVERE ";
                case WARN -> "WARNING";
                case DIM -> "FINE   ";
                default -> "INFO   ";
            };
            w.write(TIME.format(LocalDateTime.now()) + " " + level + " [setup] " + text);
            w.newLine();
            w.flush();
        } catch (IOException e) {
            failed = true;
        }
    }

    public static void error(String what, Throwable e) {
        StringWriter trace = new StringWriter();
        e.printStackTrace(new PrintWriter(trace));
        write(Output.Kind.ERROR, what + "\n" + trace);
    }

    private static BufferedWriter writer() {
        if (writer == null && !failed) {
            try {
                Path folder = Project.logsDir();
                Files.createDirectories(folder);
                Path file = folder.resolve("setup-app.log");
                if (Files.exists(file) && Files.size(file) > 2_000_000) {
                    Files.move(file, folder.resolve("setup-app.old.log"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                failed = true;
            }
        }
        return writer;
    }
}
