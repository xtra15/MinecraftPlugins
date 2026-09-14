package dev.rollthingy.core.box;

import dev.rollthingy.core.misc.ItemBundleCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlBoxCodecTest {
    private static final String DIAMOND = ItemBundleCodec.encodeMaps(List.of(Map.of("type", "DIAMOND", "amount", 1)));
    private static final String STONE = ItemBundleCodec.encodeMaps(List.of(Map.of("type", "STONE", "amount", 1)));

    @Test
    void roundTripsBox() {
        Box box = new Box("legendary",
                "Legendary Crate",
                new IconSpec("ENDER_CHEST", DIAMOND),
                List.of(new PaymentRequirement(DIAMOND, 5, true), new PaymentRequirement(STONE, 10, false)),
                new Penalty(0.5, 50.0, 20.0),
                5L,
                new Zonk(true, 5.0),
                List.of(new RarityTier("Legendary", 1.0,
                        List.of(new BoxItem(DIAMOND, 1.0), new BoxItem(DIAMOND, 0.000001)))));
        String yaml = YamlBoxCodec.toString(box);
        Box round = YamlBoxCodec.fromString(yaml, "legendary");
        assertEquals(box, round);
    }

    @Test
    void decimalWeightsSurvive() {
        Box box = new Box("b", "B", new IconSpec("CHEST", DIAMOND), List.of(), new Penalty(0.5, 50, 20), 5L,
                new Zonk(true, 5.0), List.of(new RarityTier("R", 1.0, List.of(new BoxItem(DIAMOND, 0.000001)))));
        Box round = YamlBoxCodec.fromString(YamlBoxCodec.toString(box), "b");
        assertEquals(0.000001, round.tiers().get(0).items().get(0).weight());
    }
}