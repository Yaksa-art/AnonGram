package org.telegram.margelet;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/**
 * Единственное место, где форк трогает питон напрямую.
 *
 * Лежит в модуле приложения, а не в общей библиотеке, потому что движок
 * питона подключается только к этой сборке: остальным вариантам приложения
 * он не нужен, и тащить одиннадцать мегабайт во все — глупость. Библиотека
 * зовёт этот класс по имени, через отражение: так она собирается и там, где
 * питона нет вовсе.
 */
public class MargeletPython {

    public static void start(Context context) {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
    }

    /** Открылся чат. Плагины, которые на это подписаны, узнают об этом. */
    public static void chatOpened(Object fragment) {
        final PyObject host = Python.getInstance().getModule("margelet_host");
        host.callAttr("chat_opened", fragment);
    }

    public static void run(String id, String name, String folder) {
        final PyObject host = Python.getInstance().getModule("margelet_host");
        host.callAttr("run_plugin", id, name, folder);
    }
}
