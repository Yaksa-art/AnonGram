package org.telegram.margelet;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/**
 * Движок хуков: подмена поведения методов прямо в работающем приложении.
 *
 * Это то, от чего я отговаривал, и отговаривал по делу: подмена произвольного
 * метода — это правка чужого кода на ходу, она держится на внутренностях
 * исполнителя java, а те меняются от версии андроида к версии. Владелец
 * настоял, услышав возражение, и это его право: форк его.
 *
 * Раз делаем — делаем так, чтобы худший случай не был непоправимым. Худший
 * случай здесь один: кривой хук роняет приложение при запуске, а чтобы его
 * выключить, надо открыть настройки в приложении, которое не открывается.
 * Человек остаётся с кирпичом и без объяснения.
 *
 * Поэтому здесь три вещи, и все три — про этот случай:
 *
 * Первая. По умолчанию выключено. Согласие даётся один раз и осознанно.
 *
 * Вторая. Отметка «мы начали поднимать хуки» ставится ДО их подъёма и
 * снимается после того, как приложение дожило до готовности. Увидев отметку
 * при следующем запуске, движок понимает, что прошлый раз кончился падением,
 * и не поднимается вовсе.
 *
 * Третья. Не вышло — говорим в консоль плагинов и живём дальше. На андроиде,
 * который движок не понимает, приложение обязано работать как обычно, просто
 * без хуков.
 */
public class MargeletHookEngine {

    private static final String PREFS = "margelet_hooks";
    private static final String KEY_ON = "enabled";
    private static final String KEY_TRYING = "trying";
    private static final String KEY_BROKE = "broke";

    private static boolean started;
    private static boolean working;
    private static String failure;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Разрешил ли человек хуки. По умолчанию нет. */
    public static boolean enabled() {
        return prefs().getBoolean(KEY_ON, false);
    }

    public static void setEnabled(boolean on) {
        prefs().edit().putBoolean(KEY_ON, on).putBoolean(KEY_BROKE, false).apply();
    }

    /** Сорвался ли прошлый запуск с включёнными хуками. */
    public static boolean brokeLastTime() {
        return prefs().getBoolean(KEY_BROKE, false);
    }

    /** Поднялся ли движок в этот раз. */
    public static boolean working() {
        return working;
    }

    /** Почему не поднялся, если не поднялся. */
    public static String failure() {
        return failure;
    }

    /**
     * Поднимает движок, если человек разрешил и прошлый раз не кончился
     * падением. Зовётся один раз, при запуске приложения.
     */
    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        if (!enabled()) {
            return;
        }
        if (prefs().getBoolean(KEY_TRYING, false)) {
            // Прошлый запуск поставил отметку и не снял её — значит не дожил.
            // Второй раз в ту же яму не идём: выключаем и объясняем.
            prefs().edit().putBoolean(KEY_ON, false)
                    .putBoolean(KEY_TRYING, false)
                    .putBoolean(KEY_BROKE, true).apply();
            failure = "прошлый запуск с хуками не дожил до конца, хуки выключены";
            MargeletPluginHost.log("margelet", failure, true);
            return;
        }
        prefs().edit().putBoolean(KEY_TRYING, true).apply();
        try {
            top.canyie.pine.PineConfig.debug = false;
            top.canyie.pine.PineConfig.debuggable = false;
            // Трогаем движок по-настоящему, а не просто выставляем настройки:
            // не поддержанная версия андроида скажется именно здесь.
            top.canyie.pine.Pine.ensureInitialized();
            working = true;
        } catch (Throwable t) {
            FileLog.e(t);
            failure = String.valueOf(t);
            MargeletPluginHost.log("margelet", "движок хуков не поднялся: " + failure, true);
        }
    }

    /**
     * Приложение дожило до рабочего состояния — снимаем отметку.
     *
     * Зовётся не сразу после подъёма, а позже, когда экран уже нарисован:
     * упасть кривой хук может и не в первую секунду.
     */
    public static void survived() {
        try {
            if (prefs().getBoolean(KEY_TRYING, false)) {
                prefs().edit().putBoolean(KEY_TRYING, false).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Подменить метод обработчиком из питона.
     *
     * Всю возню с Xposed-обёрткой делаем здесь, на стороне java: питону
     * достаётся простой интерфейс с двумя методами, который его мост умеет
     * подставлять. Раньше питон пытался наследовать абстрактный класс сам — и
     * не мог, о чём честно сообщал ошибкой, которую никто не читал.
     *
     * @param where  имя класса
     * @param method имя метода
     * @param args   типы доводов, если метод перегружен; может быть null
     * @param call   что звать до и после
     * @return удалось ли подменить
     */
    public static boolean hook(String where, String method, Object[] args,
                               final MargeletHookCall call) {
        if (call == null || where == null || method == null) {
            return false;
        }
        if (!working()) {
            MargeletPluginHost.log("хуки", "не работают: " + (enabled()
                    ? String.valueOf(failure()) : "выключены в настройках"), true);
            return false;
        }
        final de.robv.android.xposed.XC_MethodHook wrap = new de.robv.android.xposed.XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                call.before(param);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                call.after(param);
            }
        };
        final Object[] tail = new Object[(args == null ? 0 : args.length) + 1];
        if (args != null) {
            System.arraycopy(args, 0, tail, 0, args.length);
        }
        tail[tail.length - 1] = wrap;
        try {
            return de.robv.android.xposed.XposedHelpers.findAndHookMethod(where, null, method, tail) != null;
        } catch (Throwable t) {
            MargeletPluginHost.log("хуки", String.valueOf(t), true);
            return false;
        }
    }
}
