package org.telegram.margelet;

import android.graphics.Bitmap;
import android.net.Uri;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Баннер профиля — картинка за аватаркой.
 *
 * Лежит не у нас, а в общей группе: человек отправляет туда фотографию с
 * меткой, и она становится его баннером. Чей баннер — видно по тому, кто
 * отправил, а подпись под сообщением ставит телеграм. Подделать нельзя, и
 * проверять нам нечего.
 *
 * Сменить баннер — это отправить новый и удалить старый. Удалить — просто
 * удалить своё сообщение. Обе вещи человек может сделать и руками, прямо в
 * группе, без нашего приложения: мы не владеем его баннером, мы его только
 * показываем.
 */
public class MargeletBanner {

    /** Найденное держим в памяти: профиль перерисовывается часто, баннер редко. */
    private static final HashMap<Long, Bitmap> pictures = new HashMap<>();
    private static final HashMap<Long, Integer> ownMessage = new HashMap<>();
    private static final Set<Long> looking = new HashSet<>();
    private static final Set<Long> missing = new HashSet<>();

    private static AccountInstance account() {
        return AccountInstance.getInstance(UserConfig.selectedAccount);
    }

    private static long me() {
        try {
            return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Баннер этого человека, если он уже у нас есть.
     *
     * Зовётся из отрисовки, поэтому ничего не ждёт и ничего не качает: нет —
     * рисуем как раньше, а картинку тем временем принесут и позовут
     * {@code whenReady}.
     */
    public static Bitmap of(long userId, Runnable whenReady) {
        if (userId <= 0 || !MargeletConfig.bannersEnabled()) {
            return null;
        }
        synchronized (pictures) {
            final Bitmap ready = pictures.get(userId);
            if (ready != null) {
                return ready;
            }
            if (missing.contains(userId) || looking.contains(userId)) {
                return null;
            }
            looking.add(userId);
        }
        MargeletGroup.find(MargeletGroup.TAG_BANNER, 60, messages -> {
            MessageObject mine = null;
            for (MessageObject message : messages) {
                // Метка одна на всех, поэтому ищем по автору: чей баннер —
                // решает подпись телеграма, а не текст сообщения.
                if (MargeletGroup.authorOf(message) == userId && message.getDocument() == null) {
                    mine = message;
                    break;      // новое сверху, первое совпадение и есть свежее
                }
            }
            if (mine == null) {
                synchronized (pictures) {
                    looking.remove(userId);
                    missing.add(userId);
                }
                return;
            }
            if (userId == me()) {
                ownMessage.put(userId, mine.getId());
            }
            load(userId, mine, whenReady);
        });
        return null;
    }

    /**
     * Забирает саму картинку. Файл может быть уже скачан — телеграм хранит
     * скачанное у себя, и второй раз в сеть за ним не пойдёт.
     */
    private static void load(long userId, MessageObject message, Runnable whenReady) {
        try {
            final TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(
                    message.photoThumbs, 1280, false, null, true);
            if (size == null) {
                synchronized (pictures) {
                    looking.remove(userId);
                    missing.add(userId);
                }
                return;
            }
            final File file = FileLoader.getInstance(UserConfig.selectedAccount)
                    .getPathToAttach(size, true);
            if (file != null && file.exists()) {
                decode(userId, file, whenReady);
                return;
            }
            FileLoader.getInstance(UserConfig.selectedAccount).loadFile(
                    ImageLocation.getForObject(size, message.photoThumbsObject), message,
                    null, FileLoader.PRIORITY_LOW, FileLoader.PRELOAD_CACHE_TYPE);
            // Ждать окончания загрузки здесь нечем и незачем: спросим ещё раз
            // при следующем открытии профиля, к тому времени файл будет.
            AndroidUtilities.runOnUIThread(() -> {
                final File later = FileLoader.getInstance(UserConfig.selectedAccount)
                        .getPathToAttach(size, true);
                if (later != null && later.exists()) {
                    decode(userId, later, whenReady);
                } else {
                    synchronized (pictures) {
                        looking.remove(userId);
                    }
                }
            }, 2500);
        } catch (Throwable t) {
            FileLog.e(t);
            synchronized (pictures) {
                looking.remove(userId);
                missing.add(userId);
            }
        }
    }

    private static void decode(long userId, File file, Runnable whenReady) {
        Bitmap bitmap = null;
        try {
            final android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
            // Баннер рисуется полосой в пару сотен точек высотой. Держать в
            // памяти полноразмерную фотографию ради этого незачем.
            options.inSampleSize = 2;
            bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            FileLog.e(t);
        }
        synchronized (pictures) {
            looking.remove(userId);
            if (bitmap != null) {
                pictures.put(userId, bitmap);
            } else {
                missing.add(userId);
            }
        }
        if (bitmap != null && whenReady != null) {
            AndroidUtilities.runOnUIThread(whenReady);
        }
    }

    /** Забыть скачанное: свой баннер поменяли — старый показывать нельзя. */
    public static void forget(long userId) {
        synchronized (pictures) {
            pictures.remove(userId);
            missing.remove(userId);
            looking.remove(userId);
        }
    }

    /**
     * Поставить себе баннер: отправить картинку в группу с меткой, а прошлую
     * убрать.
     *
     * Порядок именно такой — сначала отправляем, потом удаляем старое. Наоборот
     * было бы хуже: если отправка не пройдёт, человек останется вообще без
     * баннера, хотя ничего не просил удалять.
     */
    public static void set(Uri image, Runnable done) {
        final long id = me();
        if (id <= 0 || image == null) {
            if (done != null) {
                done.run();
            }
            return;
        }
        MargeletGroup.resolve(dialogId -> {
            if (dialogId == 0) {
                if (done != null) {
                    done.run();
                }
                return;
            }
            final Integer old = ownMessage.get(id);
            try {
                SendMessagesHelper.prepareSendingPhoto(account(), null, image, dialogId,
                        null, null, null, MargeletGroup.TAG_BANNER, null, null, null, 0,
                        null, true, 0, 0, null);
            } catch (Throwable t) {
                FileLog.e(t);
            }
            forget(id);
            if (old != null) {
                // Старое убираем с задержкой: пусть новое сперва уйдёт.
                AndroidUtilities.runOnUIThread(() -> MargeletGroup.remove(old), 4000);
                ownMessage.remove(id);
            }
            if (done != null) {
                done.run();
            }
        });
    }

    /** Убрать свой баннер — то есть удалить своё сообщение из группы. */
    public static void clear(Runnable done) {
        final long id = me();
        final Integer old = ownMessage.get(id);
        if (old != null) {
            MargeletGroup.remove(old);
            ownMessage.remove(id);
        } else {
            // Номера сообщения не знаем — найдём и удалим.
            MargeletGroup.find(MargeletGroup.TAG_BANNER, 60, messages -> {
                for (MessageObject message : messages) {
                    if (MargeletGroup.authorOf(message) == id) {
                        MargeletGroup.remove(message.getId());
                        break;
                    }
                }
            });
        }
        forget(id);
        if (done != null) {
            done.run();
        }
    }
}
