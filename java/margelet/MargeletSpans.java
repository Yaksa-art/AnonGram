package org.telegram.margelet;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

/**
 * Как выглядит своё оформление. Каждый вид — своя разметка поверх текста.
 *
 * Все они наследуют {@link Base}: по нему поле ввода узнаёт свои куски, когда
 * пора превращать их в метки перед отправкой.
 */
public class MargeletSpans {

    /**
     * Общий предок: знает свой вид и своё значение.
     *
     * Наследует MetricAffectingSpan, а не просто CharacterStyle. Разница важная
     * и я её сначала не увидел: CharacterStyle меняет только отрисовку, а
     * ширину и высоту строки считают отдельно и до неё. Из-за этого крупный
     * текст рисовался поверх пузыря сообщения, выходя за его края, — пузырь
     * мерился по обычному размеру. Владелец это увидел сразу.
     */
    public abstract static class Base extends MetricAffectingSpan {

        /**
         * Оформление пришло из меток в самом тексте, а не поставлено руками.
         *
         * Разница важна при отправке: метки такого куска уже лежат в тексте,
         * и добавлять их второй раз нельзя. Так бывает при правке своего же
         * сообщения — в поле приезжает разобранный текст со спанами.
         */
        public boolean decoded;

        public abstract int kind();

        public abstract int value();

        /** Нагрузка метки: ссылка у кнопки. У остальных ничего. */
        public byte[] payload() {
            return null;
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint paint) {
            // По умолчанию оформление на размеры не влияет.
        }
    }

    /** Цвета кнопок. Значение метки — номер в этом списке. */
    public static final int[] BUTTON_COLORS = {
            0xFF4E9CF5, 0xFF34C759, 0xFFF0932B, 0xFFE74C3C, 0xFF9B59B6,
            0xFF16A085, 0xFFE84393, 0xFF7F8C8D, 0xFF2C3E50, 0xFFD4AC0D,
            0xFF00B8D9, 0xFF6C5CE7, 0xFF8DD1B0, 0xFFB7A8E0
    };

    public static int buttonColor(int value) {
        return BUTTON_COLORS[Math.max(0, Math.min(BUTTON_COLORS.length - 1, value))];
    }

    public static Object create(int kind, int value) {
        switch (kind) {
            case MargeletMarkup.KIND_SIZE:
                return new Size(value);
            case MargeletMarkup.KIND_DIM:
                return new Dim(value);
            case MargeletMarkup.KIND_RAINBOW:
                return new Rainbow(value);
            default:
                return null;
        }
    }

    /** Размер. Границы шкалы держит {@link MargeletMarkup#sizeOf(int)}. */
    public static class Size extends Base {
        private final int value;

        public Size(int value) {
            this.value = value;
        }

        @Override
        public int kind() {
            return MargeletMarkup.KIND_SIZE;
        }

        @Override
        public int value() {
            return value;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            resize(paint);
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint paint) {
            // То же самое и при измерении: иначе пузырь останется прежним, а
            // буквы вылезут за него.
            resize(paint);
        }

        private void resize(TextPaint paint) {
            paint.setTextSize(paint.getTextSize() * MargeletMarkup.sizeOf(value));
        }
    }

    /**
     * Затемнение: тот же цвет, только приглушённый.
     *
     * Смешиваем с прозрачностью, а не с серым: на тёмной теме текст серее
     * выглядит темнее, на светлой — светлее, и в обоих случаях это «тише», а
     * не «другого цвета».
     */
    public static class Dim extends Base {
        private final int value;

        public Dim(int value) {
            this.value = value;
        }

        @Override
        public int kind() {
            return MargeletMarkup.KIND_DIM;
        }

        @Override
        public int value() {
            return value;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            final float keep = 0.75f - 0.04f * Math.max(0, Math.min(13, value));
            paint.setAlpha(Math.round(paint.getAlpha() * Math.max(0.2f, keep)));
        }
    }

    /**
     * Радуга: цвет едет по кругу.
     *
     * Медленнее «Приступа» вдвое с лишним и без вспышек: яркость постоянная,
     * меняется только оттенок. Перерисовку делает тот, кто эту разметку
     * показывает; сама по себе она не двигается.
     */
    public static class Rainbow extends Base {
        private static final long CYCLE_MS = 4200L;
        private final int value;
        private static final float[] hsv = {0f, 0.85f, 0.95f};

        public Rainbow(int value) {
            this.value = value;
        }

        @Override
        public int kind() {
            return MargeletMarkup.KIND_RAINBOW;
        }

        @Override
        public int value() {
            return value;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            // Сама разметка себя не перерисовывает. Отмечаемся, что нас
            // только что нарисовали: пока это происходит, обход дерева
            // держит кадры и цвет едет. Перестали рисовать — обход гаснет
            // сам через секунду.
            MargeletSeizure.poke();
            hsv[0] = (System.currentTimeMillis() % CYCLE_MS) * 360f / CYCLE_MS;
            paint.setColor(Color.HSVToColor(hsv));
        }
    }

