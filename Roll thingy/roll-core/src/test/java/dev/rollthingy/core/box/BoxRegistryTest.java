package dev.rollthingy.core.box;

import dev.rollthingy.core.misc.ItemBundleCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BoxRegistryTest {
    @TempDir
    Path tmp;

    @Test
    void saveLoadDeleteRoundTrip() {
        BoxRegistry reg = new BoxRegistry(tmp.toFile(), new YamlBoxCodec());
        String d = ItemBundleCodec.encodeMaps(List.of(Map.of("type", "DIAMOND", "amount", 1)));
        Box box = new Box("crate", "Crate", new IconSpec("CHEST", d), List.of(new PaymentRequirement(d, 1, true)),
                new Penalty(0.5, 50, 20), 5L, new Zonk(true, 5.0), List.of());
        reg.save(box);
        List<Box> loaded = reg.loadAll();
        assertEquals(1, loaded.size());
        assertEquals("crate", loaded.get(0).id());
        assertEquals(box, loaded.get(0));
        reg.delete("crate");
        assertTrue(reg.loadAll().isEmpty());
    }
}