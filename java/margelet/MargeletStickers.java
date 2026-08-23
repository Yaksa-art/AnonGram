package org.telegram.margelet;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Свой набор стикеров в панели, первым.
 *
 * Клиентски, а не серверно: на аккаунт ничего не ставится, в список
 * установленных наборов на сервере набор не попадает. Он просто подкладывается
 * в начало того списка, который панель читает у себя в памяти. Выключил
 * настройку — исчез, и на аккаунте от него не осталось следа.
 *
 * Отдельно скажу, чего тут нет: это самое непроверяемое место за сегодня.
 * Всё остальное я мог либо посчитать, либо отрисовать, либо расшифровать. А
 * подкладывание в чужой список проверяется только на живом телефоне.
 */
public class MargeletStickers {

    private static final String SHORT_NAME = "MargeletPackMargeletter";

    private static TLRPC.TL_messages_stickerSet loaded;
    private static boolean loading;

    /** Кладёт набор в начало списка, если он уже загружен. Иначе просит его. */
    public static void inject(int account, ArrayList<TLRPC.TL_messages_stickerSet> sets) {
        if (sets == null || !MargeletConfig.stickersEnabled()) {
            if (sets != null && loaded != null) {
                sets.remove(loaded);
            }
            return;
        }
        if (loaded == null) {
            load(account);
            return;
        }
        final int at = sets.indexOf(loaded);
        if (at == 0) {
            return;
        }
        if (at > 0) {
            sets.remove(at);
        }
        // Если человек поставил набор себе по-настоящему, второй раз его
        // подкладывать не надо — просто поднимаем существующий наверх.
        for (int i = 0; i < sets.size(); i++) {
            final TLRPC.TL_messages_stickerSet set = sets.get(i);
            if (set != null && set.set != null && SHORT_NAME.equalsIgnoreCase(set.set.short_name)) {
                sets.remove(i);
                sets.add(0, set);
                return;
            }
        }
        sets.add(0, loaded);
    }

    private static void load(int account) {
        if (loading) {
            return;
        }
        loading = true;
        final TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        final TLRPC.TL_inputStickerSetShortName name = new TLRPC.TL_inputStickerSetShortName();
        name.short_name = SHORT_NAME;
        req.stickerset = name;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            loading = false;
            if (response instanceof TLRPC.TL_messages_stickerSet) {
                loaded = (TLRPC.TL_messages_stickerSet) response;
            }
        });
    }
}
