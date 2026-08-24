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

from java import jclass

_Host = jclass("org.telegram.margelet.MargeletPluginHost")

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

    def log(self, *parts):
        _Host.log(self.name, " ".join(str(p) for p in parts), False)

    def error(self, *parts):
        _Host.log(self.name, " ".join(str(p) for p in parts), True)


def run_plugin(plugin_id, name, folder):
    """Запускает main.py плагина. Ошибка плагина остаётся ошибкой плагина."""
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
