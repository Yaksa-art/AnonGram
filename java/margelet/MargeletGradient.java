package org.telegram.margelet;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    // --- рисование ---------------------------------------------------------
    //
    // Градиент линейный и идёт снизу вверх: первый цвет внизу, второй наверху.
    // Радиальный, взятый поначалу из телеграмовской шапки, читался как пятно
    // света посередине, а не как переход; горизонтальный был уже переходом, но
    // не тем — у профиля высота работает лучше ширины, потому что вниз он
    // длинный.

    /** Общая кисть. Только для главного потока: рисование живёт только в нём. */
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static int builtFrom, builtTo;
    private static float builtHeight;

    /**
     * Залить прямоугольник градиентом снизу вверх.
     *
     * Высота входит в ключ пересборки наравне с цветами: растяжка у заливки
     * своя, и пара, построенная на низкий образец, на высокой странице дала бы
     * переход не там, где нужно.
     */
    public static void paint(Canvas canvas, int[] pair,
                             float left, float top, float right, float bottom, int alpha) {
        if (canvas == null || pair == null || pair.length < 2
                || right <= left || bottom <= top) {
            return;
        }
        final float width = right - left;
        final float height = bottom - top;
        if (FILL.getShader() == null || builtFrom != pair[0] || builtTo != pair[1]
                || builtHeight != height) {
            builtFrom = pair[0];
            builtTo = pair[1];
            builtHeight = height;
            FILL.setShader(new LinearGradient(0, height, 0, 0,
                    pair[0], pair[1], Shader.TileMode.CLAMP));
        }
        FILL.setAlpha(alpha);
        canvas.save();
        canvas.translate(left, top);
        canvas.drawRect(0, 0, width, height, FILL);
        canvas.restore();
    }

    /**
     * Чем писать поверх градиента: белым или чёрным.
     *
     * Считаем по воспринимаемой яркости, а не по среднему трёх чисел: глаз
     * видит зелёное куда светлее синего той же величины, и среднее назвало бы
     * тёмно-синий светлее травяного. Веса — обычные для яркости, 0.299 / 0.587
     * / 0.114.
     *
     * Берём середину пары: краем градиента может быть и тёмное, и светлое, а
     * текст один на всю шапку.
     */
    /** Наш порог: светлее — пишем чёрным, темнее — белым. */
    public static final double LIGHT = 0.62;

    /**
     * Порог, по которому сам телеграм решает, светлая ли кнопка.
     *
     * Лежит в AndroidUtilities: {@code computePerceivedBrightness(color) < 0.721f}.
     * Записан здесь потому, что от него зависит цвет подписи на кнопке, и наш
     * цвет кнопки обязан ложиться по нужную его сторону. Разойдись эти два
     * решения — и на тёмном градиенте выйдет тёмная кнопка с чёрной подписью.
     */
    public static final double TELEGRAM_LIGHT = 0.721;

    public static int ink(int[] pair) {
        if (pair == null || pair.length < 2) {
            return Color.WHITE;
        }
        return brightness(mix(pair[0], pair[1], 0.5f)) > LIGHT ? Color.BLACK : Color.WHITE;
    }

    /**
     * Воспринимаемая яркость — теми же весами, что у телеграма (Rec. 709).
     *
     * Сперва я взял привычные 0.299/0.587/0.114, и это была ошибка не в
     * арифметике, а в согласии: подпись на кнопке телеграм считает по своим
     * весам, и два разных счёта расходились ровно там, где важнее всего, — на
     * границе светлого и тёмного.
     */
    public static double brightness(int color) {
        return (0.2126 * Color.red(color)
                + 0.7152 * Color.green(color)
                + 0.0722 * Color.blue(color)) / 255.0;
    }

    /** Сдвинуть цвет к заданной яркости — к белому или к чёрному. */
    public static int toBrightness(int color, double target) {
        final double now = brightness(color);
        if (target > now) {
            return now >= 1 ? color
                    : mix(color, Color.WHITE, (float) ((target - now) / (1 - now)));
        }
        return now <= 0 ? color
                : mix(color, Color.BLACK, (float) (1 - target / now));
    }

    /**
     * Цвет кнопок поверх градиента — его собственный, только приглушённый.
     *
     * Сперва здесь была полупрозрачная белая или чёрная краска. Она и правда
     * «чуть затемняет», но выглядит это как белая плашка на зелёном, а не как
     * кнопка того же цвета: владелец увидел ровно это и сказал, что фон должен
     * подстраиваться под градиент, а не быть белым или чёрным.
     *
     * Поэтому берём середину пары — цвет самого градиента — и сдвигаем её от
     * фона: светлый градиент затемняем, тёмный осветляем. Кнопка остаётся его
     * цвета и при этом видна.
     */
    public static int buttons(int[] pair) {
        if (pair == null || pair.length < 2) {
            return 0x22000000;
        }
        final int middle = mix(pair[0], pair[1], 0.5f);
        final double now = brightness(middle);
        // Цель по яркости, а не «смешать на столько-то»: кнопка обязана лечь
        // по ту же сторону телеграмовского порога, что и наш выбор цвета
        // текста. Иначе выходит тёмная кнопка с чёрной подписью — ровно то,
        // что владелец и увидел.
        final double target = ink(pair) == Color.BLACK
                ? Math.max(now - 0.06, TELEGRAM_LIGHT + 0.08)
                : Math.min(now + 0.10, TELEGRAM_LIGHT - 0.14);
        return toBrightness(middle, target);
    }

    /**
     * Цвет карточки на странице профиля — сам градиент, только темнее.
     *
     * Тема здесь больше не участвует, и это не упрощение, а требование: при
     * включённом градиенте профиль должен выглядеть одинаково на любой теме.
     * Прежде цвет темы подмешивался, и на светлой теме тёмный градиент давал
     * серую карточку с сероватым текстом — владелец назвал это ужасным, и был
     * прав: смесь чёрного с белым и есть серый.
     *
     * Читаемость держится не придерживанием яркости, а тем, что цвет текста мы
     * теперь тоже назначаем сами — см. {@link Palette}.
     */
    public static int card(int[] pair) {
        if (pair == null || pair.length < 2) {
            return Color.BLACK;
        }
        final int middle = mix(pair[0], pair[1], 0.5f);
        return toBrightness(middle, brightness(middle) * 0.86);
    }

    /**
     * Палитра профиля: пока стоит градиент, цвета берутся у него, а не у темы.
     *
     * «Профили должны выглядеть абсолютно одинаково на всех темах, и все цвета
     * текстов тоже» — это требование владельца, и оно же снимает противоречие,
     * из-за которого прежний вид разваливался. Пока карточку красили мы, а
     * текст на ней — тема, приходилось придерживать яркость карточки, чтобы
     * чужой текст остался виден; на светлой теме от этого получался серый на
     * сером. Как только цвет текста тоже наш, придерживать нечего.
     *
     * Это обёртка над обычным поставщиком цветов телеграма, а не подмена темы:
     * ключи, которых здесь нет, спрашиваются у него как раньше. И работает она
     * только там, куда её передали, — на странице профиля.
     *
     * Цвета окон (ключи dialog*) накрыты нарочно, хотя окно — не страница.
     * Содержимое профиля умеет открывать окна само (карточка подарка — из
     * карусели подарков), и палитра туда попадает вместе с ним. Накрыв только
     * текст, мы уже получили белое по белому; поэтому накрыт и фон — пусть
     * лучше окно выйдет одного цвета со страницей, чем невидимым.
     */
    public static final class Palette implements org.telegram.ui.ActionBar.Theme.ResourcesProvider {

        private final org.telegram.ui.ActionBar.Theme.ResourcesProvider under;
        private final long userId;

        public Palette(org.telegram.ui.ActionBar.Theme.ResourcesProvider under, long userId) {
            this.under = under;
            this.userId = userId;
        }

        private int base(int key) {
            return under != null ? under.getColor(key)
                    : org.telegram.ui.ActionBar.Theme.getColor(key);
        }

        @Override
        public int getColor(int key) {
            final int[] pair = userId == 0 ? null : of(userId, null);
            if (pair == null) {
                return base(key);
            }
            final org.telegram.ui.ActionBar.Theme.ResourcesProvider none = null;
            final int текст = ink(pair);
            if (key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite
                    || key == org.telegram.ui.ActionBar.Theme.key_dialogBackground) {
                return card(pair);
            }
            if (key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundGray
                    || key == org.telegram.ui.ActionBar.Theme.key_dialogBackgroundGray) {
                return mix(pair[0], pair[1], 0.5f);
            }
            if (key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText
                    || key == org.telegram.ui.ActionBar.Theme.key_dialogTextBlack) {
                return текст;
            }
            if (key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteValueText
                    || key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlueText
                    || key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteLinkText
                    || key == org.telegram.ui.ActionBar.Theme.key_dialogTextLink) {
                // Ссылки и значения — тем же цветом, но заметно ярче обычного
                // текста, иначе их не отличить от подписей.
                return alpha(текст, 0.92f);
            }
            if (key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText
                    || key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText2
                    || key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText3
                    || key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText4
                    || key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText5
                    || key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText6
                    || key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText7
                    || key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText8
                    || key == org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteHintText
                    || key == org.telegram.ui.ActionBar.Theme.key_dialogTextGray2
                    || key == org.telegram.ui.ActionBar.Theme.key_dialogTextGray3) {
                return alpha(текст, 0.60f);
            }
            if (key == org.telegram.ui.ActionBar.Theme.key_divider
                    || key == org.telegram.ui.ActionBar.Theme.key_dialogGrayLine) {
                return alpha(текст, 0.15f);
            }
            if (key == org.telegram.ui.ActionBar.Theme.key_sheet_scrollUp) {
                return alpha(текст, 0.35f);
            }
            if (key == org.telegram.ui.ActionBar.Theme.key_listSelector
                    || key == org.telegram.ui.ActionBar.Theme.key_settings_listSelector) {
                return alpha(текст, 0.10f);
            }
            return base(key);
        }

        @Override
        public boolean isDark() {
            final int[] pair = userId == 0 ? null : of(userId, null);
            // Пока стоит градиент, «тёмная ли тема» решает он, а не тема: иначе
            // то, что спрашивает об этом, оденется по чужой теме и разойдётся
            // со всем остальным на экране.
            return pair == null ? org.telegram.ui.ActionBar.Theme.isCurrentThemeDark()
                    : ink(pair) == Color.WHITE;
        }
    }

    /** Тот же цвет, но прозрачнее. */
    public static int alpha(int color, float part) {
        return Color.argb((int) (255 * Math.max(0, Math.min(1, part))),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Смешать два цвета: 0 — весь первый, 1 — весь второй. */
    public static int mix(int from, int to, float part) {
        return Color.argb(
                (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * part),
                (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * part),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * part),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * part));
    }

    /**
     * Подложка страницы профиля: обычный фон, а поверх — градиент человека.
     *
     * Именно рисуемая подложка, а не заданный один раз цвет. Градиент
     * приезжает из группы позже, чем создаётся экран, и цвет, поставленный при
     * создании, так и остался бы серым до следующего захода. Подложка
     * спрашивает цвета на каждой отрисовке и перерисовывает себя сама, когда
     * они появятся.
     */
    public static final class Backdrop extends Drawable {

        private final long userId;
        private final Paint plain = new Paint();

        public Backdrop(long userId, int base) {
            this.userId = userId;
            plain.setColor(base);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();
            canvas.drawRect(bounds, plain);
            final int[] pair = of(userId, this::invalidateSelf);
            if (pair != null) {
                MargeletGradient.paint(canvas, pair, bounds.left, bounds.top,
                        bounds.right, bounds.bottom, 255);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            plain.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            plain.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    private static void answer(MargeletGroup.Removed done, int what) {
        if (done != null) {
            AndroidUtilities.runOnUIThread(() -> done.onRemoved(what));
        }
    }
}
