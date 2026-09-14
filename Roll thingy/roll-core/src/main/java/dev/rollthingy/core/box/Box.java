package dev.rollthingy.core.box;

import java.util.List;

public record Box(String id, String name, IconSpec icon, List<PaymentRequirement> payment,
                  Penalty penalty, long cooldownSeconds, Zonk zonk, List<RarityTier> tiers) {}