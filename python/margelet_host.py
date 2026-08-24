# -*- coding: utf-8 -*-
"""Прослойка между приложением и плагинами Margelet.

Каждый плагин исполняется здесь: печать перехватывается и уходит в консоль
приложения, ошибки не роняют ни другие плагины, ни само приложение.

Плагину доступен модуль margelet — через него он и живёт: пишет в консоль,
хранит свои настройки, вешается на события. Всё, что он делает, видно в коде
самого плагина: код едет исходником и не пакуется, это условие форума.
"""

import importlib.util
import io
import sys
import traceback

from java import dynamic_proxy, jclass
from java.lang import Runnable

_Host = jclass("org.telegram.margelet.MargeletPluginHost")
_Android = jclass("org.telegram.messenger.AndroidUtilities")


class _Task(dynamic_proxy(Runnable)):
    """Питоновская работа, которую можно отдать андроиду.

    Повторяющаяся сама ставит себя заново: так плагину не приходится знать
    про очереди и обёртки, ему достаточно margelet.every.
    """

    def __init__(self, call, repeat_ms=None, name="плагин"):
        super().__init__()
        self._call = call
        self._repeat = repeat_ms
        self._name = name
        self.cancelled = False

    def run(self):
        if self.cancelled:
            return
        try:
            self._call()
        except Exception:
            _Host.log(self._name, traceback.format_exc(), True)
            return          # сломанное не повторяем бесконечно
        if self._repeat and not self.cancelled:
            _Android.runOnUIThread(self, self._repeat)

# Загруженные плагины: номер -> модуль. Нужен, чтобы второй запуск не плодил
# копии одного и того же.
_loaded = {}


class _Console:
    """Печать плагина уходит в консоль приложения, а не в никуда."""

    def __init__(self, name, error=False):
        self._name = name
        self._error = error
        self._buffer = ""

    def write(self, text):
        self._buffer += text
        while "\n" in self._buffer:
            line, self._buffer = self._buffer.split("\n", 1)
            if line:
                _Host.log(self._name, line, self._error)

    def flush(self):
        if self._buffer:
            _Host.log(self._name, self._buffer, self._error)
            self._buffer = ""


class Margelet:
    """То, что плагин видит под именем margelet."""

    def __init__(self, plugin_id, name, folder):
        self.id = plugin_id
        self.name = name
        self.folder = folder
        self._on_chat_opened = []

    def log(self, *parts):
        _Host.log(self.name, " ".join(str(p) for p in parts), False)

    def error(self, *parts):
        _Host.log(self.name, " ".join(str(p) for p in parts), True)

    # --- что умеет плагин, кроме печати ---

    def ui(self, call, delay_ms=0):
        """Выполнить на главном потоке: всё, что трогает экран, только оттуда."""
        task = _Task(call, None, self.name)
        _Android.runOnUIThread(task, delay_ms)
        return task

    def every(self, ms, call):
        """Повторять каждые ms миллисекунд. Остановить — margelet.cancel(...)."""
        task = _Task(call, ms, self.name)
        _Android.runOnUIThread(task, ms)
        return task

    def cancel(self, task):
        """Прекратить повтор, поставленный every или ui."""
        if task is not None:
            task.cancelled = True

    def toast(self, text):
        """Короткая надпись поверх экрана."""
        _Host.toast(str(text))

    def get(self, key, fallback=None):
        """Своя память плагина. Переживает и перезапуск, и обновление плагина."""
        value = _Host.get(self.id, str(key), None)
        return fallback if value is None else value

    def set(self, key, value):
        _Host.set(self.id, str(key), None if value is None else str(value))

    def on_chat_opened(self, call):
        """Позвать, когда человек открыл переписку. Передаётся сам экран чата."""
        self._on_chat_opened.append(call)


# Кому раздавать события: номер плагина -> его объект margelet.
_margelets = {}


def chat_opened(fragment):
    """Человек открыл переписку. Разносим по подписавшимся плагинам.

    Ошибка одного плагина не должна отменить остальных: каждый зовётся
    отдельно, и упавший получает свой разбор в консоли.
    """
    for plugin_id, margelet in list(_margelets.items()):
        for call in list(margelet._on_chat_opened):
            try:
                call(fragment)
            except Exception:
                _Host.log(margelet.name, traceback.format_exc(), True)


def run_plugin(plugin_id, name, folder):
    """Запускает main.py плагина. Ошибка плагина остаётся ошибкой плагина.

    Второй раз один и тот же плагин не запускается. Проверка эта была
    задумана с самого начала — про неё даже написано у _loaded, — но написана
    не была: словарь заполнялся и не читался никогда. Заметно это стало,
    когда плагин поставили поверх уже стоящего: старый продолжал работать,
    новый запускался рядом, и всё, что плагин делает, начинало делаться
    дважды. Остановить уже работающий питон нечем, поэтому единственный
    честный ответ — не запускать второй раз.
    """
    if plugin_id in _loaded:
        _Host.log(name, "уже запущен, второй раз не поднимаю", False)
        return
    out, err = sys.stdout, sys.stderr
    sys.stdout = _Console(name, False)
    sys.stderr = _Console(name, True)
    try:
        if folder not in sys.path:
            sys.path.insert(0, folder)
        spec = importlib.util.spec_from_file_location(
            "margelet_plugin_" + plugin_id, folder + "/main.py")
        module = importlib.util.module_from_spec(spec)
        module.margelet = Margelet(plugin_id, name, folder)
        _margelets[plugin_id] = module.margelet
        spec.loader.exec_module(module)
        _loaded[plugin_id] = module
        if hasattr(module, "on_start"):
            module.on_start()
        _Host.log(name, "запущен", False)
    except Exception as error:
        # Первый кадр разбора — сам этот файл, автору плагина он ничего не
        # говорит. Показываем только то, что в его коде.
        frames = error.__traceback__
        if frames is not None and frames.tb_next is not None:
            frames = frames.tb_next
        _Host.log(name, "".join(
            traceback.format_exception(type(error), error, frames)), True)
    finally:
        sys.stdout.flush()
        sys.stderr.flush()
        sys.stdout, sys.stderr = out, err
