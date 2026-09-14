package dev.rollthingy.core.box;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class BoxRegistry {
    private final File dir;
    private final YamlBoxCodec codec;

    public BoxRegistry(File dir, YamlBoxCodec codec) {
        this.dir = dir;
        this.codec = codec;
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("unable to create boxes dir: " + dir);
        }
    }

    public List<Box> loadAll() {
        File[] files = dir.listFiles((f, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();
        List<Box> out = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                out.add(codec.fromString(text, id));
            } catch (IOException | RuntimeException e) {
                System.err.println("[RollThingy] skipping unparseable box file " + file.getName() + ": " + e.getMessage());
            }
        }
        return out;
    }

    public Box save(Box box) {
        String id = safeId(box.id());
        String yaml = codec.toString(new Box(id, box.name(), box.icon(), box.payment(), box.penalty(),
                box.cooldownSeconds(), box.zonk(), box.tiers()));
        Path target = dir.toPath().resolve(id + ".yml");
        Path tmp = dir.toPath().resolve(id + ".yml.tmp");
        try {
            Files.writeString(tmp, yaml, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to save box " + id, e);
        }
        return box;
    }

    public void delete(String id) {
        try {
            Files.deleteIfExists(dir.toPath().resolve(safeId(id) + ".yml"));
        } catch (IOException e) {
            throw new IllegalStateException("failed to delete box " + id, e);
        }
    }

    private static String safeId(String id) {
        return id.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }
}