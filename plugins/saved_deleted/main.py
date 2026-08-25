# -*- coding: utf-8 -*-
"""
Сохранить удалённые сообщения.

on_message — кешируем все сообщения, приходящие в этом сеансе.
messagesDeleted — когда сообщение удаляют, достаём текст из кеша и шлём обратно в чат.
"""

from java import dynamic_proxy, jclass
from java.lang import Runnable

NotificationCenter = jclass("org.telegram.messenger.NotificationCenter")
UserConfig = jclass("org.telegram.messenger.UserConfig")
AndroidUtilities = jclass("org.telegram.messenger.AndroidUtilities")

# message_id -> (dialog_id, text, out)
_cache = {}
_MAX_CACHE = 3000


def _add(msg_id, dialog_id, text, out):
    if len(_cache) >= _MAX_CACHE:
        oldest = next(iter(_cache))
        del _cache[oldest]
    _cache[msg_id] = (dialog_id, text or "", out)


def _cache_message(text, dialog_id, message_id, out):
    _add(message_id, dialog_id, text, out)


def _on_deleted(ids, dialog_id):
    prefix = margelet.get("prefix", "🗑")
    show_mine = margelet.flag("show_mine", True)
    show_theirs = margelet.flag("show_theirs", True)

    for mid in ids:
        if mid not in _cache:
            continue
        c_dialog, text, out = _cache.pop(mid)
        chat = dialog_id if dialog_id != 0 else c_dialog

        if not text.strip() or chat == 0:
            continue
        if out and not show_mine:
            continue
        if not out and not show_theirs:
            continue

        label = "я" if out else "собеседник"
        note = f"{prefix} [{label}]: {text}"
        margelet.background(lambda note=note, chat=chat: margelet.send(chat, note))


class _DeleteDelegate(dynamic_proxy(
        jclass("org.telegram.messenger.NotificationCenter$NotificationCenterDelegate"))):

    def didReceivedNotification(self, event_id, account, args):
        try:
            nc = NotificationCenter.getInstance(account)
            if event_id != nc.messagesDeleted:
                return
            if args is None or len(args) < 2:
                return
            ids_java = args[0]
            dialog_id = int(args[1])
            ids = [int(ids_java.get(i)) for i in range(ids_java.size())]
            _on_deleted(ids, dialog_id)
        except Exception:
            import traceback
            margelet.error(traceback.format_exc())


class _RunOnUI(dynamic_proxy(Runnable)):
    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def run(self):
        try:
            self._fn()
        except Exception:
            import traceback
            margelet.error(traceback.format_exc())


_delegate = _DeleteDelegate()


def on_start():
    margelet.settings(
        margelet.header("Сохранение удалённых"),
        margelet.text("prefix", "Префикс", default="🗑",
                      about="Символ перед восстановленным сообщением."),
        margelet.switch("show_mine", "Мои удалённые", default=True),
        margelet.switch("show_theirs", "Чужие удалённые", default=True),
        margelet.note(
            "Сохраняет сообщения, пришедшие в этом сеансе. "
            "Старая история до запуска плагина не восстанавливается."
        ),
    )

    margelet.on_message(_cache_message)

    def _subscribe():
        for account in range(UserConfig.MAX_ACCOUNT_COUNT):
            try:
                nc = NotificationCenter.getInstance(account)
                nc.addObserver(_delegate, nc.messagesDeleted)
            except Exception:
                pass

    AndroidUtilities.runOnUIThread(_RunOnUI(_subscribe))
    margelet.log("запущен")
