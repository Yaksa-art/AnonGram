package org.telegram.margelet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Плагины Margelet: установка, список, включение.
 *
 * Плагин — это архив .marp: манифест, код на питоне, иконка и всё, что автор
 * захотел положить рядом. Код лежит исходником и читается кем угодно; это не
 * техническая мера, а условие форума, и владелец форка выбрал именно её.
 *
 * <b>Про безопасность честно.</b> Плагин исполняется как часть приложения и
 * технически может всё, что может само приложение, — включая доступ к данным
 * входа. Список разрешений в манифесте это <b>заявление автора</b>, а не
 * ограничение, и приложение не может его проверить. Так решено владельцем
 * форка: он выбрал открытость кода и проверку людьми вместо песочницы.
 * Единственное, чего здесь делать нельзя, — говорить пользователю, будто
 * разрешения его защищают. Поэтому на окне установки написано ровно то, что
 * есть.
 */
public class MargeletPlugins {

    /** Что плагин заявляет о себе. Именно заявляет — проверить это нечем. */
    public static final String[] PERMISSIONS = {
            "read_chats", "send_messages", "edit_messages",
            "delete_messages", "change_profile", "ui"
    };

    public static final class Plugin {
        public final String id;
        public final String name;
        public final String version;
        public final String author;
        public final String description;
        public final List<String> permissions;
        public final File folder;

        Plugin(String id, String name, String version, String author, String description,
               List<String> permissions, File folder) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.permissions = permissions;
            this.folder = folder;
        }

        public File entry() {
            return new File(folder, "main.py");
        }

        public Bitmap icon() {
            final File file = new File(folder, "icon.png");
            return file.exists() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
        }

        public boolean enabled() {
            return MargeletConfig.pluginEnabled(id);
        }
    }

    private static File root() {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "margelet_plugins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Все установленные плагины, по имени. */
    public static List<Plugin> installed() {
        final List<Plugin> found = new ArrayList<>();
        final File[] folders = root().listFiles();
        if (folders == null) {
            return found;
        }
        for (File folder : folders) {
            final Plugin plugin = read(folder);
            if (plugin != null) {
                found.add(plugin);
            }
        }
        Collections.sort(found, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return found;
    }

    private static Plugin read(File folder) {
        final File manifest = new File(folder, "manifest.json");
        if (!folder.isDirectory() || !manifest.exists()) {
            return null;
        }
        try {
            final JSONObject json = new JSONObject(readAll(new java.io.FileInputStream(manifest)));
            final List<String> permissions = new ArrayList<>();
            final JSONArray array = json.optJSONArray("permissions");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    permissions.add(array.optString(i));
                }
            }
            return new Plugin(
                    json.optString("id", folder.getName()),
                    json.optString("name", folder.getName()),
                    json.optString("version", "?"),
                    json.optString("author", "?"),
                    json.optString("description", ""),
                    permissions,
                    folder);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        stream.close();
        return out.toString("UTF-8");
    }

    /**
     * Распаковывает .marp во временную папку и возвращает, что там лежит.
     * Ставить сразу нельзя: человек должен сначала увидеть, кто автор и что
     * плагин о себе заявляет, — а это написано внутри архива.
     *
     * Пути из архива чистятся: запись вида «../../что-то» в обычном
     * распаковщике вылезает за папку плагина и пишет куда попало. Такие
     * записи пропускаются.
     */
    public static Plugin stage(Context context, InputStream source) {
        File folder = null;
        try {
            folder = new File(root(), "tmp_" + System.currentTimeMillis());
            folder.mkdirs();
            final ZipInputStream zip = new ZipInputStream(source);
            ZipEntry entry;
            final byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                final String name = entry.getName();
                if (entry.isDirectory() || name.contains("..") || name.startsWith("/")) {
                    continue;
                }
                final File out = new File(folder, name);
                final File parent = out.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                final FileOutputStream stream = new FileOutputStream(out);
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    stream.write(buffer, 0, read);
                }
                stream.close();
            }
            zip.close();

            final Plugin plugin = read(folder);
            if (plugin == null) {
                delete(folder);
                return null;
            }
            return plugin;
        } catch (Exception e) {
            FileLog.e(e);
            if (folder != null) {
                delete(folder);
            }
            return null;
        }
    }

    /**
     * Переносит распакованное на место. Плагин с тем же номером заменяется —
     * так обновление не плодит копии.
     */
    public static Plugin commit(Plugin staged) {
        if (staged == null) {
            return null;
        }
        final File target = new File(root(), staged.id);
        // Свои настройки плагина переживают обновление, но не удаление:
        // удалил — значит, отказался.
        delete(target);
        staged.folder.renameTo(target);
        return read(target);
    }

    /** Передумали на окне установки — временная папка не должна остаться. */
    public static void discard(Plugin staged) {
        if (staged != null) {
            delete(staged.folder);
        }
    }

    public static Plugin install(Context context, InputStream source) {
        return commit(stage(context, source));
    }

    public static void remove(Plugin plugin) {
        if (plugin != null) {
            delete(plugin.folder);
            MargeletConfig.setPluginEnabled(plugin.id, false);
        }
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        final File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        file.delete();
    }

    /**
     * Кладёт пример плагина при первом запуске. Выключенным: пример нужен,
     * чтобы его открыли и прочитали, а не чтобы он что-то делал сам.
     */
    public static void preinstallExample() {
        if (!MargeletConfig.claimExamplePlugin()) {
            return;
        }
        try {
            final Context context = ApplicationLoader.applicationContext;
            final Plugin plugin = install(context, context.getAssets().open("margelet_example.marp"));
            if (plugin != null) {
                MargeletConfig.setPluginEnabled(plugin.id, false);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Есть ли что запускать. Питон не поднимаем зря: это одиннадцать мегабайт. */
    public static boolean anyEnabled() {
        if (!MargeletConfig.pluginsEnabled()) {
            return false;
        }
        for (Plugin plugin : installed()) {
            if (plugin.enabled()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Как назвать разрешение по-человечески. Незнакомое имя показываем как
     * есть: автор мог написать что угодно, и подменять это на «прочее» —
     * значит прятать.
     */
    public static String permissionName(String key) {
        if ("read_chats".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermRead);
        } else if ("send_messages".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermSend);
        } else if ("edit_messages".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermEdit);
        } else if ("delete_messages".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermDelete);
        } else if ("change_profile".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermProfile);
        } else if ("ui".equals(key)) {
            return LocaleController.getString(R.string.MargeletPluginPermUi);
        }
        return key;
    }
}
