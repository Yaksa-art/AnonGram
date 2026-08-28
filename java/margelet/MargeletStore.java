package org.telegram.margelet;

import android.content.Context;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Магазин плагинов: обычный канал вместо своего склада.
 *
 * Склад пришлось бы содержать, охранять и кому-то доверять. Канал уже есть у
 * телеграма: автор выкладывает туда .marp, подпись под файлом ставит телеграм,
 * а кто и когда выложил — видно и без нас. Нам остаётся прочитать список.
 *
 * Ничего своего мы про эти плагины не утверждаем. Магазин показывает то, что
 * лежит в канале, и ровно в том виде, в каком лежит: проверять чужой код нам
 * нечем, и делать вид, что он проверен, было бы враньём. Окно установки
 * спрашивает про каждый так же строго, как про принесённый файлом.
 */
public class MargeletStore {

    /** Где лежат плагины. Публичный канал, читать может кто угодно. */
    public static final String CHANNEL = "margelet_marps";

    /** Что считаем плагином. */
    private static final String EXTENSION = ".marp";

    private static long channelId;

    public static class Item {
        /** Имя файла без расширения — оно же имя плагина в списке. */
        public final String name;
        /** Подпись под файлом: что автор написал про плагин. */
        public final String about;
        public final int date;
        public final long size;
        public final int messageId;
        public final MessageObject message;
        public final TLRPC.Document document;

        Item(String name, String about, int date, long size, int messageId,
             MessageObject message, TLRPC.Document document) {
            this.name = name;
            this.about = about;
            this.date = date;
            this.size = size;
            this.messageId = messageId;
            this.message = message;
            this.document = document;
        }
    }

    public interface Items {
        /** {@code problem} — причина неудачи или null. Пусто и сломано — разное. */
        void onItems(List<Item> items, String problem);
    }

    private static AccountInstance account() {
        return AccountInstance.getInstance(UserConfig.selectedAccount);
    }

    /**
     * Список плагинов из канала, новые сверху.
     *
     * Читаем историю, а не поиск: поиск по каналу знает только текст, а нам
     * нужны вложения, и свежее он видит с задержкой. Для списка «что выложили
     * последним» история и есть правильный источник.
     */
    public static void list(Items done) {
        resolve(dialogId -> {
            if (dialogId == 0) {
                done.onItems(new ArrayList<>(), "канал не нашёлся");
                return;
            }
            final TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            req.peer = account().getMessagesController().getInputPeer(dialogId);
            if (req.peer == null) {
                done.onItems(new ArrayList<>(), "канал не открывается");
                return;
            }
            req.limit = 100;
            account().getConnectionsManager().sendRequest(req, (response, error) ->
                    AndroidUtilities.runOnUIThread(() -> {
                        if (error != null) {
                            FileLog.e("margy: канал плагинов не пришёл: " + error.text);
                            done.onItems(new ArrayList<>(), error.text);
                            return;
                        }
                        done.onItems(collect(response), null);
                    }));
        });
    }

    private static List<Item> collect(org.telegram.tgnet.TLObject response) {
        final List<Item> out = new ArrayList<>();
        if (!(response instanceof TLRPC.messages_Messages)) {
            return out;
        }
        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
        account().getMessagesController().putUsers(res.users, false);
        account().getMessagesController().putChats(res.chats, false);
        for (TLRPC.Message message : res.messages) {
            if (message == null || message.media == null) {
                continue;
            }
            final TLRPC.Document document = message.media.document;
            if (document == null) {
                continue;
            }
            final String file = nameOf(document);
            if (file == null || !file.toLowerCase().endsWith(EXTENSION)) {
                continue;
            }
            final MessageObject object =
                    new MessageObject(UserConfig.selectedAccount, message, true, true);
            out.add(new Item(file.substring(0, file.length() - EXTENSION.length()),
                    message.message == null ? "" : message.message,
                    message.date, document.size, message.id, object, document));
        }
        // История приходит новым вперёд, но полагаться на это не будем:
        // порядок «сначала новые» владелец попросил прямо.
        java.util.Collections.sort(out, (a, b) -> b.date - a.date);
        return out;
    }

    private static String nameOf(TLRPC.Document document) {
        try {
            for (TLRPC.DocumentAttribute attribute : document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                    return ((TLRPC.TL_documentAttributeFilename) attribute).file_name;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void resolve(MargeletGroup.Peer done) {
        if (channelId != 0) {
            done.onPeer(channelId);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                account().getMessagesController().getUserNameResolver().resolve(CHANNEL, id -> {
                    channelId = id == null ? 0 : id;
                    done.onPeer(channelId);
                });
            } catch (Throwable t) {
                FileLog.e(t);
                done.onPeer(0);
            }
        });
    }

    public interface Ready {
        /** Файл на диске или null, если скачать не вышло. */
        void onReady(File file);
    }

    /**
     * Приносит файл плагина на диск и зовёт обратно.
     *
     * Может быть, он уже скачан — телеграм хранит скачанное у себя. Если нет,
     * ждём именно окончания загрузки, а не отмеренное наугад время: файл
     * весит сколько весит, а связь бывает какая угодно.
     */
    public static void fetch(Item item, Ready done) {
        final File ready = FileLoader.getInstance(UserConfig.selectedAccount)
                .getPathToAttach(item.document, true);
        if (ready != null && ready.exists() && ready.length() > 0) {
            done.onReady(ready);
            return;
        }
        final String name = FileLoader.getAttachFileName(item.document);
        final NotificationCenter center = NotificationCenter.getInstance(UserConfig.selectedAccount);
        final NotificationCenter.NotificationCenterDelegate[] holder =
                new NotificationCenter.NotificationCenterDelegate[1];
        holder[0] = (id, account, args) -> {
            if (args.length == 0 || !name.equals(args[0])) {
                return;
            }
            center.removeObserver(holder[0], NotificationCenter.fileLoaded);
            center.removeObserver(holder[0], NotificationCenter.fileLoadFailed);
            if (id == NotificationCenter.fileLoadFailed) {
                done.onReady(null);
                return;
            }
            final File file = FileLoader.getInstance(UserConfig.selectedAccount)
                    .getPathToAttach(item.document, true);
            done.onReady(file != null && file.exists() ? file : null);
        };
        center.addObserver(holder[0], NotificationCenter.fileLoaded);
        center.addObserver(holder[0], NotificationCenter.fileLoadFailed);
        FileLoader.getInstance(UserConfig.selectedAccount)
                .loadFile(item.document, item.message, FileLoader.PRIORITY_NORMAL, 0);
    }

    /** Скачать и показать обычное окно установки — то же, что и для файла. */
    public static void install(Context context, Item item, Runnable installed, Runnable failed) {
        fetch(item, file -> {
            if (file == null) {
                if (failed != null) {
                    failed.run();
                }
                return;
            }
            boolean known = false;
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                known = MargeletPlugins.askInstall(context, in, installed);
            } catch (Throwable t) {
                FileLog.e(t);
            }
            if (!known && failed != null) {
                failed.run();
            }
        });
    }
}
