package dev.rollthingy.core.box;

import dev.rollthingy.core.misc.ItemBundleCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OddsEngineTest {
    private static final String D = ItemBundleCodec.encodeMaps(List.of(Map.of("type", "DIAMOND", "amount", 1)));
    private static final String S = ItemBundleCodec.encodeMaps(List.of(Map.of("type", "STONE", "amount", 1)));

    private static Box box() {
        return new Box("b", "B", new IconSpec("CHEST", D), List.of(),
                new Penalty(0.5, 50, 20), 5L, new Zonk(true, 5.0),
                List.of(
                        new RarityTier("Legendary", 1.0, List.of(new BoxItem(D, 1.0))),
                        new RarityTier("Common", 90.0, List.of(new BoxItem(S, 90.0)))));
    }

    @Test
    void totalIsSumOfTiersPlusZonk() {
        OddsEngine.OddsModel m = OddsEngine.build(box());
        assertEquals(91.0, m.totalItemWeight());
        assertEquals(5.0, m.zonkWeight());
    }

    @Test
    void rollAlwaysReturnsAPick() {
        OddsEngine.OddsModel m = OddsEngine.build(box());
        Random rng = new Random(42L);
        for (int i = 0; i < 5000; i++) {
            OddsEngine.RollOutcome o = m.roll(rng);
            assertTrue(o.tierIndex() == -1 || (o.tierIndex() >= 0 && o.tierIndex() < m.tiers().size()));
        }
    }

    @Test
    void commonItemWinsFarMoreOftenThanLegendary() {
        OddsEngine.OddsModel m = OddsEngine.build(box());
        Random rng = new Random(7L);
        int legendary = 0;
        int zonk = 0;
        for (int i = 0; i < 200000; i++) {
            OddsEngine.RollOutcome o = m.roll(rng);
            if (o.tierIndex() == -1) zonk++;
            else if (m.tiers().get(o.tierIndex()).name().equals("Legendary")) legendary++;
        }
        assertTrue(zonk > 0);
        assertTrue(legendary > 0);
        assertTrue(legendary < zonk * 10); // 1/(90+5) vs (5)/(96) — legendary way rarer than zonk
    }

    @Test
    void penaltyReducesRareAndBoostsZonk() {
        OddsEngine.OddsModel base = OddsEngine.build(box());
        OddsEngine.OddsModel pen = base.withAdjustedWeights(1.0, box().penalty());
        assertTrue(pen.tiers().get(0).weight() < base.tiers().get(0).weight());
        assertTrue(pen.zonkWeight() > base.zonkWeight());
    }
}