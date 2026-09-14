package dev.rollthingy.core.box;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YamlBoxCodec {
    public YamlBoxCodec() {}

    public static String toString(Box box) {
        YamlConfiguration conf = new YamlConfiguration();
        conf.set("id", box.id());
        conf.set("name", box.name());
        conf.set("icon.material", box.icon().material());
        conf.set("icon.data", box.icon().data());

        List<Map<String, Object>> payment = new ArrayList<>();
        for (PaymentRequirement req : box.payment()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("data", req.data());
            m.put("amount", req.amount());
            m.put("strict", req.strict());
            payment.add(m);
        }
        conf.set("required-payment", payment);

        conf.set("penalty.loose-value", String.valueOf(box.penalty().looseValue()));
        conf.set("penalty.rare-cut", String.valueOf(box.penalty().rareCut()));
        conf.set("penalty.zonk-feed", String.valueOf(box.penalty().zonkFeed()));

        conf.set("cooldown-seconds", box.cooldownSeconds());

        conf.set("zonk.enabled", box.zonk().enabled());
        conf.set("zonk.base-chance", String.valueOf(box.zonk().baseChance()));

        List<Map<String, Object>> tiers = new ArrayList<>();
        for (RarityTier tier : box.tiers()) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("name", tier.name());
            tm.put("weight", String.valueOf(tier.weight()));
            List<Map<String, Object>> items = new ArrayList<>();
            for (BoxItem item : tier.items()) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("data", item.data());
                im.put("weight", String.valueOf(item.weight()));
                items.add(im);
            }
            tm.put("items", items);
            tiers.add(tm);
        }
        conf.set("tiers", tiers);

        return conf.saveToString();
    }

    public static Box fromString(String text, String id) {
        YamlConfiguration conf = YamlConfiguration.loadConfiguration(new StringReader(text));
        String name = conf.getString("name", id);
        IconSpec icon = new IconSpec(conf.getString("icon.material", "CHEST"), conf.getString("icon.data", ""));

        List<PaymentRequirement> payment = new ArrayList<>();
        for (Map<?, ?> m : conf.getMapList("required-payment")) {
            payment.add(new PaymentRequirement(
                    String.valueOf(m.get("data")),
                    number(m.get("amount")).intValue(),
                    Boolean.TRUE.equals(m.get("strict"))));
        }

        Penalty penalty = new Penalty(
                dbl(conf.get("penalty.loose-value")),
                dbl(conf.get("penalty.rare-cut")),
                dbl(conf.get("penalty.zonk-feed")));

        long cooldown = conf.getLong("cooldown-seconds", 0L);

        Zonk zonk = new Zonk(conf.getBoolean("zonk.enabled", false), dbl(conf.get("zonk.base-chance")));

        List<RarityTier> tiers = new ArrayList<>();
        for (Map<?, ?> tm : conf.getMapList("tiers")) {
            String tierName = String.valueOf(tm.get("name"));
            double tierWeight = dbl(tm.get("weight"));
            List<BoxItem> items = new ArrayList<>();
            Object itemsRaw = tm.get("items");
            if (itemsRaw instanceof List<?> itemsList) {
                for (Object itemObj : itemsList) {
                    if (!(itemObj instanceof Map<?, ?> im)) continue;
                    items.add(new BoxItem(String.valueOf(im.get("data")), dbl(im.get("weight"))));
                }
            }
            tiers.add(new RarityTier(tierName, tierWeight, items));
        }

        return new Box(id, name, icon, payment, penalty, cooldown, zonk, tiers);
    }

    private static Number number(Object o) {
        if (o instanceof Number n) return n;
        return Double.parseDouble(String.valueOf(o));
    }

    private static double dbl(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }
}