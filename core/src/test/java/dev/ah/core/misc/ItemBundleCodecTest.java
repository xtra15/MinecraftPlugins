package dev.ah.core.misc;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ItemBundleCodecTest {

    private static Map<String, Object> fixtureItem(String type) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("display-name", "Custom Sword");
        meta.put("damage", 0);
        Map<String, Object> item = new HashMap<>();
        item.put("type", type);
        item.put("amount", 1);
        item.put("meta", meta);
        return item;
    }

    @Test
    void roundTripsListOfMaps() {
        List<Map<String, Object>> original = List.of(fixtureItem("DIAMOND_SWORD"), fixtureItem("NETHERITE_PICKAXE"));
        String encoded = ItemBundleCodec.encodeMaps(original);
        List<Map<String, Object>> decoded = ItemBundleCodec.decodeMaps(encoded);
        assertEquals(original, decoded);
    }

    @Test
    void emptyListRoundTrips() {
        assertEquals(List.of(), ItemBundleCodec.decodeMaps(ItemBundleCodec.encodeMaps(List.of())));
    }

    @Test
    void corruptInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> ItemBundleCodec.decodeMaps("not-base64!!!"));
        assertThrows(IllegalArgumentException.class, () -> ItemBundleCodec.decodeMaps(""));
    }
}