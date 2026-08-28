package de.robv.android.xposed;

import org.telegram.margelet.MargeletHookEngine;
import org.telegram.margelet.MargeletPluginHost;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * Мост к движку хуков под именем, знакомым авторам плагинов.
 *
 * Всё, что здесь есть, — перевод с языка Xposed на язык нашего движка.
 * Никакой собственной магии: подмену делает движок, мы только раскладываем
 * доводы по местам и следим, чтобы ошибка плагина не превратилась в ошибку
 * приложения.
 */
public final class XposedBridge {

    private XposedBridge() {
    }

    /** Что уже подменено. Нужно, чтобы уметь снять всё разом. */
    private static final List<Object> installed = new ArrayList<>();

    public static void log(String text) {
        MargeletPluginHost.log("хуки", text, false);
    }

    public static void log(Throwable t) {
        MargeletPluginHost.log("хуки", String.valueOf(t), true);
    }

    /**
     * Подменить метод.
     *
     * Возвращает null, если движок не поднят: на неподдержанном андроиде или
     * при выключенных хуках плагин должен получить честный отказ, а не тихо
     * работать вхолостую, думая, что подменил.
     */
    public static Object hookMethod(Member method, XC_MethodHook callback) {
        if (method == null || callback == null) {
            return null;
        }
        if (!MargeletHookEngine.working()) {
            log("хуки не работают: " + (MargeletHookEngine.enabled()
                    ? String.valueOf(MargeletHookEngine.failure())
                    : "выключены в настройках"));
            return null;
        }
        try {
            final Object token = Pine.hook(method, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    final XC_MethodHook.MethodHookParam param = paramOf(frame, method);
                    callback.callBefore(param);
                    frame.args = param.args;
                    if (param.isReturnEarly()) {
                        // Решили за метод — сам метод не вызываем.
                        if (param.hasThrowable()) {
                            frame.setThrowable(param.getThrowable());
                        } else {
                            frame.setResult(param.getResult());
                        }
                    }
                }

                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    final XC_MethodHook.MethodHookParam param = paramOf(frame, method);
                    param.setResultQuietly(frame.getResult());
                    callback.callAfter(param);
                    frame.setResult(param.getResult());
                }
            });
            synchronized (installed) {
                installed.add(token);
            }
            return token;
        } catch (Throwable t) {
            log(t);
            return null;
        }
    }

    private static XC_MethodHook.MethodHookParam paramOf(Pine.CallFrame frame, Member method) {
        final XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.method = method;
        param.thisObject = frame.thisObject;
        param.args = frame.args;
        return param;
    }
}
