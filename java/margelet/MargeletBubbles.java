package org.telegram.margelet;

import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * Свои сообщения — цветом своего градиента профиля.
 *
 * Видно это только тому, кто включил: цвет подменяется при показе, в сами
 * сообщения ничего не дописывается и никуда не отправляется. Собеседник видит
 * обычные пузыри своей темы, и знать про эту настройку ему незачем.
 *
 * Перехват идёт по ключу цвета — тем же способом, каким это уже делает
 * «приступ». Тема при этом не трогается: выключил — и всё вернулось само,
 * чинить нечего.
 */
public class MargeletBubbles {

    /**
     * Ключи, которыми красится исходящий пузырь.
     *
     * Их шесть, а не один: у пузыря есть выделенное состояние, тень и три
     * точки градиента на случай, когда тема рисует его переливом. Подмени мы
     * только основной — выделенное сообщение осталось бы прежнего цвета, и это
     * заметили бы первым же длинным нажатием.
     */
    private static boolean ours(int key) {
        return key == Theme.key_chat_outBubble
                || key == Theme.key_chat_outBubbleSelected
                || key == Theme.key_chat_outBubbleGradient1
                || key == Theme.key_chat_outBubbleGradient2
                || key == Theme.key_chat_outBubbleGradient3;
    }

    private static int[] lastPair;
    private static int plain, selected;

    /** Цвет для ключа или ноль, если этот ключ трогать не надо. */
    public static int colorFor(int key) {
        if (!MargeletConfig.ownBubblesGradient() || !ours(key)) {
            return 0;
        }
        final long me = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (me <= 0) {
            return 0;
        }
        final int[] pair = MargeletGradient.of(me, null);
        if (pair == null) {
            return 0;   // градиента нет — красить нечем, пусть будет как в теме
        }
        // Считаем при смене пары, а не на каждый вызов: за кадр цвет
        // спрашивают тысячи раз, и каждый раз пересчитывать — только мусор
        // копить. Тот же довод, что и у «приступа».
        if (lastPair == null || lastPair[0] != pair[0] || lastPair[1] != pair[1]) {
            lastPair = new int[]{pair[0], pair[1]};
            plain = MargeletGradient.mix(pair[0], pair[1], 0.5f);
            // Выделенное — заметно, но не «другой цвет»: сдвигаем к белому или
            // к чёрному, смотря что читается на этом фоне.
            selected = MargeletGradient.ink(pair) == android.graphics.Color.WHITE
                    ? MargeletGradient.mix(plain, android.graphics.Color.WHITE, 0.18f)
                    : MargeletGradient.mix(plain, android.graphics.Color.BLACK, 0.14f);
        }
        return key == Theme.key_chat_outBubbleSelected ? selected : plain;
    }
}
