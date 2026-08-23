package org.telegram.margelet;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;
import android.text.style.UpdateAppearance;

import androidx.annotation.NonNull;

/**
 * Как выглядит своё оформление. Каждый вид — своя разметка поверх текста.
 *
 * Все они наследуют {@link Base}: по нему поле ввода узнаёт свои куски, когда
 * пора превращать их в метки перед отправкой.
 */
public class MargeletSpans {

    /** Общий предок: знает свой вид и своё значение. */
    public abstract static class Base extends CharacterStyle implements UpdateAppearance {
        public abstract int kind();

        public abstract int value();
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
}
