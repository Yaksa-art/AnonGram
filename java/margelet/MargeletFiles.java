package org.telegram.margelet;

import android.content.Intent;
import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Выбор файла для плагина — названной дверью, а не через потроха андроида.
 *
 * Автор чужого плагина упёрся в это первым: чтобы открыть системный
 * проводник, из питона надо было собрать Intent, а мост питона выбирает
 * конструктор отражением и добирается до скрытого {@code Intent(Parcel)},
 * которого на деле нет. Чинить чужой мост мы не можем и не должны.
 *
 * Но и не в мосте дело. Плагину, которому нужен файл, незачем знать про
 * Intent, про коды ответов и про то, что результат приходит в другой метод
 * другого класса. Всё это — наша забота, а его дело сказать «мне нужен файл»
 * и получить путь.
 *
 * Файл копируем к плагину в папку. Системный проводник отдаёт не путь, а
 * адрес с временным правом на чтение: оно живёт до перезапуска и снаружи
 * питона не открывается. Копия — единственное, чем плагин сможет
 * пользоваться дальше.
 */
public class MargeletFiles {

    /** Свой код ответа. Далеко от телеграмовских, чтобы не столкнуться. */
    private static final int REQUEST = 47110;

    /** Больше этого не копируем: плагин просит файл, а не образ диска. */
    private static final long MAX_SIZE = 64L * 1024 * 1024;

    /** Кому отдать выбранный файл. Интерфейс, потому что его подставляет питон. */
    public interface Picked {
        /** Путь к копии файла или null, если не выбрали или не смогли. */
        void onPicked(String path);
    }

    private static Picked waiting;
    private static String waitingFor;

    /**
     * Спросить у человека файл.
     *
     * @param pluginId кому потом положить копию
     * @param types    какие файлы показывать, например "image/*"; пусто — любые
     * @param done     позовём с путём к копии или с null
     */
    public static void pick(String pluginId, String types, Picked done) {
        if (done == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            final LaunchActivity activity = LaunchActivity.instance;
            if (activity == null) {
                // Приложение свёрнуто или ещё не поднялось: спрашивать некого.
                done.onPicked(null);
                return;
            }
            waiting = done;
            waitingFor = pluginId;
            try {
                final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(types == null || types.isEmpty() ? "*/*" : types);
                activity.startActivityForResult(intent, REQUEST);
            } catch (Throwable t) {
                FileLog.e(t);
                waiting = null;
                waitingFor = null;
                done.onPicked(null);
            }
        });
    }

    /**
     * Пришёл ответ от проводника.
     *
     * Зовётся при любом исходе, в том числе при отказе: обработчик плагина
     * должен быть позван всегда, иначе плагин повиснет в ожидании.
     *
     * @return взяли ли мы этот ответ себе
     */
    public static boolean deliver(int requestCode, boolean ok, Intent data) {
        if (requestCode != REQUEST) {
            return false;
        }
        final Picked done = waiting;
        final String pluginId = waitingFor;
        waiting = null;
        waitingFor = null;
        if (done == null) {
            return true;
        }
        final Uri uri = !ok || data == null ? null : data.getData();
        if (uri == null) {
            // Передумали и закрыли проводник. Это не ошибка, но и не файл, и
            // молчать нельзя: не позвав обработчик, мы оставили бы плагин
            // ждать ответа, который никогда не придёт.
            done.onPicked(null);
            return true;
        }
        // Копирование — работа с диском, ей не место в главном потоке.
        new Thread(() -> {
            final String path = copy(pluginId, uri);
            AndroidUtilities.runOnUIThread(() -> done.onPicked(path));
        }).start();
        return true;
    }

    /** Кладёт выбранное рядом с плагином и отдаёт путь. Null — не вышло. */
    private static String copy(String pluginId, Uri uri) {
        File target = null;
        try {
            final File folder = MargeletPlugins.filesOf(pluginId);
            if (folder == null) {
                return null;
            }
            target = new File(folder, "picked_" + System.currentTimeMillis());
            long written = 0;
            try (InputStream in = ApplicationLoader.applicationContext
                    .getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(target)) {
                if (in == null) {
                    return null;
                }
                final byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    written += read;
                    if (written > MAX_SIZE) {
                        // Обрывать молча нельзя: половина файла хуже, чем его
                        // отсутствие — плагин примет обрезок за целое.
                        out.close();
                        target.delete();
                        return null;
                    }
                    out.write(buffer, 0, read);
                }
            }
            return target.getAbsolutePath();
        } catch (Throwable t) {
            FileLog.e(t);
            if (target != null) {
                target.delete();
            }
            return null;
        }
    }
}
