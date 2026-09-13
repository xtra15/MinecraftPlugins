package dev.ah.core.economy;

import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Optional economy facade. Default installation uses the disabled path:
 * auction-house behaves as pure item trading. Implementations are wired only
 * when config economy.enabled is true.
 */
public interface EconomyService {
    OptionalDouble balance(UUID player);
    boolean take(UUID player, double amount);
    void give(UUID player, double amount);
}