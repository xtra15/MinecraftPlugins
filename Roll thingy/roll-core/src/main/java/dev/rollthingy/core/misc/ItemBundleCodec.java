package dev.rollthingy.core.misc;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class ItemBundleCodec {
    private ItemBundleCodec() {}

    public static String encodeMaps(List<Map<String, Object>> maps) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new ArrayList<>(maps));
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to encode item data", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> decodeMaps(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("empty item data");
        }
        Object raw;
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             ObjectInputStream in = new ObjectInputStream(bytes)) {
            raw = in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("corrupt item data", e);
        }
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("corrupt item data");
        for (Object element : list) {
            if (!(element instanceof Map<?, ?>)) throw new IllegalArgumentException("corrupt item data");
        }
        return (List<Map<String, Object>>) list;
    }

    public static String encode(List<ItemStack> items) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (ItemStack item : items) maps.add(item.serialize());
        return encodeMaps(maps);
    }

    public static List<ItemStack> decode(String base64) {
        List<ItemStack> items = new ArrayList<>();
        for (Map<String, Object> map : decodeMaps(base64)) items.add(ItemStack.deserialize(map));
        return items;
    }
}