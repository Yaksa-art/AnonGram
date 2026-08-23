package org.telegram.margelet;

import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Канал форка первой строкой в списке чатов.
 *
 * Подписки он не требует: строка появляется, даже если человек в канале не
 * состоит, — нажатие открывает его обычным предпросмотром с кнопкой
 * «подписаться». Выключается в настройках форка, по умолчанию включён.
 *
 * Настоящий список чатов при этом не трогается: строка добавляется в копию,
 * которую видит только список. Влезать в хранилище телеграма ради украшения
 * нельзя — там лежит переписка, а не наше место под баннер.
 */
public class MargeletChannel {

    /**
     * Номер канала в том виде, в каком его понимает список чатов.
     *
     * У каналов номер переписки — это «минус триллион минус номер канала»;
     * в таблице значков лежит тот же канал, но в другой записи, поэтому
     * держим одно число здесь и одно там, а не одно на два смысла.
     */
    public static final long CHANNEL_ID = 4426743212L;
    public static final long DIALOG_ID = -1000000000000L - CHANNEL_ID;

    private static TLRPC.TL_dialog own;
    private static boolean asked;

    /**
     * Список чатов с каналом форка первой строкой.
     *
     * Возвращает тот же список, если добавлять нечего: лишняя копия на каждой
     * перерисовке списка чатов — не то место, где стоит сорить.
     */
    public static ArrayList<TLRPC.Dialog> onTop(int account, ArrayList<TLRPC.Dialog> array,
                                                int dialogsType, int folderId) {
        if (array == null || dialogsType != 0 || folderId != 0 || !MargeletConfig.channelOnTop()) {
            return array;
        }
        TLRPC.Dialog existing = null;
        for (TLRPC.Dialog dialog : array) {
            if (dialog != null && dialog.id == DIALOG_ID) {
                existing = dialog;
                break;
            }
        }
        if (existing == null && !load(account)) {
            return array;   // канал ещё не загружен — рисовать нечего
        }
        if (existing != null && !array.isEmpty() && array.get(0) == existing) {
            return array;   // и так первый
        }
        final ArrayList<TLRPC.Dialog> out = new ArrayList<>(array.size() + 1);
        out.add(existing != null ? existing : own);
        for (TLRPC.Dialog dialog : array) {
            if (dialog != existing) {
                out.add(dialog);
            }
        }
        return out;
    }

    /**
     * Готовит строку канала. Пока канал не подгружен, строке неоткуда взять
     * ни имени, ни снимка, поэтому её просто нет — пустая серая полоска в
     * начале списка выглядела бы поломкой.
     */
    private static boolean load(int account) {
        final MessagesController controller = MessagesController.getInstance(account);
        if (controller.getChat(CHANNEL_ID) == null) {
            if (!asked) {
                asked = true;
                controller.getUserNameResolver().resolve("margeletter", id -> {
                    // Ответ нам не нужен: важно, что канал после этого лежит
                    // в памяти приложения и его можно показать.
                });
            }
            return false;
        }
        if (own == null) {
            own = new TLRPC.TL_dialog();
            own.id = DIALOG_ID;
            own.folder_id = 0;
        }
        return true;
    }
}
