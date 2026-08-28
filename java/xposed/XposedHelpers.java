package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * То, чем авторы плагинов под Xposed пользуются на самом деле.
 *
 * Сам по себе {@link XposedBridge#hookMethod} требует уже найденного метода, а
 * ищут его почти всегда одинаково: по имени класса, имени метода и списку
 * типов доводов. Отсюда и эти помощники — они ровно про поиск, подмену делает
 * мост.
 *
 * Имена и порядок доводов повторяют настоящий Xposed. Отличаться было бы
 * бессмысленно: смысл всей затеи в том, чтобы чужой плагин работал без правок.
 */
public final class XposedHelpers {

    private XposedHelpers() {
    }

    public static Class<?> findClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false,
                    loader != null ? loader : XposedHelpers.class.getClassLoader());
        } catch (Throwable t) {
            throw new RuntimeException("класс не найден: " + name, t);
        }
    }

    /**
     * Найти и подменить метод.
     *
     * Последним доводом идёт сам перехват, перед ним — типы доводов метода.
     * Типы можно писать и классами, и именами: у Xposed так, и плагины
     * написаны в расчёте на это.
     */
    public static Object findAndHookMethod(Class<?> clazz, String methodName, Object... args) {
        if (clazz == null || args == null || args.length == 0) {
            return null;
        }
        final Object last = args[args.length - 1];
        if (!(last instanceof XC_MethodHook)) {
            XposedBridge.log("последним доводом должен быть перехват");
            return null;
        }
        final Class<?>[] types = typesOf(clazz.getClassLoader(), args, args.length - 1);
        if (types == null) {
            return null;
        }
        final Member method = find(clazz, methodName, types);
        if (method == null) {
            XposedBridge.log("метод не найден: " + clazz.getName() + "." + methodName);
            return null;
        }
        return XposedBridge.hookMethod(method, (XC_MethodHook) last);
    }

    public static Object findAndHookMethod(String className, ClassLoader loader,
                                           String methodName, Object... args) {
        return findAndHookMethod(findClass(className, loader), methodName, args);
    }

    /** Подменить создание объекта. */
    public static Object findAndHookConstructor(Class<?> clazz, Object... args) {
        if (clazz == null || args == null || args.length == 0) {
            return null;
        }
        final Object last = args[args.length - 1];
        if (!(last instanceof XC_MethodHook)) {
            return null;
        }
        final Class<?>[] types = typesOf(clazz.getClassLoader(), args, args.length - 1);
        if (types == null) {
            return null;
        }
        try {
            final Constructor<?> constructor = clazz.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return XposedBridge.hookMethod(constructor, (XC_MethodHook) last);
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }

    /** Типы доводов: и классами, и именами. */
    private static Class<?>[] typesOf(ClassLoader loader, Object[] args, int count) {
        final Class<?>[] types = new Class<?>[count];
        for (int i = 0; i < count; i++) {
            final Object item = args[i];
            if (item instanceof Class) {
                types[i] = (Class<?>) item;
            } else if (item instanceof String) {
                try {
                    types[i] = primitive((String) item);
                    if (types[i] == null) {
                        types[i] = findClass((String) item, loader);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t);
                    return null;
                }
            } else {
                XposedBridge.log("непонятный тип довода: " + item);
                return null;
            }
        }
        return types;
    }

    private static Class<?> primitive(String name) {
        switch (name) {
            case "int": return int.class;
            case "long": return long.class;
            case "boolean": return boolean.class;
            case "float": return float.class;
            case "double": return double.class;
            case "byte": return byte.class;
            case "short": return short.class;
            case "char": return char.class;
            case "void": return void.class;
            default: return null;
        }
    }

    /**
     * Ищет метод у класса и его предков.
     *
     * Именно у предков тоже: метод может быть объявлен выше по цепочке, а
     * плагин знает только тот класс, с которым работает.
     */
    private static Member find(Class<?> clazz, String name, Class<?>[] types) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                final Method method = current.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    // --- чтение и запись полей: без этого хуки почти бесполезны ---

    private static Field field(Object object, String name) throws NoSuchFieldException {
        Class<?> current = object instanceof Class ? (Class<?>) object : object.getClass();
        while (current != null) {
            try {
                final Field found = current.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    public static Object getObjectField(Object object, String name) {
        try {
            return field(object, name).get(object);
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }

    public static void setObjectField(Object object, String name, Object value) {
        try {
            field(object, name).set(object, value);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public static int getIntField(Object object, String name) {
        final Object value = getObjectField(object, name);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static boolean getBooleanField(Object object, String name) {
        final Object value = getObjectField(object, name);
        return value instanceof Boolean && (Boolean) value;
    }

    /** Позвать метод, даже закрытый. */
    public static Object callMethod(Object object, String name, Object... args) {
        if (object == null) {
            return null;
        }
        final Class<?>[] types = new Class<?>[args == null ? 0 : args.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = args[i] == null ? Object.class : args[i].getClass();
        }
        Class<?> current = object.getClass();
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)
                        || method.getParameterTypes().length != types.length) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(object, args);
                } catch (Throwable t) {
                    // Перегрузок может быть несколько: не подошла эта — пробуем
                    // следующую, а не сдаёмся на первой.
                }
            }
            current = current.getSuperclass();
        }
        XposedBridge.log("метод не позвался: " + name);
        return null;
    }
}
