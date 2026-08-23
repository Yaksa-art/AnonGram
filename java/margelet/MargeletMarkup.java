package org.telegram.margelet;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Своё оформление текста поверх телеграмовского.
 *
 * Телеграм умеет жирный, курсив, зачёркнутый и ещё несколько видов — и список
 * этот закрыт: он лежит на сервере. Дописать в него «размер» нельзя. Поэтому
 * оформление едет прямо в тексте сообщения, невидимыми знаками, а разбирает их
 * уже сам форк.
 *
 * <b>Формат.</b> Кусок текста обёрнут парой меток:
 * <pre>
 *     ОТКРЫТЬ вид значение ... текст ... ЗАКРЫТЬ
 * </pre>
 * Все четыре знака — селекторы начертания (U+FE00…U+FE0F). Это служебный
 * диапазон юникода: он ничего не рисует и в обычном клиенте не виден вовсе.
 * Поэтому у человека без форка сообщение выглядит не «текстом с сором», а
 * просто текстом без оформления.
 *
 * <b>Почему не видимые символы.</b> Владелец предполагал видимые. Невидимые
 * лучше ровно тем, что не портят чтение посторонним: реклама форка и так стоит
 * заголовком, а засорять чужой экран сверх этого незачем. Смена на видимые —
 * это другие значения четырёх констант ниже.
 *
 * <b>Чего этот формат не умеет.</b> Он ничего не подтверждает и не защищает:
 * любой может поставить те же знаки руками. Это оформление, а не подпись.
 */
public class MargeletMarkup {

    /** Начало метки. */
    public static final char OPEN = '\uFE00';
    /** Конец куска. */
    public static final char CLOSE = '\uFE01';
    /** Цифры значения: FE02 + n, n от 0 до 13. */
    private static final char DIGIT = '\uFE02';
    private static final int DIGITS = 14;

    public static final int KIND_SIZE = 0;
    public static final int KIND_DIM = 1;
    public static final int KIND_RAINBOW = 2;

    /**
     * Заголовок, который форк дописывает в начало оформленного сообщения.
     * В самом форке он спрятан, у остальных виден — так и задумано владельцем.
     */
    public static final String HEADER = "<! Message looks better with @margeletter! >";

    /** Размер: от 0,6 до 2,0 обычного. Границы жёсткие с обеих сторон. */
    private static final float SIZE_MIN = 0.6f;
    private static final float SIZE_MAX = 2.0f;

    public static float sizeOf(int value) {
        final int v = Math.max(0, Math.min(DIGITS - 1, value));
        return SIZE_MIN + (SIZE_MAX - SIZE_MIN) * v / (DIGITS - 1);
    }

    /** Ближайшее значение шкалы к нужному множителю. */
    public static int sizeValue(float scale) {
        final float clamped = Math.max(SIZE_MIN, Math.min(SIZE_MAX, scale));
        return Math.round((clamped - SIZE_MIN) * (DIGITS - 1) / (SIZE_MAX - SIZE_MIN));
    }

    private static boolean isDigit(char c) {
        return c >= DIGIT && c < DIGIT + DIGITS;
    }

    private static char digit(int value) {
        return (char) (DIGIT + Math.max(0, Math.min(DIGITS - 1, value)));
    }

    /** Открывающая метка как строка. */
    public static String open(int kind, int value) {
        return "" + OPEN + digit(kind) + digit(value);
    }

    public static String close() {
        return String.valueOf(CLOSE);
    }

    /** Найденный кусок оформления. */
    public static final class Run {
        public final int kind;
        public final int value;
        public final int start;
        public final int end;

