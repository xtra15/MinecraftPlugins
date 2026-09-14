package dev.rollthingy.box;

import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.BoxRegistry;
import dev.rollthingy.core.box.OddsEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BoxModelCache {
    private final BoxRegistry registry;
    private final Map<String, Box> boxes = new ConcurrentHashMap<>();
    private final Map<String, OddsEngine.OddsModel> models = new ConcurrentHashMap<>();

    public BoxModelCache(BoxRegistry registry) {
        this.registry = registry;
    }

    public List<Box> boxes() {
        return new ArrayList<>(boxes.values());
    }

    public Box byId(String id) {
        return boxes.get(id);
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
        for (Box box : loaded) {
            boxes.put(box.id(), box);
            models.put(box.id(), OddsEngine.build(box));
        }
    }

    public void addOrUpdate(Box box) {
        registry.save(box);
        boxes.put(box.id(), box);
        models.put(box.id(), OddsEngine.build(box));
    }

    public void remove(String id) {
        registry.delete(id);
        boxes.remove(id);
        models.remove(id);
    }
}