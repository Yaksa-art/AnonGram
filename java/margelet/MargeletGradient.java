package org.telegram.margelet;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Градиент профиля — два своих цвета вместо телеграмовских.
 *
 * Живёт там же, где баннер: в общей группе, сообщением с меткой. Чей градиент
 * — видно по автору сообщения, и подделать это нельзя.
 *
 * Но, в отличие от баннера, это не картинка, а два числа. Поэтому и сообщение
 * не фотография, а строка: {@code #margy_gradient 8DD1B0-B7A8E0}. Так оно
 * весит десяток байт, приходит поиском мгновенно и читается человеком, который
 * забрёл в группу и не знает, что это.
 */
public class MargeletGradient {

    /** Метка градиента. Одна на всех: чей — видно по автору. */
    public static final String TAG = "#margy_gradient";

    /**
     * Что мы согласны считать градиентом.
     *
     * Два цвета, а не четыре и не один. Один — это не градиент, а заливка;
     * больше двух телеграмовский профиль всё равно не нарисует: у него под
     * шапкой радиальный градиент ровно на две точки.
     */
    private static final Pattern PAIR = Pattern.compile(
            "#margy_gradient\\s+([0-9A-Fa-f]{6})-([0-9A-Fa-f]{6})\\b");

    /** Найденное держим в памяти: профиль перерисовывается по многу раз в секунду. */
    private static final HashMap<Long, int[]> colors = new HashMap<>();
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

    /** Собрать строку сообщения из двух цветов. */
    public static String describe(int color1, int color2) {
        return TAG + " " + hex(color1) + "-" + hex(color2);
    }

    private static String hex(int color) {
        return String.format("%06X", color & 0xFFFFFF);
    }

    /**
     * Разобрать сообщение обратно в цвета. Возвращает null, если это не оно.
     *
     * Прозрачность дописываем сами, а не читаем из сообщения: полупрозрачная
     * шапка профиля — это не оформление, а поломка показа, и давать её ставить
     * незачем.
     */
    public static int[] parse(String text) {
        if (text == null) {
            return null;
        }
        final Matcher at = PAIR.matcher(text);
        if (!at.find()) {
            return null;
        }
        try {
            return new int[]{
                    0xFF000000 | Integer.parseInt(at.group(1), 16),
                    0xFF000000 | Integer.parseInt(at.group(2), 16)};
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Градиент этого человека, если он уже у нас есть.
     *
     * Зовётся из отрисовки, поэтому ничего не ждёт и не спрашивает сеть
     * дважды: нет — рисуем как раньше, а ответ придёт и позовёт
     * {@code whenReady}.
     */
    public static int[] of(long userId, Runnable whenReady) {
        // Выключатель — про показ ЧУЖИХ градиентов, как и написано на нём.
        // Свой он не гасит, и это не придирка к слову: свой нужен ещё и
        // выбиральщику, чтобы открыться на том, что у человека стоит. Гаси мы
        // и свой — выбиральщик начал бы с цветов по умолчанию, и одно нажатие
        // «поставить» молча заменило бы человеку его градиент на чужой.
        if (userId <= 0 || (userId != me() && !MargeletConfig.gradientsEnabled())) {
            return null;
        }
        synchronized (colors) {
            final int[] ready = colors.get(userId);
            if (ready != null) {
                return ready;
            }
            if (missing.contains(userId) || looking.contains(userId)) {
                return null;
            }
            looking.add(userId);
        }
        MargeletGroup.find(TAG, userId, 20, (messages, problem) -> {
            int[] found = null;
            int at = 0;
            for (MessageObject message : messages) {
                if (message == null || message.messageOwner == null) {
                    continue;
                }
                final int[] pair = parse(message.messageOwner.message);
                if (pair != null) {
                    found = pair;
                    at = message.getId();
                    break;
                }
            }
            synchronized (colors) {
                looking.remove(userId);
                if (found != null) {
                    colors.put(userId, found);
                    missing.remove(userId);
                } else if (problem == null) {
                    // Не нашли и спрашивать было у кого — значит градиента нет.
                    // Если же группа не ответила, во второй раз спросить стоит:
                    // отсутствие ответа и отсутствие градиента — разные вещи.
                    missing.add(userId);
                }
            }
            if (found != null && at != 0 && userId == me()) {
                ownMessage.put(userId, at);
            }
            if (whenReady != null) {
                AndroidUtilities.runOnUIThread(whenReady);
            }
        });
        return null;
    }

    /** Забыть найденное: градиент поменяли, старый показывать нельзя. */
    public static void forget(long userId) {
        synchronized (colors) {
            colors.remove(userId);
            missing.remove(userId);
            looking.remove(userId);
        }
    }

    /**
     * Поставить себе градиент: написать в группу, а прошлое сообщение убрать.
     *
     * Порядок тот же, что у баннера, и по той же причине: сначала пишем, потом
     * удаляем старое. Наоборот было бы хуже — не пройдёт отправка, и человек
     * останется вообще без градиента, хотя удалять не просил.
     */
    public static void set(int color1, int color2, Runnable done) {
        final long id = me();
        if (id <= 0) {
            if (done != null) {
                done.run();
            }
            return;
        }
        final Integer old = ownMessage.get(id);
        MargeletGroup.post(describe(color1, color2), () -> {
            // Свой градиент показываем немедленно, не дожидаясь, пока сервер
            // вернёт наше же сообщение поиском: человек его только что выбрал
            // и вправе увидеть сразу.
            synchronized (colors) {
                colors.put(id, new int[]{0xFF000000 | (color1 & 0xFFFFFF),
                        0xFF000000 | (color2 & 0xFFFFFF)});
                missing.remove(id);
                looking.remove(id);
            }
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

    /**
     * Убрать свой градиент — то есть удалить своё сообщение из группы.
     *
     * Ответ честный: «убрали», «нечего было убирать» и «не смогли спросить» —
     * это три разных исхода, и говорить на все три одно и то же значит не
     * сказать ничего.
     */
    public static void clear(MargeletGroup.Removed done) {
        final long id = me();
        if (id <= 0) {
            answer(done, MargeletGroup.FAILED);
            return;
        }
        final Integer known = ownMessage.get(id);
        if (known != null) {
            MargeletGroup.remove(known);
            ownMessage.remove(id);
            forget(id);
            answer(done, MargeletGroup.REMOVED);
            return;
        }
        MargeletGroup.find(TAG, id, 20, (messages, problem) -> {
            if (problem != null) {
                answer(done, MargeletGroup.FAILED);
                return;
            }
            int removed = 0;
            for (MessageObject message : messages) {
                if (message != null && message.messageOwner != null
                        && parse(message.messageOwner.message) != null) {
                    MargeletGroup.remove(message.getId());
                    removed++;
                }
            }
            forget(id);
            answer(done, removed > 0 ? MargeletGroup.REMOVED : MargeletGroup.NOTHING);
        });
    }

    private static void answer(MargeletGroup.Removed done, int what) {
        if (done != null) {
            AndroidUtilities.runOnUIThread(() -> done.onRemoved(what));
        }
    }
}