        Run(int kind, int value, int start, int end) {
            this.kind = kind;
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Разбирает метки в тексте.
     *
     * Отсчёты возвращаются <b>по исходному тексту, вместе с метками</b> — их
     * никто не вырезает. Причина простая: отсчёты жирного и курсива приходят с
     * сервера и посчитаны по тому же тексту. Вырежешь четыре знака в начале — и
     * весь остальной разбор уедет.
     */
    public static List<Run> parse(CharSequence text) {
        final List<Run> runs = new ArrayList<>();
        if (text == null || text.length() < 4) {
            return runs;
        }
        // Метки могут вкладываться друг в друга, поэтому открытые куски
        // держим стопкой: закрывающий знак закрывает последний открытый.
        final ArrayList<int[]> open = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == OPEN && i + 2 < text.length()
                    && isDigit(text.charAt(i + 1)) && isDigit(text.charAt(i + 2))) {
                open.add(new int[]{text.charAt(i + 1) - DIGIT, text.charAt(i + 2) - DIGIT, i + 3});
                i += 2;
            } else if (c == CLOSE && !open.isEmpty()) {
                final int[] top = open.remove(open.size() - 1);
                if (i > top[2]) {
                    runs.add(new Run(top[0], top[1], top[2], i));
                }
            }
        }
        return runs;
    }

    public static boolean has(CharSequence text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == OPEN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Превращает разметку в тексте поля ввода в метки перед отправкой.
     *
     * Идём с конца: вставка сдвигает всё, что правее, а то, что левее, стоит на
     * месте. Так отсчёты не нужно пересчитывать ни разу.
     */
    public static CharSequence encode(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return text;
        }
        final Spanned spanned = (Spanned) text;
        final MargeletSpans.Base[] spans = spanned.getSpans(0, spanned.length(), MargeletSpans.Base.class);
        if (spans.length == 0) {
            return text;
        }
        final SpannableStringBuilder out = new SpannableStringBuilder(text);
        // Сначала все границы, потом вставка — иначе порядок вставок зависит
        // от того, в каком порядке система вернула разметку.
        final ArrayList<int[]> marks = new ArrayList<>();   // позиция, вид, значение, открыть?
        for (MargeletSpans.Base span : spans) {
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            if (start < 0 || end <= start) {
                continue;
            }
            marks.add(new int[]{start, span.kind(), span.value(), 1});
            marks.add(new int[]{end, 0, 0, 0});
        }
        // По убыванию позиции, а на равных позициях — сначала открывающие.
        //
        // Порядок тут не «как удобнее», а единственный правильный, и я сначала
        // взял обратный. Вставка в одну и ту же точку переворачивает порядок:
        // вставишь закрывающий, потом открывающий — в тексте они окажутся
        // наоборот, и два куска встык («жирный конец» одного и начало другого)
        // склеятся в один. Поймано отдельной моделью формата на питоне
        // (tools/markup_model.py) до первой сборки.
        Collections.sort(marks, (a, b) ->
                a[0] != b[0] ? Integer.compare(b[0], a[0]) : Integer.compare(b[3], a[3]));
        for (int[] mark : marks) {
            out.insert(mark[0], mark[3] == 1 ? open(mark[1], mark[2]) : close());
        }
        return HEADER + "\n" + out;
    }

    /** Вешает оформление по меткам. Текст не меняется, меняется только вид. */
    public static void apply(Spannable text) {
        if (text == null) {
            return;
        }
        for (Run run : parse(text)) {
            final Object span = MargeletSpans.create(run.kind, run.value);
            if (span != null) {
                text.setSpan(span, run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        hideHeader(text);
    }

    /**
     * Прячет заголовок с рекламой форка внутри самого форка.
     *
     * Именно прячет, а не вырезает: вырезание сдвинуло бы отсчёты жирного,
     * курсива и ссылок, которые пришли с сервера и посчитаны по тексту
     * вместе с заголовком.
     */
    private static void hideHeader(Spannable text) {
        final int at = indexOf(text, HEADER);
        if (at < 0) {
            return;
        }
        int end = at + HEADER.length();
        if (end < text.length() && text.charAt(end) == '\n') {
            end++;
        }
        text.setSpan(new MargeletSpans.Hidden(), at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static int indexOf(CharSequence text, String what) {
        final int limit = text.length() - what.length();
        outer:
        for (int i = 0; i <= limit; i++) {
            for (int j = 0; j < what.length(); j++) {
                if (text.charAt(i + j) != what.charAt(j)) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
