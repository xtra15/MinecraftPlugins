package dev.rollthingy.core.box;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class OddsEngine {
    private OddsEngine() {}

    public record TierChances(String name, double weight, List<Double> itemWeights) {
        double totalItems() {
            return itemWeights.stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    public record RollOutcome(int tierIndex, int itemIndex) {}

    public static final class OddsModel {
        private final List<TierChances> tiers;
        private final double zonkWeight;

        OddsModel(List<TierChances> tiers, double zonkWeight) {
            this.tiers = List.copyOf(tiers);
            this.zonkWeight = zonkWeight;
        }

        public List<TierChances> tiers() {
            return tiers;
        }

        public double zonkWeight() {
            return zonkWeight;
        }

        public double totalItemWeight() {
            return tiers.stream().mapToDouble(TierChances::weight).sum();
        }

        public RollOutcome roll(Random rng) {
            double total = totalItemWeight() + zonkWeight;
            double cursor = rng.nextDouble() * total;
            if (cursor < zonkWeight) return new RollOutcome(-1, -1);
            cursor -= zonkWeight;
            for (int t = 0; t < tiers.size(); t++) {
                TierChances tier = tiers.get(t);
                if (cursor < tier.weight()) {
                    double tierCursor = cursor / tier.weight() * tier.totalItems();
                    for (int i = 0; i < tier.itemWeights().size(); i++) {
                        double w = tier.itemWeights().get(i);
                        if (tierCursor < w || i == tier.itemWeights().size() - 1) {
                            return new RollOutcome(t, i);
                        }
                        tierCursor -= w;
                    }
                }
                cursor -= tier.weight();
            }
            return new RollOutcome(tiers.size() - 1, 0);
        }

        public double chanceOf(TierChances tier, int itemIndex) {
            double total = totalItemWeight() + zonkWeight;
            if (total <= 0 || tier.itemWeights().isEmpty()) return 0;
            double item = tier.itemWeights().get(itemIndex);
            double tierTotal = tier.totalItems();
            if (tierTotal <= 0) return 0;
            return item / tierTotal * tier.weight() / total * 100.0;
        }

        public OddsModel withAdjustedWeights(double shortfall, Penalty penalty) {
            List<TierChances> out = new ArrayList<>();
            for (TierChances tier : tiers) {
                double factor = 1.0 - shortfall * penalty.rareCut() / 100.0;
                out.add(new TierChances(tier.name(), Math.max(0, tier.weight() * factor), tier.itemWeights()));
            }
            double fed = shortfall * penalty.zonkFeed() / 100.0;
            double base = zonkWeight;
            double newZonk = (base + fed) * (totalItemWeight() + zonkWeight) / (totalItemWeight() + base + fed);
            return new OddsModel(out, newZonk == base ? base : newZonk);
        }
    }

    public static OddsModel build(Box box) {
        List<TierChances> tiers = new ArrayList<>();
        for (RarityTier tier : box.tiers()) {
            List<Double> weights = new ArrayList<>();
            for (BoxItem item : tier.items()) weights.add(item.weight());
            tiers.add(new TierChances(tier.name(), tier.weight(), weights));
        }
        double zonk = box.zonk() != null && box.zonk().enabled() ? box.zonk().baseChance() : 0.0;
        return new OddsModel(tiers, zonk);
    }
}