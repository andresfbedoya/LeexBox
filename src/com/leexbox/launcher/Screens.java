package com.leexbox.launcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class Screens {
    static final class Item {
        final String kind;
        final int widgetId;
        final String data;
        float x;
        float y;
        float w;
        float h;

        Item(String kind, int widgetId, String data, float x, float y, float w, float h) {
            this.kind = kind;
            this.widgetId = widgetId;
            this.data = data;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean resizable() {
            return kind.equals("terminal") || kind.equals("stats") || kind.equals("widget");
        }

        float minW() {
            return resizable() ? 0.15f : 0.1f;
        }

        float minH() {
            if (kind.equals("terminal")) return 0.15f;
            if (kind.equals("stats")) return 0.05f;
            return resizable() ? 0.08f : 0.08f;
        }
    }

    private final Prefs prefs;
    private final float pageHeightDp;
    final List<List<Item>> pages = new ArrayList<>();
    int current;

    Screens(Prefs prefs, float pageHeightDp) {
        this.prefs = prefs;
        this.pageHeightDp = Math.max(300f, pageHeightDp);
        load();
    }

    List<Item> page() {
        return pages.get(current);
    }

    boolean has(String kind) {
        for (List<Item> page : pages) {
            for (Item it : page) {
                if (it.kind.equals(kind)) return true;
            }
        }
        return false;
    }

    int count(String kind, List<Item> page) {
        int n = 0;
        for (Item it : page) {
            if (it.kind.equals(kind)) n++;
        }
        return n;
    }

    void addPage() {
        pages.add(new ArrayList<Item>());
        save();
    }

    List<Integer> removePage(int index) {
        List<Integer> ids = new ArrayList<>();
        if (pages.size() <= 1) return ids;
        List<Item> gone = pages.remove(index);
        List<Item> dest = pages.get(0);
        for (Item it : gone) {
            if (it.kind.equals("widget")) {
                ids.add(it.widgetId);
            } else if (it.kind.equals("terminal") || it.kind.equals("stats")) {
                dest.add(it);
            }
        }
        current = Math.min(current, pages.size() - 1);
        save();
        return ids;
    }

    void removeKind(String kind) {
        for (List<Item> page : pages) {
            for (int i = page.size() - 1; i >= 0; i--) {
                if (page.get(i).kind.equals(kind)) page.remove(i);
            }
        }
        save();
    }

    List<Integer> reset() {
        List<Integer> ids = new ArrayList<>();
        for (List<Item> page : pages) {
            for (Item it : page) {
                if (it.kind.equals("widget")) ids.add(it.widgetId);
            }
        }
        pages.clear();
        prefs.layout("");
        prefs.page(0);
        load();
        return ids;
    }

    void save() {
        try {
            JSONArray root = new JSONArray();
            for (List<Item> page : pages) {
                JSONArray arr = new JSONArray();
                for (Item it : page) {
                    arr.put(new JSONObject()
                            .put("k", it.kind)
                            .put("id", it.widgetId)
                            .put("d", it.data)
                            .put("x", it.x)
                            .put("y", it.y)
                            .put("w", it.w)
                            .put("h", it.h));
                }
                root.put(arr);
            }
            prefs.layout(root.toString());
            prefs.page(current);
        } catch (JSONException ignored) {
        }
    }

    private void load() {
        pages.clear();
        try {
            JSONArray root = new JSONArray(prefs.layout());
            for (int i = 0; i < root.length(); i++) {
                JSONArray arr = root.getJSONArray(i);
                List<Item> page = new ArrayList<>();
                for (int j = 0; j < arr.length(); j++) {
                    JSONObject o = arr.getJSONObject(j);
                    page.add(new Item(o.getString("k"), o.optInt("id", -1), o.optString("d", ""),
                            (float) o.getDouble("x"), (float) o.getDouble("y"),
                            (float) o.getDouble("w"), (float) o.getDouble("h")));
                }
                pages.add(page);
            }
        } catch (JSONException e) {
            pages.clear();
        }
        if (pages.isEmpty()) {
            pages.add(defaultPage());
            current = 0;
            save();
        } else {
            current = Math.max(0, Math.min(prefs.page(), pages.size() - 1));
        }
    }

    private List<Item> defaultPage() {
        List<Item> page = new ArrayList<>();
        float y = 0.01f;
        if (prefs.showStats()) {
            page.add(new Item("stats", -1, "", 0.15f, y, 0.7f, 0.06f));
            y += 0.07f;
        }
        for (int id : prefs.widgets()) {
            float h = Math.min(0.5f, Math.max(0.08f, prefs.size("w" + id, 100) / pageHeightDp));
            page.add(new Item("widget", id, "", 0.04f, y, 0.92f, h));
            y += h + 0.01f;
        }
        float top = Math.min(y, 0.7f);
        float th = 1f - top;
        int saved = prefs.size("terminal", 0);
        if (saved > 0) th = Math.min(th, saved / pageHeightDp);
        page.add(new Item("terminal", -1, "", 0f, 1f - th, 1f, th));
        prefs.widgets(new ArrayList<Integer>());
        return page;
    }
}
