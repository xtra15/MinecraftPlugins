package dev.rollthingy.core.msg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageRepositoryTest {
    @TempDir
    Path tmp;

    private static final String BUNDLED = """
            main.title: "Bundled Title"
            main.page: "Page <page>/<pages>"
            bundled-only: "kept"
            """;

    private static final String DISK = """
            main.title: "Disk Title"
            """;

    @Test
    void diskWinsOverBundledDefaults() throws IOException {
        File disk = tmp.resolve("lang.yml").toFile();
        Files.writeString(disk.toPath(), DISK, StandardCharsets.UTF_8);
        MessageRepository repo = new MessageRepository(disk,
                new ByteArrayInputStream(BUNDLED.getBytes(StandardCharsets.UTF_8)));
        assertEquals("Disk Title", repo.get("main.title"));
        assertEquals("kept", repo.get("bundled-only"));
    }

    @Test
    void replacesBracesAndAnglePlaceholders() throws IOException {
        File disk = tmp.resolve("lang.yml").toFile();
        Files.writeString(disk.toPath(), "main.page: \"Page <page>/<pages>\"", StandardCharsets.UTF_8);
        MessageRepository repo = new MessageRepository(disk,
                new ByteArrayInputStream("main.page: \"Page {page}/{pages}\"".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Page 3/5", repo.get("main.page", Map.of("page", "3", "pages", "5")));
    }

    @Test
    void missingKeyReportsHelpfulText() throws IOException {
        File disk = tmp.resolve("lang.yml").toFile();
        Files.writeString(disk.toPath(), "", StandardCharsets.UTF_8);
        MessageRepository repo = new MessageRepository(disk,
                new ByteArrayInputStream(BUNDLED.getBytes(StandardCharsets.UTF_8)));
        assertTrue(repo.get("nope").contains("Missing message"));
    }
}