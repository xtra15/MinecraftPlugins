package dev.rollthingy.core.box;

import java.util.List;

public record RarityTier(String name, double weight, List<BoxItem> items) {}