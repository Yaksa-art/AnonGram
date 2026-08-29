package org.telegram.margelet;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Небольшие файлы с гитхаба: список значков, номер последней версии.
 *
 * Смысл в том, чтобы такие вещи правились без пересборки клиента — дописал
 * строчку в репозитории, и она приехала людям. Поэтому здесь нет ничего, кроме
 * «скачать текст и запомнить»: разбор формата — дело того, кто заказывал.
 *
 * Скачанное всегда кладётся в настройки. Без сети показывается последнее
 * скачанное, а пока не скачалось ни разу — тот, кто спрашивал, обходится своим
 * вшитым запасом. Значит, ни один экран не зависит от того, есть ли интернет.
 */
public class MargeletRemote {

    /** Куда смотрим за файлами. Ветка main репозитория форка. */
    public static final String BASE =
            "https://raw.githubusercontent.com/narezany/margelet/main/";

    private static final String PREFS = "margelet_remote";
    /** Файлы здесь маленькие; всё, что больше, — уже не наш файл. */
    private static final int MAX_BYTES = 256 * 1024;
    private static final int TIMEOUT_MS = 15000;

    /**
     * Значение на языке приложения: сперва ключ с суффиксом языка, потом
     * основной. Так устроены и манифесты плагинов — формат один, чтобы
     * человеку не приходилось помнить два.
     */
    public static String localized(JSONObject json, String key, String fallback) {
        String language = null;
        try {
            language = LocaleController.getInstance().getCurrentLocale().getLanguage();
        } catch (Exception ignored) {
        }
        if (language != null) {
            final String value = json.optString(key + "_" + language, null);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return json.optString(key, fallback);
    }

    public interface Callback {
        /** Вызывается в главном потоке. text — null, если скачать не вышло. */
        void onResult(String text);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Перечитать файл, если прошлый ответ старше указанного срока.
     *
     * Списку котов не нужна свежесть до секунды, а лишний запрос при каждом
     * открытии экрана — это трафик человека за наше удобство. Поэтому спрашиваем
     * по возрасту: старее срока — идём в сеть, иначе живём с тем, что есть.
     */
    public static void refreshIfOlder(String path, String key, long ageMs, Callback callback) {
        if (System.currentTimeMillis() - cachedAt(key) < ageMs) {
            if (callback != null) {
                callback.onResult(null);
            }
            return;
        }
        fetch(path, key, callback);
    }

    /** Куда складываем скачанные картинки. */
    private static File imagesDir() {
        final File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "margelet_remote");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public interface ImageCallback {
        /** Файл картинки или null, если скачать не вышло. */
        void onImage(File file);
    }

    /**
     * Приносит картинку по адресу и отдаёт файл.
     *
     * Скачанное лежит в кэше под именем от адреса: тот же адрес — тот же файл,
     * второй раз в сеть не пойдём. Кэш система вправе почистить, и это
     * нормально: не нашли — скачаем снова.
     */
    /**
     * Уже скачанная картинка или null.
     *
     * Спросить это надо ДО показа: подпись под котом берётся у того же кота,
     * чью картинку мы покажем, а какого именно покажем — решается тем, есть
     * ли она уже на диске. Узнать это через {@link #image} нельзя: он отвечает
     * обратным вызовом, а решение нужно сейчас.
     */
    public static File cachedImage(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        final String full = url.startsWith("http") ? url : BASE + url;
        final File target = new File(imagesDir(), Integer.toHexString(full.hashCode()));
        return target.exists() && target.length() > 0 ? target : null;
    }

    public static void image(String url, ImageCallback callback) {
        if (url == null || url.isEmpty() || callback == null) {
            if (callback != null) {
                callback.onImage(null);
            }
            return;
        }
        final String full = url.startsWith("http") ? url : BASE + url;
        final File target = new File(imagesDir(), Integer.toHexString(full.hashCode()));
        if (target.exists() && target.length() > 0) {
            callback.onImage(target);
            return;
        }
        new Thread(() -> {
            File done = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(full).openConnection();
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", MargeletConfig.APP_NAME);
                if (connection.getResponseCode() == 200) {
                    final File tmp = new File(target.getAbsolutePath() + ".part");
                    long written = 0;
                    try (InputStream in = connection.getInputStream();
                         java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                        final byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            written += read;
                            if (written > MAX_IMAGE_BYTES) {
                                break;
                            }
                            out.write(buffer, 0, read);
                        }
                    }
                    // Недокачанное не переименовываем: обрезанная картинка
                    // хуже отсутствующей — она покажется битой и останется в
                    // кэше навсегда.
                    if (written > 0 && written <= MAX_IMAGE_BYTES && tmp.renameTo(target)) {
                        done = target;
                    } else {
                        tmp.delete();
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final File result = done;
            AndroidUtilities.runOnUIThread(() -> callback.onImage(result));
        }).start();
    }

    /** Картинка кота — не обои: больше этого точно что-то не то. */
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    /** Последнее удачно скачанное содержимое или null. */
    public static String cached(String key) {
        return prefs().getString(key, null);
    }

    /** Когда в последний раз удалось скачать, в миллисекундах. */
    public static long cachedAt(String key) {
        return prefs().getLong(key + "_at", 0);
    }

    /**
     * Качает файл в фоне и отдаёт ответ в главный поток.
     *
     * Удачная загрузка перезаписывает кэш. Неудачная не трогает его вовсе:
     * лучше показать вчерашний список, чем пустой.
     */
    public static void fetch(String path, String key, Callback callback) {
        final Thread worker = new Thread(() -> {
            String result = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BASE + path).openConnection();
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", MargeletConfig.APP_NAME);
                if (connection.getResponseCode() == 200) {
                    try (InputStream in = connection.getInputStream()) {
                        final ByteArrayOutputStream out = new ByteArrayOutputStream();
                        final byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) > 0 && out.size() <= MAX_BYTES) {
                            out.write(buffer, 0, read);
                        }
                        if (out.size() <= MAX_BYTES) {
                            result = out.toString("UTF-8");
                        }
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            if (result != null) {
                prefs().edit()
                        .putString(key, result)
                        .putLong(key + "_at", System.currentTimeMillis())
                        .apply();
            }
            final String delivered = result;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(delivered));
            }
        }, "margelet-remote");
        worker.setDaemon(true);
        worker.start();
    }
}
