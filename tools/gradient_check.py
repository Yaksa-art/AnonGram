# -*- coding: utf-8 -*-
"""Проверяет правила разбора градиента — на тех самых выражениях из кода.

Выражения не переписаны сюда руками, а вычитаны из .java. Переписанное
расходится с настоящим молча: правишь код, забываешь проверку, и она
продолжает подтверждать вчерашнее.

    python3 tools/gradient_check.py [путь-к-исходникам]
"""
import io
import os
import re
import sys

КОРЕНЬ = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "java")


def выражение(файл, кусок):
    """Достать java-литерал регулярки и перевести в питоновскую строку.

    В java обратная косая в строке удвоена; питону нужна одинарная.
    """
    текст = io.open(файл, encoding="utf-8").read()
    начало = текст.index(кусок)
    куски = re.findall(r'"((?:[^"\\]|\\.)*)"', текст[начало:начало + 700])
    собрано = "".join(куски[:3])
    return собрано.replace('\\\\', '\\')


ГРАДИЕНТ = os.path.join(КОРЕНЬ, "margelet", "MargeletGradient.java")
ГРУППА = os.path.join(КОРЕНЬ, "margelet", "MargeletGroup.java")

ПАРА = re.compile(выражение(ГРАДИЕНТ, "Pattern PAIR"))
МЕТКИ = re.compile(выражение(ГРУППА, "Pattern TAGS"))

СЛУЧАИ = [
    # текст, ждём ли пару цветов, ждём ли что метка спрячется
    ("#margy_gradient 8DD1B0-B7A8E0", True, True),
    ("#margy_gradient 8dd1b0-b7a8e0", True, True),          # строчными тоже
    ("#margy_gradient 8DD1B0-B7A8E0\nпривет", True, True),
    ("привет\n#margy_gradient 8DD1B0-B7A8E0", True, True),   # не только в начале
    # Семь знаков — не цвет, парой это не станет. А метка всё равно прячется:
    # отличить «метка без цветов» от «метка и мусор» нечем, и правило для обеих
    # одно — спрятать саму метку. Сначала я ждал здесь обратного и ошибался
    # именно в ожидании, а не в коде.
    ("#margy_gradient 8DD1B0-B7A8E0extra", False, True),
    ("#margy_gradient 8DD1B0", False, True),                 # один цвет — не градиент
    ("#margy_gradient", False, True),                        # голая метка всё равно прячется
    ("#margy_gradients 8DD1B0-B7A8E0", False, False),        # чужая метка, похожая
    ("#margy_banner", False, True),
    ("#margy_wall_123", False, True),
    ("#margy_wall_c123", False, True),
    ("просто сообщение", False, False),
]

плохих = 0
for текст, ждём_пару, ждём_метку in СЛУЧАИ:
    вышла_пара = ПАРА.search(текст) is not None
    вышла_метка = МЕТКИ.search(текст) is not None
    беда = (вышла_пара != ждём_пару) or (вышла_метка != ждём_метку)
    плохих += беда
    print("%-40s пара %-5s метка %-5s%s"
          % (текст.replace("\n", "\\n"), вышла_пара, вышла_метка,
             "   ПЛОХО" if беда else ""))

# Метка прячется вместе с цветами, а не до них: иначе в группе осталась бы
# висеть голая строка «8DD1B0-B7A8E0», которая ничего не значит.
целиком = МЕТКИ.search("#margy_gradient 8DD1B0-B7A8E0")
если_не_целиком = целиком is None or целиком.group(0) != "#margy_gradient 8DD1B0-B7A8E0"
плохих += если_не_целиком
print("\nметка прячется целиком: %s%s"
      % (целиком.group(0) if целиком else "—", "   ПЛОХО" if если_не_целиком else ""))

print("плохих: %d" % плохих)
sys.exit(1 if плохих else 0)
