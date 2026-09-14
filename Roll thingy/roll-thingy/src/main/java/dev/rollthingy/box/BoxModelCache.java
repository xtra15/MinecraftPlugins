package dev.rollthingy.box;

import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.BoxRegistry;
import dev.rollthingy.core.box.OddsEngine;
import dev.rollthingy.core.misc.ItemBundleCodec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BoxModelCache {
    private final BoxRegistry registry;
    private final Map<String, Box> boxes = new ConcurrentHashMap<>();
    private final Map<String, OddsEngine.OddsModel> models = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> icons = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> items = new ConcurrentHashMap<>();
    private final Map<String, String> materials = new ConcurrentHashMap<>();

    public BoxModelCache(BoxRegistry registry) {
        this.registry = registry;
    }

    public List<Box> boxes() {
        return new ArrayList<>(boxes.values());
    }

    public Box byId(String id) {
        return boxes.get(id);
    }

    /** Cached decoded icon; mutating the result is safe (it's a clone). */
    public ItemStack icon(Box box) {
        ItemStack template = icons.get(box.id());
        if (template == null) {
            template = decodeIcon(box);
            icons.put(box.id(), template);
        }
        return template.clone();
    }

    private static ItemStack decodeIcon(Box box) {
        if (box.icon() != null && box.icon().data() != null && !box.icon().data().isBlank()) {
            try {
                List<ItemStack> decoded = ItemBundleCodec.decode(box.icon().data());
                if (!decoded.isEmpty()) return decoded.get(0).clone();
            } catch (IllegalArgumentException ignored) {}
        }
        try {
            return new ItemStack(Material.valueOf(box.icon() != null ? box.icon().material() : "CHEST"), 1);
        } catch (IllegalArgumentException e) {
            return new ItemStack(Material.CHEST, 1);
        }
    }

    /** Cached decoded prize item (clone safe to mutate). Falls back to air when corrupt. */
    private ItemStack itemTemplate(String data) {
        if (data == null || data.isBlank()) return new ItemStack(Material.AIR, 1);
        return items.computeIfAbsent(data, d -> decodeFirstOrAir(d));
    }

    /** Cached decoded prize item; returns null when the data is corrupt. */
    public ItemStack itemOrNull(String data) {
        ItemStack template = itemTemplate(data);
        return template.getType().isAir() ? null : template.clone();
    }

    /** Cached decoded prize item (clone safe to mutate). Falls back to stone when corrupt. */
    public ItemStack item(String data) {
        ItemStack template = itemTemplate(data);
        return template.getType().isAir() ? new ItemStack(Material.STONE, 1) : template.clone();
    }

    /** Cached material name for a prize's base64 data (never decodes twice). */
    public String materialOf(String data) {
        String cached = materials.get(data);
        if (cached != null) return cached;
        String material = decodeFirstOrAir(data).getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        materials.put(data, material);
        return material;
    }

    private static ItemStack decodeFirstOrAir(String data) {
        try {
            List<ItemStack> decoded = ItemBundleCodec.decode(data);
            if (!decoded.isEmpty()) return decoded.get(0).clone();
        } catch (IllegalArgumentException ignored) {}
        return new ItemStack(Material.AIR, 1);
    }

    public OddsEngine.OddsModel modelOf(String id) {
        OddsEngine.OddsModel model = models.get(id);
        if (model == null) {
            Box box = boxes.get(id);
            if (box == null) throw new IllegalArgumentException("no such box: " + id);
            model = OddsEngine.build(box);
            models.put(id, model);
        }
        return model;
    }

    public void reloadAll() {
        List<Box> loaded = registry.loadAll();
        boxes.clear();
        models.clear();
        icons.clear();
        items.clear();
        materials.clear();
        for (Box box : loaded) {
            boxes.put(box.id(), box);
            models.put(box.id(), OddsEngine.build(box));
            icons.put(box.id(), decodeIcon(box));
        }
    }

    public void addOrUpdate(Box box) {
        registry.save(box);
        boxes.put(box.id(), box);
        models.put(box.id(), OddsEngine.build(box));
        icons.put(box.id(), decodeIcon(box));
    }

    public void remove(String id) {
        registry.delete(id);
        boxes.remove(id);
        models.remove(id);
        icons.remove(id);
    }
}