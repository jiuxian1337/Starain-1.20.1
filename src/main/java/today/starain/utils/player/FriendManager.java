package today.starain.utils.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import today.starain.utils.wrapper.Wrapper;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FriendManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> friends = new CopyOnWriteArrayList<>();
    private static boolean loaded;

    public static boolean isFriend(Entity player) {
        return player instanceof Player && isFriend(player.getName().getString());
    }

    public static boolean isFriend(String player) {
        ensureLoaded();
        return indexOf(player) >= 0;
    }

    public static void addFriend(Player player) {
        addFriend(player.getName().getString());
    }

    /** @return 原本不在列表里才返回 true */
    public static boolean addFriend(String name) {
        ensureLoaded();
        if (name == null || name.isBlank() || indexOf(name) >= 0) return false;
        friends.add(name);
        save();
        return true;
    }

    public static void removeFriend(Player player) {
        removeFriend(player.getName().getString());
    }

    public static boolean removeFriend(String name) {
        ensureLoaded();
        int index = indexOf(name);
        if (index < 0) return false;
        friends.remove(index);
        save();
        return true;
    }

    public static void clear() {
        ensureLoaded();
        friends.clear();
        save();
    }

    public static List<String> getFriends() {
        ensureLoaded();
        return friends;
    }

    // 玩家名大小写不敏感
    private static int indexOf(String name) {
        if (name == null) return -1;
        for (int i = 0; i < friends.size(); i++) {
            if (friends.get(i).equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static File file() {
        return new File(Wrapper.resolveConfigFolder(), "friends.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = file();
        if (!file.isFile()) return;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) return;
            for (JsonElement element : root.getAsJsonArray()) {
                String name = element.getAsString();
                if (indexOf(name) < 0) friends.add(name);
            }
        } catch (Exception e) {
            System.err.println("[Starain] 读取 friends.json 失败: " + e.getMessage());
        }
    }

    private static void save() {
        File file = file();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            JsonArray array = new JsonArray();
            for (String friend : friends) array.add(friend);
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(array, writer);
            }
        } catch (Exception e) {
            System.err.println("[Starain] 保存 friends.json 失败: " + e.getMessage());
        }
    }
}
