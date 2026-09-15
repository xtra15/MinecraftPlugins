package dev.ah.auctionhouse.gui;

import dev.ah.core.misc.ItemBundleCodec;
import org.bukkit.inventory.ItemStack;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ItemCache {
    private static final int MAX_ICONS = 4096;
    private static final Map<String, CachedIcon> ICONS = new LinkedHashMap<>(256, 0.75f, true);

    private ItemCache() {}

    static final class CachedIcon {
        private final ItemStack icon;
        private final int count;
        private final long total;

        CachedIcon(ItemStack icon, int count, long total) {
            this.icon = icon;
            this.count = count;
            this.total = total;
        }

        ItemStack cloneItem() {
            ItemStack clone = icon.clone();
            clone.setAmount((int) total);
            return clone;
        }

        int count() {
            return count;
        }

        long total() {
            return total;
        }
    }

    static CachedIcon icon(String key, String itemData) {
        synchronized (ICONS) {
            CachedIcon cached = ICONS.get(key);
            if (cached != null) return cached;
            List<ItemStack> items = ItemBundleCodec.decode(itemData);
            if (items.isEmpty()) return null;
            CachedIcon entry = new CachedIcon(IconUtil.clean(items.get(0)), items.size(),
                    IconUtil.totalAmount(items));
            if (ICONS.size() >= MAX_ICONS) {
                Iterator<Map.Entry<String, CachedIcon>> it = ICONS.entrySet().iterator();
                it.next();
                it.remove();
            }
            ICONS.put(key, entry);
            return entry;
        }
    }
}