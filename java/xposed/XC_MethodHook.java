package de.robv.android.xposed;

import java.lang.reflect.Member;

/**
 * Перехват метода: что сделать до вызова и что после.
 *
 * Имена пакета и классов здесь чужие нарочно — так их знают авторы плагинов
 * под Xposed. Плагин, написанный для него, должен работать у нас без правок,
 * иначе поддержка Xposed остаётся словом, а не свойством.
 *
 * Внутри у нас свой движок, к настоящему Xposed отношения не имеющий: рут не
 * нужен, подмена живёт только внутри этого приложения и только пока оно
 * работает.
 */
public abstract class XC_MethodHook {

    /** Что известно о вызове и что с ним можно сделать. */
    public static class MethodHookParam {
        /** Какой метод вызвали. */
        public Member method;
        /** У какого объекта. Для статических — null. */
        public Object thisObject;
        /** Доводы вызова. Их можно менять до вызова. */
        public Object[] args;

        private Object result;
        private Throwable throwable;
        private boolean returnEarly;

        public Object getResult() {
            return result;
        }

        /**
         * Подменить ответ. Если сделать это ДО вызова, самого вызова не
         * будет вовсе: именно так плагин и отменяет чужое действие.
         */
        public void setResult(Object value) {
            result = value;
            throwable = null;
            returnEarly = true;
        }

        /**
         * Положить ответ, не помечая вызов решённым.
         *
         * Нужно после вызова: там ответ уже дал сам метод, и объявлять его
         * «решённым заранее» — вранье, из-за которого движок пропустил бы
         * настоящий вызов на следующем хуке.
         */
        public void setResultQuietly(Object value) {
            result = value;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable value) {
            throwable = value;
            result = null;
            returnEarly = true;
        }

        /** Решено ли уже за метод, не вызывая его. */
        public boolean isReturnEarly() {
            return returnEarly;
        }

        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) {
                throw throwable;
            }
            return result;
        }
    }

    /** До вызова. Здесь можно поменять доводы или отменить вызов целиком. */
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    /** После вызова. Здесь можно поменять ответ. */
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    /** Зовётся движком. Ошибка плагина не должна ломать сам метод. */
    public final void callBefore(MethodHookParam param) {
        try {
            beforeHookedMethod(param);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public final void callAfter(MethodHookParam param) {
        try {
            afterHookedMethod(param);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
