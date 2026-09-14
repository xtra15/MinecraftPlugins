package dev.rollthingy.core.misc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemBundleCodecTest {
    @Test
    void roundTripsListOfMaps() {
        List<Map<String, Object>> in = List.of(Map.of("type", "DIAMOND", "amount", 2));
        String enc = ItemBundleCodec.encodeMaps(in);
        List<Map<String, Object>> out = ItemBundleCodec.decodeMaps(enc);
        assertEquals(in, out);
    }

    @Test
    void emptyListRoundTrips() {
        assertEquals(List.of(), ItemBundleCodec.decodeMaps(ItemBundleCodec.encodeMaps(List.of())));
    }

    @Test
    void corruptOrBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> ItemBundleCodec.decodeMaps(""));
        assertThrows(IllegalArgumentException.class, () -> ItemBundleCodec.decodeMaps("!!!not-base64!!!"));
    }
}