    /**
     * Пометка кнопки: невидимая, нужна только чтобы отправка знала цвет и
     * ссылку. Рисует кнопку отдельная разметка {@link Button}: одна и та же
     * разметка не может и занимать место, и оставаться в списке наших меток.
     */
    public static class ButtonMark extends Base {
        private final int value;
        private final String url;

        public ButtonMark(int value, String url) {
            this.value = value;
            this.url = url;
        }

        @Override
        public int kind() {
            return MargeletMarkup.KIND_BUTTON;
        }

        @Override
        public int value() {
            return value;
        }

        @Override
        public byte[] payload() {
            return MargeletMarkup.bytesOf(url);
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            // Ничего: рисует Button.
        }
    }

    /**
     * Кнопка: подпись рисуется на цветной плашке, как у ботов.
     *
     * Рисование и нажатие разведены нарочно. Эта разметка занимает место и
     * рисует плашку, а за нажатие отвечает обычная ссылочная разметка, которую
     * телеграм уже умеет ловить, — свою обработку нажатий заводить незачем.
     */
    public static class Button extends ReplacementSpan {
        private final int color;
        private final String url;
        private final android.graphics.RectF rect = new android.graphics.RectF();

        public Button(int value, String url) {
            this.color = buttonColor(value);
            this.url = url;
        }

        public String url() {
            return url;
        }

        private static int padding() {
            return org.telegram.messenger.AndroidUtilities.dp(10);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                           Paint.FontMetricsInt fm) {
            final int width = (int) paint.measureText(text, start, end) + padding() * 2;
            if (fm != null) {
                final Paint.FontMetricsInt source = paint.getFontMetricsInt();
                final int extra = org.telegram.messenger.AndroidUtilities.dp(3);
                fm.ascent = fm.top = source.ascent - extra;
                fm.descent = fm.bottom = source.descent + extra;
            }
            return width;
        }

        /**
         * Попадает ли касание в нарисованную плашку.
         *
         * Отсчёты знаков тут не при чём: плашку рисуем мы сами и знаем, где
         * именно она лежит. Через отсчёты кнопка ловилась через раз — в
         * сообщении, где кроме неё ничего нет, не ловилась вовсе, — а
         * прямоугольник врать не умеет.
         */
        public boolean hit(float x, float y) {
            final float slack = org.telegram.messenger.AndroidUtilities.dp(4);
            return !rect.isEmpty()
                    && x >= rect.left - slack && x <= rect.right + slack
                    && y >= rect.top - slack && y <= rect.bottom + slack;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, @NonNull Paint paint) {
            final int extra = org.telegram.messenger.AndroidUtilities.dp(3);
            final float width = paint.measureText(text, start, end) + padding() * 2;
            rect.set(x, paint.getFontMetricsInt().ascent + y - extra,
                    x + width, paint.getFontMetricsInt().descent + y + extra);
            final int wasColor = paint.getColor();
            paint.setColor(color);
            canvas.drawRoundRect(rect, org.telegram.messenger.AndroidUtilities.dp(8),
                    org.telegram.messenger.AndroidUtilities.dp(8), paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawText(text, start, end, x + padding(), y, paint);
            paint.setColor(wasColor);
        }
    }

    /**
     * Спрятанный кусок: заголовок с рекламой форка внутри самого форка.
     *
     * Занимает ноль по ширине и ничего не рисует. Именно так, а не вырезанием:
     * вырезание сдвинуло бы отсчёты жирного и ссылок, пришедшие с сервера.
     */
    public static class Hidden extends ReplacementSpan {
        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                           Paint.FontMetricsInt fm) {
            if (fm != null) {
                // Строку не съедаем: высоту оставляем как есть, иначе абзац
                // схлопнется вместе со следующей строкой.
                fm.ascent = fm.top = 0;
                fm.descent = fm.bottom = 0;
            }
            return 0;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, @NonNull Paint paint) {
            // Ничего.
        }
    }

    /** Кнопка форка под этой точкой или null. Точка — в отсчётах разметки. */
    public static Button buttonAt(CharSequence text, float x, float y) {
        if (!(text instanceof android.text.Spanned)) {
            return null;
        }
        final Button[] buttons = ((android.text.Spanned) text)
                .getSpans(0, text.length(), Button.class);
        for (Button button : buttons) {
            if (button.hit(x, y)) {
                return button;
            }
        }
        return null;
    }

    /** Есть ли в сообщении хоть одна кнопка форка. */
    public static boolean hasButton(CharSequence text) {
        return text instanceof android.text.Spanned
                && ((android.text.Spanned) text).getSpans(0, text.length(), Button.class).length > 0;
    }
}
