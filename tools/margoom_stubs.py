# -*- coding: utf-8 -*-
"""Прогнать ВЕСЬ плагин, включая половину, разговаривающую с андроидом.

До сих пор я проверял только чистую половину, а три поломки подряд случились
именно во второй: то не тот поток, то дробный отсчёт, то чёрный экран. Здесь
андроид подменяется заглушками — ровно настолько, чтобы код выполнился и
показал своё исключение мне, а не владельцу.

Заглушки нарочно тупые: они ничего не проверяют и не изображают. Их дело —
дать коду доработать до конца, чтобы стало видно, где он падает.
"""
import io, sys, types, math

class Заглушка:
    """Что угодно: зовётся, читается, возвращает себя."""
    def __init__(self, имя="?"):
        self._имя = имя
    def __call__(self, *args, **kw):
        return Заглушка(self._имя + "()")
    def __getattr__(self, имя):
        return Заглушка(self._имя + "." + имя)
    def __int__(self):
        return 0

class Вид(Заглушка):
    def __init__(self):
        Заглушка.__init__(self, "вид")
        self.картинка = None
    def getWidth(self):
        return 2400
    def getHeight(self):
        return 1080
    def setImageBitmap(self, b):
        self.картинка = b
    def getDrawable(self):
        return Заглушка("рисовалка")

class Картинка(Заглушка):
    def __init__(self, ш, в):
        Заглушка.__init__(self, "картинка")
        self.ш, self.в = ш, в
        self.точки = None
    def setPixels(self, точки, off, stride, x, y, w, h):
        assert len(точки) == self.ш * self.в, (len(точки), self.ш * self.в)
        self.точки = точки

сделанные = {}

def jclass(имя):
    if имя == "android.graphics.Bitmap":
        к = Заглушка("Bitmap")
        к.createBitmap = lambda ш, в, cfg: Картинка(ш, в)
        return к
    if имя == "android.widget.ImageView":
        return lambda ctx: Вид()
    if имя == "org.telegram.messenger.AndroidUtilities":
        к = Заглушка("AndroidUtilities")
        к.dp = lambda n: int(n * 3)
        к.runOnUIThread = lambda r, d=0: сделанные.setdefault("отложено", []).append(r)
        return к
    return Заглушка(имя)

модуль = types.ModuleType("java")
модуль.jclass = jclass
модуль.dynamic_proxy = lambda интерфейс: object
модуль.jarray = lambda тип: (lambda последовательность: list(последовательность))
модуль.jint = "int"
модуль.cast = lambda тип, o: o
sys.modules["java"] = модуль

class Марджелет:
    name = "MargOOM"
    жалобы = []
    def color(self, argb):
        return argb - (1 << 32) if argb >= (1 << 31) else argb
    def settings(self, *r): return r
    def header(self, t): return {"kind": "header", "title": t}
    def note(self, t): return {"kind": "note", "title": t}
    def switch(self, k, t, **kw): return {"kind": "switch", "key": k}
    def text(self, k, t, **kw): return {"kind": "text", "key": k}
    def button(self, t, c, key=None): pass
    def on_send(self, c): pass
    def activity(self): return Заглушка("активность")
    def toast(self, t): pass
    def log(self, *ч): pass
    def error(self, *ч):
        Марджелет.жалобы.append(" ".join(str(c) for c in ч))
    def get(self, k, f=None): return f
    def flag(self, k, f=False): return f

источник = io.open("main.py", encoding="utf-8").read()
G = {"margelet": Марджелет()}
exec(compile(источник, "main.py", "exec"), G)

плохо = 0
def ok(что, условие):
    global плохо
    if not условие:
        print("НЕ СОШЛОСЬ", что)
        плохо += 1

Игра = G["Игра"]
игра = Игра(Заглушка("активность"))
игра.view = Вид()

# Кадр за кадром: если что-то падает, оно упадёт здесь, а не у владельца.
for номер in range(12):
    игра.шаг()
    ok("кадр %d нарисовался" % номер, игра.bitmap is not None and игра.bitmap.точки is not None)
    if Марджелет.жалобы:
        print("ЖАЛОБА на кадре", номер, ":", Марджелет.жалобы[0])
        плохо += 1
        break
    # Немного походим и постреляем, чтобы задеть больше кода.
    игра.forward = 0.05
    игра.angle += 0.3
    игра.выстрел()
    if номер == 5:
        игра.перезарядить()

# Экран смерти и перезапуск.
Марджелет.жалобы = []
игра.health = 0
игра.шаг()
ok("экран смерти нарисовался", not Марджелет.жалобы)
if Марджелет.жалобы:
    print("ЖАЛОБА на смерти:", Марджелет.жалобы[0])
игра.начать()
ok("после перезапуска здоровье целое", игра.health == 100)
ok("после перезапуска враги живы", all(в["alive"] for в in игра.enemies))
Марджелет.жалобы = []
игра.шаг()
ok("кадр после перезапуска", not Марджелет.жалобы)
if Марджелет.жалобы:
    print("ЖАЛОБА после перезапуска:", Марджелет.жалобы[0])

print("плохих:", плохо)
sys.exit(1 if плохо else 0)
